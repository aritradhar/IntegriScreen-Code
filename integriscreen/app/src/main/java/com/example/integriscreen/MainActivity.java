package com.example.integriscreen;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static com.example.integriscreen.ISImageProcessor.applyCanny;
import static com.example.integriscreen.ISImageProcessor.rotate270;
import static com.example.integriscreen.ISImageProcessor.rotate90;
import static com.example.integriscreen.ISImageProcessor.storePic;
import static com.example.integriscreen.ISStringProcessor.almostIdenticalString;
import static com.example.integriscreen.ISStringProcessor.concatTextBlocks;
import static com.example.integriscreen.ISStringProcessor.isChangeLegit;
import static java.lang.System.currentTimeMillis;
import static org.opencv.imgproc.Imgproc.THRESH_BINARY;
import static org.opencv.imgproc.Imgproc.line;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2, OnDataLoadedEventListener, EvaluationListener {

    private static final String TAG = "MainActivity";

    private enum OutputSelection { RAW, CANNY, DIFF, DETECT_TRANSFORMATION, DETECT_TEXT, INTEGRISCREEN};
    private static OutputSelection currentOutputSelection;
    private static SeekBar huePicker;
    private static TextView colorLabel;
    private static TextView textOutput;
    private static SeekBar detectPicker;
    private static CheckBox realignCheckbox;
    private static CheckBox limitAreaCheckbox;
    private static CheckBox liveCheckbox;

    private Mat previousFrameMat;

    private Point upper_left, lower_right;

    private int color_border_hue = 130;

    // the form created based on specs received from Server
    private TargetForm targetForm;
    private ArrayList<ActiveElementLogs> activeElementLogs;

    // static address of the server to fetch list of forms
    private static String serverURL = "http://tildem.inf.ethz.ch/IntegriScreenServer/MainServer";
    private static String serverPageTypeURLParam = "?page_type=mobile_form";

    //    private CameraBridgeViewBase _cameraBridgeViewBase;
    private CustomCameraView _cameraBridgeViewBase;

    // eu: square view to show the focus point
    private DrawingView drawingView;

    // TextRecognizer is the native vision API for text extraction
    private static TextRecognizer textRecognizer;

    private ISServerCommunicationManager formsListManager;

    public enum ISState {  DETECTING_FRAME,       // Start detecting the green frame
                            DETECTING_TITLE,       // Start OCR-ing to find the title
                            LOADING_FORM,          // Load the form based on title
                            REALIGNING_AFTER_FORM_LOAD,    // Realign once more, this time speficially to the form ratio
                            VERIFYING_UI,          // Start verifying that the UI is as expected
                            SUPERVISING_USER_INPUT,  // Accept user's input for as long as everything is OK
                            SUBMITTING_DATA,       // Keep sending user data until server responds
                            EVERYTHING_OK,         // Tell the user that everything is OK
                            DATA_MISMATCH,        // There was a mismatch on the server. Show the diff to the user.
                            ERROR_DURING_INPUT };  // We might end up here in case we detect something strange during user input
    public ISState currentISState;
    private boolean activityDetected;
    private JSONObject receivedJSONObject;
    private Timer submitDataTimer;
    private TimerTask submitDataTimerTask;

    private PerspectiveRealigner cameraFrameRealigner;
    private PerspectiveRealigner ISUpperFrameContinuousRealigner;
    private ISImageProcessor upperFrameISImageProcessor;
    private ISImageProcessor lowerFrameISImageProcessor;
    private ISImageProcessor wholeFrameISImageProcessor;
    private EvaluationController myEvaluationController;

    private BaseLoaderCallback _baseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    // Load ndk built module, as specified in moduleName in build.gradle
                    // after opencv initialization
                    System.loadLibrary("native-lib");
                    _cameraBridgeViewBase.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
            }
        }
    };

    private void hideActionBar() {
        // HIDE the Status Bar and Action Bar
        View decorView = getWindow().getDecorView();
        // Hide the status bar.
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
        // Remember that you should never show the action bar if the
        // status bar is hidden, so hide that too if necessary.

        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        actionBar.hide();
        // -----------------------
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        hideActionBar();

        // Initialize textRecognizer
        textRecognizer = new TextRecognizer.Builder(this).build();

        //check textRecognizer is operational
        if (!textRecognizer.isOperational()) {
            Log.w(TAG, "Detector dependencies are not yet available.");

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(this, "Low Storage!!!", Toast.LENGTH_LONG).show();
                Log.w(TAG, "Low Storage!!!");
            }
        }

        formsListManager = new ISServerCommunicationManager(serverURL, getApplicationContext());

        currentOutputSelection = OutputSelection.RAW;
//        currentOutputSelection = OutputSelection.CANNY;

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        // Permissions for Android 6+
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                1);

//        _cameraBridgeViewBase = (CameraBridgeViewBase) findViewById(R.id.main_surface);
        _cameraBridgeViewBase = (CustomCameraView) findViewById(R.id.main_surface);

        // Uncomment to set one of the upper bounds on the camera resolution (the other is the preview View size)
        // To hardcode the resolution, find "// calcWidth = 1920;" in CameraBridgeViewBase
        // Ivo's phone: 960x540, 1280x720 (1M), 1440x1080 (1.5M), 1920x1080 (2M)
        // _cameraBridgeViewBase.setMaxFrameSize(1280, 720);
        _cameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        _cameraBridgeViewBase.setCvCameraViewListener(this);
        _cameraBridgeViewBase.enableFpsMeter();

        // Deal with the UI element bindings
        colorLabel = (TextView)findViewById(R.id.colorLabel);
        textOutput = (TextView)findViewById(R.id.textOutput);
        detectPicker = (SeekBar)findViewById(R.id.detect_method);
        realignCheckbox = (CheckBox)findViewById(R.id.realignCheckBox);
        limitAreaCheckbox = (CheckBox)findViewById(R.id.limitAreaCheckBox);
        liveCheckbox = (CheckBox)findViewById(R.id.liveCheckbox);

        // initialize logs
        activeElementLogs = new ArrayList<>();

        cameraFrameRealigner = new PerspectiveRealigner();
        ISUpperFrameContinuousRealigner = new PerspectiveRealigner();
        upperFrameISImageProcessor = new ISImageProcessor();
        lowerFrameISImageProcessor = new ISImageProcessor();
        wholeFrameISImageProcessor = new ISImageProcessor();

        myEvaluationController = new EvaluationController(this);

        huePicker = (SeekBar)findViewById(R.id.colorSeekBar);
        huePicker.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float []hsv = new float[3];
                hsv[0] = (float)progress;
                hsv[1] = hsv[2] = 100f;

                colorLabel.setBackgroundColor(Color.HSVToColor(hsv));
                colorLabel.setText(Integer.toString(progress));
            }
        });

        //cameraPreview.changeExposureComp(-currentAlphaAngle);
        drawingView = (DrawingView) findViewById(R.id.drawing_surface);
        _cameraBridgeViewBase.setDrawingView(drawingView);
    }

    @Override
    public void onPause() {
        super.onPause();
        disableCamera();
    }

    @Override
    public void onResume() {
        super.onResume();

        // For some reason the action bar tended to resurface so we hide it
        // Otherwise, openCV would start using a different camera resolution
        hideActionBar();

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, _baseLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            _baseLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    Log.d("TAG", "Permission granted");
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(MainActivity.this, "Permission denied to read your External storage", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    public void outputOnToast(final String outString) {
        Log.d("Toast Output", outString);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), outString, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void outputOnUILabel(final String textToShow) {
//        final String textToShow = outputText;
        Log.d("UILabel Output", textToShow);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textOutput.setText(textToShow);
            }
        });

    }

    public void onClickShowCanny(View view) {
        currentOutputSelection = OutputSelection.CANNY;
    }

    public void onClickSubmitData(View view) {
        if (currentISState == null)
            return;

        Log.d("clickSubmit", currentISState.name());

        if (targetForm.isLoaded)
            transitionISSTo(ISState.SUBMITTING_DATA);
        else
            outputOnToast("The form is not loaded yet!");
    }

    public void onClickStartEval(View view) {
        myEvaluationController.startEvaluation();
    }

    public void onClickShowDiff(View view) {
        currentOutputSelection = OutputSelection.DIFF;
    }
    public void onClickShowColor(View view) {
        currentOutputSelection = OutputSelection.DETECT_TRANSFORMATION;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            huePicker.setProgress(color_border_hue, true);
        }
    }

    public void onClickShowRaw(View view) {
        currentOutputSelection = OutputSelection.RAW;
    }

    public void onClickDetectText(View view) {
        if (liveCheckbox.isChecked())
            currentOutputSelection = OutputSelection.DETECT_TEXT;
        else {
            if (limitAreaCheckbox.isChecked())
                extractAndDisplayTextFromFrame(previousFrameMat.submat(new Rect(upper_left, lower_right)));
            else
                extractAndDisplayTextFromFrame(previousFrameMat);
        }
    }

    public void startIntegriScreen(int evalIndex) {
        currentOutputSelection = OutputSelection.INTEGRISCREEN;
        // currentEvalIndex = evalIndex;

        transitionISSTo(ISState.DETECTING_FRAME);
        outputOnUILabel("Make the green frame visible in the top part, then click Realign.");
    }

    public void onClickStartIS(View view) {
        startIntegriScreen(-1);
    }

    // Button callback to handle taking a picture
    public void onClickTakePic(View view) {
        Log.d(TAG, "Take picture button clicled.");
        takePicHighRes();
    }

    // Callback which is called by TargetForm class once the data is ready.
    public void onFormLoaded() {
        Log.d(TAG, "Form loaded!" + targetForm.toString());
        //Toast.makeText(getApplicationContext(), "Loaded form: " + targetForm.formUrl, Toast.LENGTH_SHORT).show();
    }

    public void onResponseReceived(JSONObject responseJSON) {
        if (currentOutputSelection != OutputSelection.INTEGRISCREEN)
            cancelTimers();

        receivedJSONObject = responseJSON;
        // outputOnUILabel(responseJSON.toString());
        try {
            String responseVal = receivedJSONObject.getString("response");
            if (responseVal.equals("match")) {
                transitionISSTo(ISState.EVERYTHING_OK);
            }
            else if (responseVal.equals("nomatch")) {
                transitionISSTo(ISState.DATA_MISMATCH);
            }

            // outputOnToast(receivedJSONObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
            Log.d(TAG + "error:", "responseJSON" + responseJSON.toString() + "|" + e.getMessage());
        }
    }

    // Callback when picture is taken
    public void onPicTaken(byte[] data) {
        Log.d(TAG, "onPicTaken: " + currentTimeMillis());

        //convert to mat
        Mat matPic = Imgcodecs.imdecode(new MatOfByte(data), Imgcodecs.IMREAD_COLOR);
        Log.d(TAG, "afterImdecode: " + currentTimeMillis());

        if (currentISState == ISState.VERIFYING_UI) {
            Mat rotatedFullRes = new Mat(1, 1, 1);
            rotate90(matPic, rotatedFullRes);
            Rect frameLimit = targetForm.getFrameSize(matPic.height(), matPic.width());

//            storePic(rotatedFullRes, "_rotated");

            Mat croppedRotatedFullRes = new Mat(1, 1, 1);
            rotatedFullRes.submat(frameLimit).copyTo(croppedRotatedFullRes);

            PerspectiveRealigner fullResolutionPerspective = new PerspectiveRealigner();
            fullResolutionPerspective.detectFrameAndComputeTransformation(croppedRotatedFullRes, color_border_hue);
            fullResolutionPerspective.realignImage(croppedRotatedFullRes);

            List<Pair<String, String>> OCRMismatches = plotFormAndFindOCRMismatches(croppedRotatedFullRes, targetForm);
            if (OCRMismatches.size() == 0) {
                transitionISSTo(ISState.SUPERVISING_USER_INPUT);
            }
            else {
                myEvaluationController.storeOCRMismatches(OCRMismatches);
                outputOnToast("Validation based on high-res photo failed");
                storePic(croppedRotatedFullRes, "_validation");
            }

            rotatedFullRes.release();
            croppedRotatedFullRes.release();
            return;
        }

        // ------------
        // We keep this for now mainly for testing
        // store plain pic
         storePic(data, "_byte");

        Imgproc.cvtColor(matPic, matPic, Imgproc.COLOR_BGR2RGB);
        Log.d(TAG, "afterCvtColor: " + currentTimeMillis());

        //        matPic = matPic.submat(new Rect(0, 0, matPic.width() / 2, matPic.height()));

        // detect the green framebox
        PerspectiveRealigner myPerspective = new PerspectiveRealigner();
        myPerspective.detectFrameAndComputeTransformation(matPic, color_border_hue, matPic.width(), matPic.height());
        myPerspective.realignImage(matPic);

        // rotate the mat so we get the proper orientation
        rotate90(matPic, matPic);

        Log.d("TextDetection", "Detected: " + extractAndDisplayTextFromFrame(matPic));

        // store mat image in drive
        storePic(matPic, "_mat");
    }

    private static boolean validateAndPlotForm(Mat currentFrameMat, TargetForm form) {
        // If no mismatches found, all is OK
        return (plotFormAndFindOCRMismatches(currentFrameMat, form).size() == 0);
    }


    private static List<Pair<String, String>> plotFormAndFindOCRMismatches(Mat currentFrameMat, TargetForm form) {
        double scaleX = (double)currentFrameMat.width() / form.form_w_abs;
        double scaleY = (double)currentFrameMat.height() / form.form_h_abs;

        List<Pair<String, String>> OCRMismatches = new ArrayList<>();
        for(int i = 0; i < form.allElements.size(); ++i) {
            UIElement element = form.allElements.get(i);
            Rect rescaledBox = element.getRescaledBox(scaleX, scaleY);

            Log.d("box: ", rescaledBox.toString() + "|" + currentFrameMat.size());
            String detected = extractAndDisplayTextFromFrame(currentFrameMat.submat(rescaledBox));

            Scalar rectangle_color;
            if (almostIdenticalString(detected, element.defaultVal, false)) {
                rectangle_color = new Scalar(255, 255, 0);
            } else {
                rectangle_color = new Scalar( 255, 0, 0);
                // Output the text on the UI elements
                int textHeight = (int) Imgproc.getTextSize(element.defaultVal, Core.FONT_HERSHEY_SIMPLEX, 1.3, 1, new int[1]).height;
                Imgproc.putText(currentFrameMat, element.defaultVal, new Point(rescaledBox.x, rescaledBox.y + textHeight + 20),
                        Core.FONT_HERSHEY_SIMPLEX, 1.3, new Scalar(255, 0, 0));

                OCRMismatches.add(new Pair<> (element.defaultVal, detected));
            }

            // Plot the borders of the UI elements
            Imgproc.rectangle(currentFrameMat, rescaledBox.tl(), rescaledBox.br(), rectangle_color, 4);
        }

        return OCRMismatches;
    }


    // This method:
    // 1) extracts all the text from a (specific part of) frame
    // 2) concatenates it
    // 3) draws it on the screen
    // 4) displays it on an UI label
    // 5) returns the concatenated text
    private static String extractAndDisplayTextFromFrame(Mat frameMat) {

        SparseArray<TextBlock> texts = detect_text(frameMat);

        String concatDelim = "";
        String concatenatedText = "";
        Log.d("TextDetected", texts.size()+" words");
        for (int i = 0; i < texts.size(); ++i) {
            TextBlock item = texts.valueAt(i);
            android.graphics.Rect rect = new android.graphics.Rect(item.getBoundingBox());

            if (item != null && item.getValue() != null) {
                int textHeight = (int) Imgproc.getTextSize(item.getValue(), Core.FONT_HERSHEY_SIMPLEX, 1, 2, new int[1]).height;
                Imgproc.putText(frameMat, item.getValue(), new Point(rect.left, rect.top + textHeight + 10),
                        Core.FONT_HERSHEY_SIMPLEX, 1, new Scalar(0,255,0), 2);

                Log.d("TextDetected", item.getValue());
                concatenatedText += item.getValue() + concatDelim;
            }
        }

        // Also, output on the UI label
//         if (currentOutputSelection == OutputSelection.RAW && !liveCheckbox.isChecked())
        // outputOnUILabel(concatenatedText);

        return concatenatedText;
    }

    /**
     * The method to take a high resolution picture programatically.
     */
    private void takePicHighRes() {
        _cameraBridgeViewBase.setPictureSize(0);    // the best quality is set by default for pictures

        Log.d(TAG, "before the takePicture: " + currentTimeMillis());
        _cameraBridgeViewBase.takePicture(this);
    }
    
    public void onDestroy() {
        super.onDestroy();
        disableCamera();
    }

    public void disableCamera() {
        if (_cameraBridgeViewBase != null)
            _cameraBridgeViewBase.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        previousFrameMat = new Mat(1, 1, CvType.CV_8UC4);
    }

    public void onCameraViewStopped() {
        previousFrameMat.release();
    }

    // this one will probably be of size 1080 / 960 -> since the form needs to know how much height it is allowed to take
    public boolean loadFormBasedOnName(Mat rotatedUpperFrameMat, int maxScreenHeight) {
        Rect formTitleBox = new Rect(0, 0, rotatedUpperFrameMat.width() / 2, rotatedUpperFrameMat.height() / 8); // about 15%

        Imgproc.rectangle(rotatedUpperFrameMat, formTitleBox.tl(), formTitleBox.br(), new Scalar(255, 0, 0), 4);

        // At the moment we are strongly and implicitly!!! hardcoding the values "string -> URL"
        String formToLoad = concatTextBlocks(detect_text(rotatedUpperFrameMat.submat(formTitleBox)));

        formToLoad = formToLoad.replaceAll("\\s+","");

        String urlToLoad = formsListManager.getFormURLFromName(formToLoad);
        if (urlToLoad != null) {
            Log.d("box: curr: ", rotatedUpperFrameMat.size().toString());
            targetForm = new TargetForm(getApplicationContext(), urlToLoad, rotatedUpperFrameMat.width(), maxScreenHeight, this);
            // outputOnToast("Loading form: " + formToLoad);
        } else
            return false;

        return true;
    }

    boolean shouldDetectTransformation(ISState currentISState) {
        if (liveCheckbox.isChecked())
            return true;

        List<ISState> dontDetectTransformation = Arrays.asList(
                ISState.VERIFYING_UI,
                ISState.SUPERVISING_USER_INPUT,
                ISState.SUBMITTING_DATA,
                ISState.EVERYTHING_OK,
                ISState.DATA_MISMATCH);

        if (dontDetectTransformation.contains(currentISState))
            return false;

        return true;
    }

    private void cancelTimers() {
        // if (submitDataTimer != null) outputOnToast("Cancelling the existing timer.");

        if (submitDataTimerTask != null) submitDataTimerTask.cancel();
        if (submitDataTimer != null) submitDataTimer.cancel();
    }

    private void executeISStateEntryActions(ISState newState)
    {
        if (newState == ISState.DETECTING_FRAME) {
            cancelTimers();

            realignCheckbox.setChecked(true);
        }
        else if (newState == ISState.VERIFYING_UI) {
            // TODO eu: Focus is set arbitrarly to the midle point of the upper-half of the screen
            int x = (int) _cameraBridgeViewBase.getWidth() / 2;
            int y = (int) _cameraBridgeViewBase.getHeight() / 4;
            int squareSize = 200;

            _cameraBridgeViewBase.drawingViewSet = false;
            _cameraBridgeViewBase.focusAt(x, y, squareSize);
            Log.d(TAG, "Focus is set automatically to the midle point of the upper-half of the screen");

            // Once it is ready, we use this to verify as well
            takePicHighRes();
        }
        else if (newState == ISState.SUBMITTING_DATA) {
            cancelTimers();
            outputOnToast("Polling the server every 5 seconds...");

            submitDataTimer = new Timer();
            submitDataTimerTask= new TimerTask() {
                @Override
                public void run() {
                    outputOnToast("Sending: " + (new Date()).toString());
                    targetForm.submitFormData(serverURL + serverPageTypeURLParam);
                }
            };

            submitDataTimer.schedule(submitDataTimerTask, 0, 5000);
        }
        else if (newState == ISState.EVERYTHING_OK) {
            // realignCheckbox.setChecked(false);
            cancelTimers();
        }
        else if (newState == ISState.DATA_MISMATCH) {
            // realignCheckbox.setChecked(false);
            cancelTimers();
        }

    }

    private void transitionISSTo(ISState newState)
    {
        executeISStateEntryActions(newState);
        currentISState = newState;
        // outputOnToast("Entering State: " + newState.name());
        outputOnUILabel("Current State: " + newState.name());
    }


    private void detectHandsAndUpdateActivity(Mat currentFrameMat, long mid_delim)
    {
        // ===== 2) Handle the lower part
        // -- apply detect_color(human_skin)
        // -- apply diff on color of human_skin
        // -- detect if any changes are happening in the lower part
        Rect handsBox = new Rect(new Point(mid_delim, 0), new Point(currentFrameMat.width(), currentFrameMat.height()));
        Mat handsPart = currentFrameMat.submat(handsBox);

        int skin_hue_estimate = 26;
        PerspectiveRealigner.detectColor(handsPart, handsPart, skin_hue_estimate);

        List<Pair<Rect, Integer>> changedLocations = lowerFrameISImageProcessor.diffFramesAndGetAllChangeLocations(handsPart,
                1, 1, 500);

        if (changedLocations.size() > 0)
            activityDetected = true;
        else
            activityDetected = false;

        if (handsPart.channels() == 1)
            Imgproc.cvtColor(handsPart, handsPart, Imgproc.COLOR_GRAY2RGBA);

        handsPart.copyTo(currentFrameMat.submat(handsBox));
        handsPart.release();
    }

    // int cnt = 0; int freq = 50;
    void processRotatedUpperPart(Mat rotatedUpperPart)
    {
        // HANDLE THE UPPER PART!
        // ---------------- Based on the state that we are in, handle the upper part ------

        if (currentISState == ISState.VERIFYING_UI) {
            // storePic(rotatedUpperPart, "_UI_Verification");
            if (validateAndPlotForm(rotatedUpperPart, targetForm) || limitAreaCheckbox.isChecked()) {
                transitionISSTo(ISState.SUPERVISING_USER_INPUT);
            }

        } else if (currentISState == ISState.SUPERVISING_USER_INPUT) {
            Mat diffsUpperPart = rotatedUpperPart.clone();
            // We don't apply canny any more since it causes a lot of flickering
            // applyCanny(rotatedUpperPart, diffsUpperPart);
            // rotatedUpperPart.copyTo(diffsUpperPart);

            List<Pair<Rect, Integer>> changedLocations = upperFrameISImageProcessor.diffFramesAndGetAllChangeLocations(diffsUpperPart,
                    1, 1, 10);

            validateUIChanges(rotatedUpperPart, changedLocations);



            String allDiffAreas = "";
            for(Pair<Rect, Integer> P : changedLocations)
                allDiffAreas += " | " + P.second.toString();

            outputOnUILabel("No. of diffs: " + changedLocations.size() + ", areas: " + allDiffAreas);

            if (changedLocations.size() > 0 && !activityDetected) {
                Log.d("attack", "Warning: UI changes, but no hand movement!");
            }

//            if (cnt % freq == 0) storePic(diffsUpperPart, "_after_diff");

            Imgproc.cvtColor(diffsUpperPart, rotatedUpperPart, Imgproc.COLOR_GRAY2RGBA);

            diffsUpperPart.release();

//            ++cnt;
        } else if (currentISState == ISState.SUBMITTING_DATA) {
            Log.d("SubmittingData", "bla");
        } else if (currentISState == ISState.EVERYTHING_OK) {
            //          outputOnUILabel(receivedJSONObject.toString());
        } else if (currentISState == ISState.DATA_MISMATCH) {
            // outputOnUILabel(receivedJSONObject.toString());

            try {
                JSONArray mismatchElements = receivedJSONObject.getJSONArray("diffs");

                // iterate through all elements
                // for(JSONObject currDiff : mismatchElements) {
                for(int i = 0; i < mismatchElements.length(); ++i) {
                    JSONObject currDiff = mismatchElements.getJSONObject(i);

                    String elementID = currDiff.getString("elementid");
                    String phoneVal = currDiff.getString("phone");
                    String browserVal = currDiff.getString("browser");

                    Log.d("diffs", elementID + "|" + browserVal);

                    UIElement currentElement = targetForm.getElementById(elementID);

                    int textHeight = (int)Imgproc.getTextSize(browserVal, Core.FONT_HERSHEY_SIMPLEX, 1.3, 2, new int[1]).height;
                    Imgproc.putText(rotatedUpperPart, browserVal, new Point(currentElement.box.tl().x, currentElement.box.tl().y+textHeight+10),
                            Core.FONT_HERSHEY_SIMPLEX, 1, new Scalar(0, 255, 255), 2);

                    textHeight = textHeight + (int)Imgproc.getTextSize(phoneVal, Core.FONT_HERSHEY_SIMPLEX, 1.3, 2, new int[1]).height;
                    Imgproc.putText(rotatedUpperPart, phoneVal, new Point(currentElement.box.tl().x, currentElement.box.tl().y + textHeight + 20),
                            Core.FONT_HERSHEY_SIMPLEX, 1, new Scalar(0, 255, 0), 2);

                    Imgproc.rectangle(rotatedUpperPart, currentElement.box.tl(), currentElement.box.br(), new Scalar(255, 0, 0), 4);
                }

            } catch (JSONException e) {
                e.printStackTrace();
                Log.d("ListOfForms", e.getMessage());
            }

        } else {
            extractAndDisplayTextFromFrame(rotatedUpperPart);
        }
    }

    void handleUpperPart(Mat currentFrameMat, long mid_delim) {
        // ==== 1) Handle the upper part of the screen:
        // -- Rotate by 90
        // -- Detect the transformation
        // -- Realign
        // -- PROCESSS
        // -- Rotate back

        Rect screenBox = new Rect(new Point(0, 0), new Point(mid_delim, currentFrameMat.height()));
        Mat screenPart = currentFrameMat.submat(screenBox);

        Mat rotatedScreenPart = new Mat(1, 1, CvType.CV_8UC1);
        rotate90(screenPart, rotatedScreenPart);

        if (shouldDetectTransformation(currentISState)) { // during "verifying UI", we need to have a still screen
            ISUpperFrameContinuousRealigner.detectFrameAndComputeTransformation(rotatedScreenPart, color_border_hue);

            if (currentISState == ISState.REALIGNING_AFTER_FORM_LOAD) {
                transitionISSTo(ISState.VERIFYING_UI);
            }
        }

        if (realignCheckbox.isChecked()) {
            ISUpperFrameContinuousRealigner.realignImage(rotatedScreenPart);
        }

        if (currentISState == ISState.DETECTING_FRAME && formsListManager.isReady()) {   // make sure that forms are already downloaded
            if (loadFormBasedOnName(rotatedScreenPart, currentFrameMat.width())) { // It is important that this function only returns true if such a form exists!
                transitionISSTo(ISState.LOADING_FORM);
            }
        }

        // Depending on the app state, run detection of text, find changes, etc.
        processRotatedUpperPart(rotatedScreenPart);

        rotate270(rotatedScreenPart, screenPart);
        rotatedScreenPart.release();

        if (screenPart.channels() == 1)
            Imgproc.cvtColor(screenPart, screenPart, Imgproc.COLOR_GRAY2RGBA);

        // Combine the two parts
        screenPart.copyTo(currentFrameMat.submat(screenBox));
        screenPart.release();
    }

    // This function runs a state machine, which will in each frame transition to the next state if specific conditions are met.
    public Mat executeISStateMachine(Mat currentFrameMat) {
        long mid_delim = currentFrameMat.width() / 2; // By default, take a half of the screen size

        if (targetForm != null && targetForm.isLoaded) { // If form is loaded, start realinging to its shape, recompute the params
            mid_delim = Math.round((double) currentFrameMat.height() * targetForm.form_ratio_h / targetForm.form_ratio_w);
            if (currentISState == ISState.LOADING_FORM)
                transitionISSTo(ISState.REALIGNING_AFTER_FORM_LOAD);
        }


        // ======== Handle and update the upport part of the screen: rotate, detect frame, realign, process, rotate back, update
        handleUpperPart(currentFrameMat, mid_delim);


        // ======== Handle the lower part of the screen
        // This function detects the hands, updates the currentFrameMat and sets activityDetected variable
        detectHandsAndUpdateActivity(currentFrameMat, mid_delim);
        // =============================================

        if (currentISState == ISState.EVERYTHING_OK || currentISState == ISState.DATA_MISMATCH)
            rotate90(currentFrameMat, currentFrameMat);

        int textX = 300;
        int textY = 1100;
        if (currentISState == ISState.EVERYTHING_OK) {
            Imgproc.putText(currentFrameMat, "EVERYTHING OK!", new Point(textX, textY),
                    Core.FONT_HERSHEY_SIMPLEX, 3, new Scalar(0, 255, 0),3);
        } else if (currentISState == ISState.DATA_MISMATCH) {
            Imgproc.putText(currentFrameMat, "MISMATCH!", new Point(textX, textY),
                    Core.FONT_HERSHEY_SIMPLEX, 4, new Scalar(255, 0, 0), 5);

            Imgproc.putText(currentFrameMat, "BROWSER", new Point(textX, textY + 200),
                    Core.FONT_HERSHEY_SIMPLEX, 3, new Scalar(0, 255, 255), 5);

            Imgproc.putText(currentFrameMat, "PHONE", new Point(textX, textY + 300),
                    Core.FONT_HERSHEY_SIMPLEX, 3, new Scalar(0, 255, 0), 5);
        } else {
            // Draw the separating line, choose color depending on activity
            Scalar lineColor = (activityDetected) ? new Scalar(0, 255, 0) : new Scalar(255, 0, 0);
            line(currentFrameMat, new Point(mid_delim, currentFrameMat.height()), new Point(mid_delim, 0), lineColor, 3);
        }

        if (currentISState == ISState.EVERYTHING_OK || currentISState == ISState.DATA_MISMATCH)
            rotate270(currentFrameMat, currentFrameMat);

        return currentFrameMat;
    }






    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat currentFrameMat = inputFrame.rgba();
//        Log.d(TAG, "Frame size: " + currentFrameMat.rows() + "x" + currentFrameMat.cols());

        if (currentOutputSelection == OutputSelection.INTEGRISCREEN) {
            return executeISStateMachine(currentFrameMat);
        }

        if (currentOutputSelection == OutputSelection.DETECT_TRANSFORMATION) {
            int hueCenter = huePicker.getProgress(); // get progress value from the progress bar, divide by 2 since this is what OpenCV expects
            int detection_option = detectPicker.getProgress();
            if (detection_option == 0) // just show the color detection
                cameraFrameRealigner.detectColor(currentFrameMat, currentFrameMat, hueCenter);
            else
                cameraFrameRealigner.detectFrameAndComputeTransformation(currentFrameMat, hueCenter,
                        currentFrameMat.width(), currentFrameMat.height(), true);

            return currentFrameMat;
        }

        if (realignCheckbox.isChecked()) {
            if (liveCheckbox.isChecked() && currentOutputSelection != OutputSelection.DIFF && currentOutputSelection != OutputSelection.CANNY) { // Only continuiously realign if live is turned on?
                cameraFrameRealigner.detectFrameAndComputeTransformation(currentFrameMat, color_border_hue, currentFrameMat.width(), currentFrameMat.height());
            }

            cameraFrameRealigner.realignImage(currentFrameMat, currentFrameMat);
        }

        int h_border_perc = 30;
        int v_border_perc = 47;
        if (currentOutputSelection == OutputSelection.RAW) {
            currentFrameMat.copyTo(previousFrameMat);
            if (limitAreaCheckbox.isChecked()) {
                // Setup border parameters
                int h_edge = (int) Math.round(currentFrameMat.width() * h_border_perc / 100.0); // horizontal edge
                int v_edge = (int) Math.round(currentFrameMat.height() * v_border_perc / 100.0); // horizontal edge

                upper_left = new Point(h_edge, v_edge);
                lower_right = new Point(currentFrameMat.width() - h_edge, currentFrameMat.height() - v_edge);

                Imgproc.rectangle(currentFrameMat, upper_left, lower_right, new Scalar(255, 0, 0), 4);
            }

            return currentFrameMat;
        }

        if (currentOutputSelection == OutputSelection.CANNY) {
            applyCanny(currentFrameMat, currentFrameMat);

            // Some parameters to play with Canny
            if (liveCheckbox.isChecked()) {
                Imgproc.threshold(currentFrameMat, currentFrameMat, 40, 255, THRESH_BINARY);
            }
            if (limitAreaCheckbox.isChecked()) {
                UnusedImageProcessing.applyTimeAverage(currentFrameMat, 3);
            }

            return currentFrameMat;
        }

        if (currentOutputSelection == OutputSelection.DIFF) {
            wholeFrameISImageProcessor.diffWithPreviousFrame(currentFrameMat, currentFrameMat, 1, 1);
            return currentFrameMat;
        }


        if (currentOutputSelection == OutputSelection.DETECT_TEXT) {
            Rect limitRect = null;
            if (limitAreaCheckbox.isChecked()) {
                // Setup border parameters
                int h_edge = (int) Math.round(currentFrameMat.width() * h_border_perc / 100.0); // horizontal edge
                int v_edge = (int) Math.round(currentFrameMat.height() * v_border_perc / 100.0); // horizontal edge

                upper_left = new Point(h_edge, v_edge);
                lower_right = new Point(currentFrameMat.width() - h_edge, currentFrameMat.height() - v_edge);

                limitRect = new Rect(upper_left, lower_right);
                Imgproc.rectangle(currentFrameMat, upper_left, lower_right, new Scalar(255, 0, 0), 4);

                extractAndDisplayTextFromFrame(currentFrameMat.submat(limitRect));
            }
            else
                extractAndDisplayTextFromFrame(currentFrameMat);

            return currentFrameMat;
        }

        return currentFrameMat;
    }

    /**
     * This method takes as input the list of changes on the screen and confirms the integrity
     * of screen changes
     */
    long previousFrameTimestamp = 0;
    private void validateUIChanges(Mat rawScreenFrame, List<Pair<Rect, Integer>> changedLocations) {
        // TODO(enis): make sure that only editable elements are allowed to change!
        List<String> processedElements = new ArrayList<String>();   // skip future changes on the same element
        Log.d("ElementChanges", "Total changes: " + changedLocations.size());
        for (int i = 0; i < changedLocations.size(); i++) {
            UIElement currElement = targetForm.matchElFromDiff(changedLocations.get(i).first);
            int currElementArea = changedLocations.get(i).second;

            // skip if the change did not happen on an input element or the change is already processed for the same frame
            if ((currElement == null) || (processedElements.contains(currElement.id)))
                continue;

            Log.d("ElementChanges", "Element being edited: " + currElement.id);

            // read the element value from the current frame
            String newValue = concatTextBlocks(detect_text(rawScreenFrame.submat(currElement.box)));

            long currentTimestamp = currentTimeMillis();
            double diffTimestamp = currentTimestamp - previousFrameTimestamp;

            // check if diff comes from an element value change
            if (!currElement.currentVal.equals(newValue)) {
                if (!isChangeLegit(currElement.currentVal, newValue, diffTimestamp)) {
                    String message = "Change from |" + currElement.currentVal + "| to |" + newValue + "| is not OK in " + diffTimestamp + "ms";
  //                  outputOnToast(message);
                    outputOnUILabel(message);
                    Log.w("non legit change: ", message);
                }

                targetForm.updateElementWithValue(currElement.id, newValue);    // value updated Todo eu: what if the frame is unclear and the change is unreal

                if (targetForm.activEl.equals(currElement.id)) {    // this element is already the active one
                    targetForm.activeElementLastEdit = System.currentTimeMillis();  //Todo eu: frame timestamp would be more accurate!
                }
                else {
                    String oldElement = targetForm.activEl;
                    double tsOfNow = System.currentTimeMillis();
                    targetForm.activeSince = tsOfNow;
                    targetForm.activeElementLastEdit = tsOfNow;
                    targetForm.activEl = currElement.id;

                    // store the change in the logs
                    ActiveElementLogs tmp = new ActiveElementLogs(oldElement, currElement.id, tsOfNow);
                    activeElementLogs.add(tmp);
                }
            }

            processedElements.add(currElement.id);  // add the current elemnt in the processed list
//            auditActiveElementsLogs();
        }
        previousFrameTimestamp = currentTimeMillis();
    }



    private void auditActiveElementsLogs(int lastEvents) {
        // consider #lastEvents in the audit

    }

    /**
     * Returns the value of an UI element that includes a given point
     */
    public String readElementValueAtDiff(Mat currentFrame, Point point) {
        String output = "";
//        int index = targetForm.matchElFromDiff(point);
//        UIElement tmp = targetForm.getElement(index);
//        output = concatTextBlocks(detect_text(currentFrame.submat(tmp.box)));
        return output;
    }

    /**
     * Returns the value of an UI element that includes a given rectangle
     */
    public String readElementValueAtDiff(Mat currentFrame, Rect box) {
        String output = "";
//        int index = targetForm.matchElFromDiff(box);
//        UIElement tmp = targetForm.getElement(index);
//        output = concatTextBlocks(detect_text(currentFrame.submat(tmp.box)));
        return output;
    }

    public void updateUIElement(int i, String text) {
        // check if the element is active, TODO: does time since active help?
        if (targetForm.getElement(i).id.equals(targetForm.activEl)) {
            targetForm.getElement(i).currentVal = text;
            targetForm.getElement(i).lastUpdated = currentTimeMillis();
        }
        else {
            // raise an alarm flag
            Log.d(TAG, "***Attack*** Take things seriously :-) " +
                    "Trying to modify element with ID: " + targetForm.getElement(i).id
                    + ", while active is: " + targetForm.activEl);
        }
    }

    public static void addToCommunicationQueue(JsonObjectRequest jsonObjectRequest) {
        ISServerCommunicationManager.queue.add(jsonObjectRequest);
    }


    /**
     * This method returns a SparseArry of TextBlocs found in a frame, or a subframe if box is not null
     */
    private static SparseArray<TextBlock> detect_text(Mat matFrame) {
        //convert Mat to Bitmap
        Bitmap bmp = Bitmap.createBitmap(matFrame.cols(), matFrame.rows(),
                Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(matFrame, bmp);
        //convert Bitmap to Frame. TODO: Optimize conversions if we need more FPS
        Frame frame = new Frame.Builder().setBitmap(bmp).build();

        SparseArray<TextBlock> texts = textRecognizer.detect(frame);
        return texts;
    }

    public boolean shouldStopEvaluation() {
        if (targetForm == null || !targetForm.isLoaded)
            return false;

        if (targetForm.pageId.equals("__STOP__"))
            return true;

        return false;
    }

    public boolean previousFormSuccessfullyVerified() {
        return (currentISState == ISState.SUPERVISING_USER_INPUT);
    }

    public void reportEvaluationResult(int cntSuccess, int cntTotal) {
        String message = "Success rate: " + cntSuccess + "/ " + cntTotal + " = " + (double)cntSuccess / cntTotal;
        Log.d("Evaluation Finished:", message);
        outputOnToast(message);
    }

//    public void startEvaluation(int startIndex, int endIndex) {
//        // TODO: not implemented yet
//    }


    // get motion events
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();

            Log.d("FocusMode", "Click to: " + x + ", " + y
                    + ", at: " + event.getEventTime());

            _cameraBridgeViewBase.focusAt(x, y, 100);
        }
        return false;
    }

}

