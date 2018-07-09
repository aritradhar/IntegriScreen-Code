package com.example.integriscreen;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
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
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import static com.example.integriscreen.ISImageProcessor.applyCanny;
import static com.example.integriscreen.ISImageProcessor.rotate270;
import static com.example.integriscreen.ISImageProcessor.rotate90;
import static com.example.integriscreen.ISImageProcessor.storePic;
import static com.example.integriscreen.ISStringProcessor.concatTextBlocks;
import static com.example.integriscreen.ISStringProcessor.isChangeLegit;
import static com.example.integriscreen.LogManager.logF;
import static com.example.integriscreen.LogManager.logR;
import static com.example.integriscreen.LogManager.logW;
import static java.lang.System.currentTimeMillis;
import static org.opencv.imgproc.Imgproc.THRESH_BINARY;
import static org.opencv.imgproc.Imgproc.line;
import static org.opencv.imgproc.Imgproc.rectangle;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2, OnDataLoadedEventListener {

    private static final String TAG = "MainActivity";

    private enum OutputSelection {RAW, CANNY, DIFF, DETECT_TRANSFORMATION, DETECT_TEXT, INTEGRISCREEN}

    private static OutputSelection currentOutputSelection;
    private static SeekBar huePicker;
    private static TextView colorLabel;
    private static TextView textOutput;
    private static SeekBar detectPicker;
    private static CheckBox realignCheckbox;
    private static CheckBox limitAreaCheckbox;
    private static CheckBox liveCheckbox;
    private static Button detectButton;
    private static Button rawButton;
    private static Button startSubmitButton;
    private static Button snoozeButton;


    private static boolean optionalElementsVisible = true;

    private static LogManager LM;

    private Mat previousFrameMat;
    private Size frameSize;

    private Point upper_left, lower_right;

    private int color_border_hue = 130;
    private long currentFrameId = 0;
    private long previousFrameTimestamp = 0;
    private long currentFrameTimestamp = 0;
    private SparseArray<TextBlock> detectedTextBlocks = null;


    private static String submitButtonText = "(2) Submit";
    private static String startButtonText = "(1) Start";

    // This has to be a global variable if we run the check e.g. every 3-rd frame!
    private boolean foundAdditionalTextOnFrame = false;

    private int realignmentFrequency = 10;
    private int detectUnspecifiedTextFrequency = 5;

    private static int defaultFont = Core.FONT_HERSHEY_SIMPLEX;
//    private static Scalar detectedOCRColor = new Scalar(65, 143, 244);
//    private static Scalar expectedOCRColor = new Scalar(255, 128, 0);
    private static Scalar detectedOCRColor = new Scalar(0, 143, 0);
    private static Scalar expectedOCRColor = new Scalar(65, 143, 244);

    private static int comparisonFontScale = 2;
    private static int comparisonFontThickness = 4;
    private static String emptyVal = "[empty]";

    private boolean snoozedWarnings;
    private Vibrator v;
    private Ringtone r;


    private Point touchCenter = null;

    // the form created based on specs received from Server
    private HashMap<String, TargetForm> allLoadedForms;
    private TargetForm targetForm;
    private ArrayList<ActiveElementLog> activeElementLogs;
    private ArrayList<ChangeEventLog> allChangeLogs;

    // static address of the server to fetch list of forms
    // private static String serverUrl = "http://tildem.inf.ethz.ch";
    private static String serverUrl = "http://idvm-infk-capkun01.inf.ethz.ch:8085";

    private static String serverPageTypeURLParam = "?page_type=mobile_form";
    private static String stopFormId = "STOP"; // The header of the form that we use to stop experiments.

    //    private CameraBridgeViewBase _cameraBridgeViewBase;
    private CustomCameraView _cameraBridgeViewBase;

    // eu: square view to show the focus point
    private DrawingView drawingView;

    // TextRecognizer is the native vision API for text extraction
    private static TextRecognizer textRecognizer;

    private ISServerCommunicationManager formsListManager;

    public enum ISState {
        DETECTING_FRAME,       // Start detecting the green frame
        LOADING_FORM,          // Load the form based on title
        REALIGNING_AFTER_FORM_LOAD,    // Realign once more, this time speficially to the form ratio
        SUPERVISING_USER_INPUT,  // Accept user's input for as long as everything is OK
        SUBMITTING_DATA,       // Keep sending user data until server responds
        EVERYTHING_OK,         // Tell the user that everything is OK
        DATA_MISMATCH,        // There was a mismatch on the server. Show the diff to the user.
        ERROR_DURING_INPUT
    }

    ;  // We might end up here in case we detect something strange during user input
    public ISState currentISState;
    private boolean handActivityDetected;
    private JSONObject receivedJSONObject;
    private Timer submitDataTimer;
    private TimerTask submitDataTimerTask;

    private PerspectiveRealigner cameraFrameRealigner;
    private PerspectiveRealigner ISUpperFrameContinuousRealigner;

    private ISImageProcessor upperFrameISImageProcessor;
    private ISImageProcessor lowerFrameISImageProcessor;
    private ISImageProcessor wholeFrameISImageProcessor;

    private BaseLoaderCallback _baseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    logF(TAG, "OpenCV loaded successfully");
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
            logF(TAG, "Detector dependencies are not yet available.");

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(this, "Low Storage!!!", Toast.LENGTH_LONG).show();
                logF(TAG, "Low Storage!!!");
            }
        }

        LM = new LogManager(getApplicationContext(), this);

        allLoadedForms = new HashMap<>();

        formsListManager = new ISServerCommunicationManager(serverUrl, getApplicationContext());

        currentOutputSelection = OutputSelection.RAW;
//        currentOutputSelection = OutputSelection.CANNY;

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);


        // Permissions for Android 6+
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.VIBRATE},
                1);

//        _cameraBridgeViewBase = (CameraBridgeViewBase) findViewById(R.id.main_surface);
        _cameraBridgeViewBase = (CustomCameraView) findViewById(R.id.main_surface);

        // Uncomment to set one of the upper bounds on the camera resolution (the other is the preview View size)
        // To hardcode the resolution, find "// calcWidth = 1920;" in CameraBridgeViewBase
        // Ivo's phone: 960x540, 1280x720 (1M), 1440x1080 (1.5M), 1920x1080 (2M)
        // _cameraBridgeViewBase.setMaxFrameSize(1440, 1080);
        _cameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        _cameraBridgeViewBase.setCvCameraViewListener(this);
        _cameraBridgeViewBase.enableFpsMeter();

        // Deal with the UI element bindings
        colorLabel = (TextView) findViewById(R.id.colorLabel);
        textOutput = (TextView) findViewById(R.id.textOutput);
        detectPicker = (SeekBar) findViewById(R.id.detect_method);
        realignCheckbox = (CheckBox) findViewById(R.id.realignCheckBox);
        limitAreaCheckbox = (CheckBox) findViewById(R.id.limitAreaCheckBox);
        liveCheckbox = (CheckBox) findViewById(R.id.liveCheckbox);

        rawButton = (Button) findViewById(R.id.raw);
        detectButton = (Button) findViewById(R.id.detect_frame);
        startSubmitButton = (Button) findViewById(R.id.ISStart);
        snoozeButton = (Button) findViewById(R.id.ISSnooze);

        // initialize logs
        activeElementLogs = new ArrayList<>();
        allChangeLogs = new ArrayList<>();

        cameraFrameRealigner = new PerspectiveRealigner();
        ISUpperFrameContinuousRealigner = new PerspectiveRealigner();
        upperFrameISImageProcessor = new ISImageProcessor();
        lowerFrameISImageProcessor = new ISImageProcessor();
        wholeFrameISImageProcessor = new ISImageProcessor();

        huePicker = (SeekBar) findViewById(R.id.colorSeekBar);
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
                float[] hsv = new float[3];
                hsv[0] = (float) progress;
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
            logF(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, _baseLoaderCallback);
        } else {
            logF(TAG, "OpenCV library found inside package. Using it!");
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
                    logF("TAG", "Permission granted");
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
        logF("Toast Output", outString);
        logF("toastOutput", outString);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), outString, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void outputOnUILabel(final String textToShow) {
//        final String textToShow = outputText;
        logF("UILabel Output", textToShow);
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

    public void onClickISSnooze(View view) {
        if (snoozeButton.getText().equals("Snooze"))
            snoozeWarnings();
        else
            unsnoozeWarnings();
    }

    public void handleSubmit() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                startSubmitButton.setText(startButtonText);
                startSubmitButton.setBackgroundColor(Color.parseColor("#87CEFA")); // Light blue
            }
        });

        if (currentISState == null)
            return;

        // audit logs before submitting data
        auditActiveElementsLogs(0);
        logF("clickSubmit", currentISState.name());

        if (targetForm != null && targetForm.isLoaded)
            transitionISSTo(ISState.SUBMITTING_DATA);
        else
            outputOnToast("The form is not loaded yet!");
    }

    public void onClickSubmitData(View view) {
        handleSubmit();
    }

    private void cleanAllData() {
        // This ensures state machine goes from the beginning
        currentISState = ISState.DETECTING_FRAME;

        // Either delete only the specified one, or all of them
//        if (targetForm != null)
//            allLoadedForms.remove(targetForm.formUrl);
        allLoadedForms = new HashMap<>();
    }

    public void onClickISReset(View view) {
        realignCheckbox.setChecked(false);
        currentOutputSelection = OutputSelection.RAW;

        cleanAllData();
        outputOnUILabel("Input reset.");

        startIntegriScreen();
    }

    public void toggleAllOptionalElementsVisibility() {
        int newVisibility = (optionalElementsVisible) ? View.INVISIBLE : View.VISIBLE;

        detectButton.setVisibility(newVisibility);
        rawButton.setVisibility(newVisibility);
        huePicker.setVisibility(newVisibility);
        detectPicker.setVisibility(newVisibility);
        colorLabel.setVisibility(newVisibility);
        limitAreaCheckbox.setVisibility(newVisibility);
        realignCheckbox.setVisibility(newVisibility);
        liveCheckbox.setVisibility(newVisibility);
        textOutput.setVisibility(newVisibility);

        /*
        if (newVisibility == View.INVISIBLE) {
            _cameraBridgeViewBase.disableFpsMeter();
        } else {
            _cameraBridgeViewBase.enableFpsMeter();
        }*/

        optionalElementsVisible = (newVisibility == View.VISIBLE);
    }

    public void onClickToggleOptions(View view) {
        toggleAllOptionalElementsVisibility();
    }

    public void onClickShowColor(View view) {
        currentOutputSelection = OutputSelection.DETECT_TRANSFORMATION;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            huePicker.setProgress(color_border_hue, true);
        }
    }

    public void onClickShowRaw(View view) {
        realignCheckbox.setChecked(false);
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

    public void unsnoozeWarnings() {
        snoozedWarnings = false;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            snoozeButton.setText("Snooze");
            snoozeButton.setBackgroundColor(Color.parseColor("#ffffbb33")); // Light blue
            }
        });

    }

    public void snoozeWarnings() {
        snoozedWarnings = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            snoozeButton.setText("Snoozed!");
            snoozeButton.setBackgroundColor(Color.RED); // Light blue
            }
        });


    }

    public void startIntegriScreen() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                startSubmitButton.setText(submitButtonText);
                startSubmitButton.setBackgroundColor(Color.GREEN);
            }
        });

        unsnoozeWarnings();
        currentOutputSelection = OutputSelection.INTEGRISCREEN;

        transitionISSTo(ISState.DETECTING_FRAME);
        outputOnUILabel("Make the green frame visible in the upper part of the screen.");
    }

    public void onClickStartIS(View view) {
        if (startSubmitButton.getText().equals(startButtonText)) {
            startIntegriScreen();
        } else {
            handleSubmit();
        }
    }

    // Button callback to handle taking a picture
    public void onClickTakePic(View view) {
        logF(TAG, "Take picture button clicled.");
        takePicHighRes();
    }

    // Callback which is called by TargetForm class once the data is ready.
    public void onFormLoaded() {
        logF(TAG, "Form loaded!" + targetForm.toString());
        //Toast.makeText(getApplicationContext(), "Loaded form: " + targetForm.formUrl, Toast.LENGTH_SHORT).show();
    }

    public void onReceivedSubmitDataResponse(JSONObject responseJSON) {
        if (currentOutputSelection != OutputSelection.INTEGRISCREEN)
            cancelTimers();

        receivedJSONObject = responseJSON;
        // outputOnUILabel(responseJSON.toString());
        try {
            String responseVal = receivedJSONObject.getString("response");
            String attackLogs = receivedJSONObject.getString("attackLogs");
            if (responseVal.equals("match")) {
                logR("Attack Logs", "|" + attackLogs + "|");
                transitionISSTo(ISState.EVERYTHING_OK);
            } else if (responseVal.equals("nomatch")) {
                logR("Attack Logs", "|" + attackLogs + "|");
                transitionISSTo(ISState.DATA_MISMATCH);
            }

            // outputOnToast(receivedJSONObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
            logF(TAG + "error:", "responseJSON" + responseJSON.toString() + "|" + e.getMessage());
        }
    }


    // Callback when picture is taken
    public void onPicTaken(byte[] data) {
        makeWarningSound();
        return;

//        logF(TAG, "onPicTaken: " + currentTimeMillis());
//
//        //convert to mat
//        Mat matPic = Imgcodecs.imdecode(new MatOfByte(data), Imgcodecs.IMREAD_COLOR);
//        logF(TAG, "afterImdecode: " + currentTimeMillis());
//
//        // ------------
//        // We keep this for now mainly for testing
//        // store plain pic
//         storePic(data, "_byte");
//
//        Imgproc.cvtColor(matPic, matPic, Imgproc.COLOR_BGR2RGB);
//        logF(TAG, "afterCvtColor: " + currentTimeMillis());
//
//        //        matPic = matPic.submat(new Rect(0, 0, matPic.width() / 2, matPic.height()));
//
//        // detect the green framebox
//        PerspectiveRealigner myPerspective = new PerspectiveRealigner();
//        myPerspective.detectFrameAndComputeTransformation(matPic, color_border_hue);
//        myPerspective.realignImage(matPic);
//
//        // rotate the mat so we get the proper orientation
//        Mat rotatedPic = new Mat(1, 1, 1);
//        rotate90(matPic, rotatedPic);
//        rotatedPic.copyTo(matPic);
//
//        logF("TextDetection", "Detected: " + extractAndDisplayTextFromFrame(matPic));
//
//        // store mat image in drive
//        storePic(matPic, "_mat");
//        matPic.release();
    }


    private static String displayTextBlocksOnFrame(Mat currentFrameMat, SparseArray<TextBlock> detectedTextBlocks, Scalar textColor, boolean drawBox) {
        String concatDelim = "";
        String concatenatedText = "";
        int offset = 20;
        for (int i = 0; i < detectedTextBlocks.size(); ++i) {
            TextBlock item = detectedTextBlocks.valueAt(i);
            android.graphics.Rect rect = new android.graphics.Rect(item.getBoundingBox());

            if (item != null && item.getValue() != null) {
                int textHeight = (int) Imgproc.getTextSize(item.getValue(), defaultFont, comparisonFontScale, comparisonFontThickness, new int[1]).height;
                Imgproc.putText(currentFrameMat, item.getValue(), new Point(rect.left, rect.top + textHeight + 10),
                        defaultFont, comparisonFontScale, textColor, comparisonFontThickness);

                if (drawBox) {
                    Imgproc.rectangle(currentFrameMat, new Point(rect.left - offset, rect.top - offset),
                            new Point(rect.right + offset, rect.bottom + offset), new Scalar(255, 0, 0), 4);
                }

                concatenatedText += item.getValue() + concatDelim;
            }
        }
        return concatenatedText;
    }

    // This method:
    // 1) extracts all the text from a (specific part of) frame
    // 2) concatenates it
    // 3) draws it on the screen
    // 4) displays it on an UI label
    // 5) returns the concatenated text
    private static String extractAndDisplayTextFromFrame(Mat frameMat, Scalar textColor) {
        SparseArray<TextBlock> detectedTextBlocks = detect_text(frameMat);

        return displayTextBlocksOnFrame(frameMat, detectedTextBlocks, textColor, false);
    }

    private static String extractAndDisplayTextFromFrame(Mat frameMat) {
        return extractAndDisplayTextFromFrame(frameMat, new Scalar(0, 255, 0));
    }

    /**
     * The method to take a high resolution picture programatically.
     */
    private void takePicHighRes() {
        _cameraBridgeViewBase.setPictureSize(0);    // the best quality is set by default for pictures

        logF(TAG, "before the takePicture: " + currentTimeMillis());
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
    private String detectFormNameAndGetUrl(Mat rotatedUpperFrameMat) {
        Rect formTitleBox = new Rect(0, 0, rotatedUpperFrameMat.width() / 2, rotatedUpperFrameMat.height() / 8); // about 15%

        Imgproc.rectangle(rotatedUpperFrameMat, formTitleBox.tl(), formTitleBox.br(), new Scalar(255, 0, 0), 4);

        String formNameToLoad = concatTextBlocks(detect_text(rotatedUpperFrameMat.submat(formTitleBox)));
        formNameToLoad = formNameToLoad.replaceAll("\\s+","");

        return formsListManager.getFormURLFromName(formNameToLoad);
    }

    private void storeAllFormVerificationResults() {
        int formCnt = 0;
        Vector<String> formSuccesses = new Vector<>();
        Vector<String> formFailures = new Vector<>();

        int elementCnt = 0;
        Vector<Pair<String, String> > elementMismatches = new Vector<>();
        for(String currentUrl: allLoadedForms.keySet()) {
            TargetForm currentForm = allLoadedForms.get(currentUrl);
            if (currentForm.pageId.equals(stopFormId))
                continue;

            ++formCnt;
            elementCnt += currentForm.allElements.size();
            if (currentForm.initiallyVerified) {
                formSuccesses.add(currentUrl);
            } else {
                formFailures.add(currentUrl);

                for(UIElement currentElement: currentForm.allElements) {
                    if (!currentElement.everVerified) {
                        elementMismatches.add(currentElement.lastMismatch);
                    }
                }
            }
        }

        logR("Overall form success rate", String.valueOf((double)formSuccesses.size() / formCnt));
        String allFormFailuresList = "\n";
        for(String failForm : formFailures)
            allFormFailuresList += failForm + "\n";

        logR("All form failures", allFormFailuresList);


        logR("Overall element success rate", String.valueOf((double)(elementCnt - elementMismatches.size()) / elementCnt));
        String allElementMismatches = "\n";
        for(Pair<String, String> mismatch: elementMismatches)
            allElementMismatches += "|" + mismatch.first + "|\n|" + mismatch.second + "|\n\n";

        logR("All element mismatches", allElementMismatches);
    }

    public TargetForm loadFormBasedOnUrl(String formUrlToLoad, Mat rotatedUpperFrameMat) {
        if (formUrlToLoad == null) return null;

        if (allLoadedForms.get(formUrlToLoad) == null) {
            TargetForm newForm = new TargetForm(getApplicationContext(), formUrlToLoad, rotatedUpperFrameMat.width(), this);
            allLoadedForms.put(formUrlToLoad, newForm);

            logF("Creating new form", formUrlToLoad);
            return newForm;
        }
        else {
            TargetForm existingForm = allLoadedForms.get(formUrlToLoad);
            existingForm.makeAllDirty();
            logF("Re-loading existing form", formUrlToLoad);
            return existingForm;
        }
    }

    static List<ISState> dontDetectTransformation = Arrays.asList(
            ISState.SUPERVISING_USER_INPUT,
            ISState.SUBMITTING_DATA,
            ISState.EVERYTHING_OK,
            ISState.DATA_MISMATCH);

    boolean shouldDetectTransformation(ISState currentISState) {
        // If it's true, try realigning every Nth frame
        if (liveCheckbox.isChecked() || currentFrameId % realignmentFrequency == 0)
            return true;

        if (dontDetectTransformation.contains(currentISState))
            return false;

        return true;
    }

    private void cancelTimers() {
        // if (submitDataTimer != null) outputOnToast("Cancelling the existing timer.");

        if (submitDataTimerTask != null) submitDataTimerTask.cancel();
        if (submitDataTimer != null) submitDataTimer.cancel();
    }

    private void focusToForm() {
        // Careful to take screen rotation into account!
        int x = (int) _cameraBridgeViewBase.getWidth() / 4;
        int y = (int) _cameraBridgeViewBase.getHeight() / 2;
        int squareSize = 200;

        _cameraBridgeViewBase.focusAt(x, y, squareSize);
        logF(TAG, "Focus is set automatically to the midle point of the upper-half of the screen");
    }

    private void executeISStateEntryActions(ISState newState)
    {
        if (newState == ISState.DETECTING_FRAME) {
            // If there were any previous timers, turn them off
            cancelTimers();

            // Ensure that focus is properly set
            focusToForm();

            // Start realigning
            realignCheckbox.setChecked(true);
        }
        else if (newState == ISState.SUBMITTING_DATA) {
            cancelTimers();
            // outputOnToast("Polling the server every 5 seconds...");

            submitDataTimer = new Timer();
            submitDataTimerTask= new TimerTask() {
                @Override
                public void run() {
                    // outputOnToast("Sending: " + (new Date()).toString());
                    // This could also be fetched from the form's form_action field (but we don't do it yet)
                    targetForm.submitFormData(serverUrl + "/IntegriScreenServer/MainServer" + serverPageTypeURLParam);
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
            handActivityDetected = true;
        else
            handActivityDetected = false;

        if (handsPart.channels() == 1)
            Imgproc.cvtColor(handsPart, handsPart, Imgproc.COLOR_GRAY2RGBA);

        if (optionalElementsVisible)
            handsPart.copyTo(currentFrameMat.submat(handsBox));
        handsPart.release();
    }

    static boolean impactedByChanges(Rect R, List<Pair<Rect, Integer>> changeLocations) {
        for(Pair<Rect, Integer> change : changeLocations) {
            android.graphics.Rect ch = new android.graphics.Rect(change.first.x,
                                                                 change.first.y,
                                                           change.first.x + change.first.width,
                                                         change.first.y + change.first.height);
            if (ch.intersects(R.x, R.y, R.x + R.width, R.y + R.height))
                return true;
        }
        return false;
    }

    private boolean tryLoadingNewForm(Mat currentFrameMat) {
        // In this case, we need to recreate the "non-realigned" image to make sure we are extracting the form
        Rect screenBox = new Rect(new Point(0, 0), new Point(currentFrameMat.width() / 2, currentFrameMat.height()));
        Mat screenPart = currentFrameMat.submat(screenBox);
        Mat rotatedPotentialFormMat = new Mat(1, 1, CvType.CV_8UC1);
        rotate90(screenPart, rotatedPotentialFormMat);

        PerspectiveRealigner titleDetector = new PerspectiveRealigner();
        titleDetector.realignImage(rotatedPotentialFormMat);

        String potentialFormUrl = detectFormNameAndGetUrl(rotatedPotentialFormMat);
        TargetForm newForm = loadFormBasedOnUrl(potentialFormUrl, rotatedPotentialFormMat);

        boolean didReaload = false;
        if (newForm != null && !newForm.formUrl.equals(targetForm.formUrl)) {
            targetForm = newForm;
            didReaload = true;
            startIntegriScreen();
        }
        rotatedPotentialFormMat.release();

        return didReaload;
    }

    SparseArray<TextBlock> detectUnspecifiedText(Mat currentFrameMat) {
        Mat allWhite = new Mat(currentFrameMat.height(), currentFrameMat.width(), currentFrameMat.type(), new Scalar(255, 255, 255));
        for(UIElement currentElement : targetForm.allElements) {
            // "Whiten the UI elements
            allWhite.submat(currentElement.box).copyTo(currentFrameMat.submat(currentElement.box));
        }
//        storePic(currentFrameMat, "ui_el_removed");
        allWhite.release();

        SparseArray<TextBlock> detectedTextBlocks = detect_text(currentFrameMat);

       // storePic(currentFrameMat, "text_extracted");
        return detectedTextBlocks;
    }

    boolean processRotatedUpperPart(Mat rotatedUpperPart, Mat nonRealignedUpperPart)
    {
        // HANDLE THE UPPER PART!
        // ---------------- Based on the state that we are in, handle the upper part ------

        // Reload the new form if needed
        if (currentISState == ISState.SUPERVISING_USER_INPUT || currentISState == ISState.EVERYTHING_OK || currentISState == ISState.DATA_MISMATCH) {
            Mat diffsUpperPart = rotatedUpperPart.clone();
            List<Pair<Rect, Integer>> changedLocations = upperFrameISImageProcessor.diffFramesAndGetAllChangeLocations(diffsUpperPart,
                    1, 1, 10);

            boolean acceptingInput = false;
            // Check if title was impacted. If it was, update the form.
            if (targetForm.titleElement != null && impactedByChanges(targetForm.titleElement.box, changedLocations)) {
                // If I managed to reload the form, just return
                if (tryLoadingNewForm(nonRealignedUpperPart))
                    return false;
            }
            if (targetForm.titleElement != null && currentISState == ISState.SUPERVISING_USER_INPUT) {
                acceptingInput = superviseUIChanges(rotatedUpperPart, changedLocations);

                // This is a special case which we use to handle handle automated tests

                if (acceptingInput && targetForm.initiallyVerified == false) {
                    if (targetForm.pageId.equals(stopFormId)) {
                        storeAllFormVerificationResults();
                    }

                    logR("Successfully verified form", targetForm.pageId);
                    targetForm.initiallyVerified = true;
                }

                // This is where we could choose to draw the black-and-white on the screen
                // Imgproc.cvtColor(diffsUpperPart, rotatedUpperPart, Imgproc.COLOR_GRAY2RGBA);
                diffsUpperPart.release();

                String allDiffAreas = "";
                for (Pair<Rect, Integer> P : changedLocations) {
                    allDiffAreas += " | " + P.second.toString();
                    Imgproc.rectangle(rotatedUpperPart, P.first.tl(), P.first.br(), new Scalar(255, 0, 255), 2);
                }

                if (touchCenter != null)
                    Imgproc.circle(rotatedUpperPart, touchCenter, 100, new Scalar(255, 0, 0), 5);

                // At the moment, we are not outputing this at all
                if (changedLocations.size() < 0)
                    outputOnUILabel("No. of diffs: " + changedLocations.size() + ", areas: " + allDiffAreas);

                return acceptingInput;
            }
        }

        // The execution only reaches this point if the app is neither supervising an existing form, nor reloading a new one

        if (currentISState == ISState.SUBMITTING_DATA) {
            logF("SubmittingData", "bla");
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

                    if (phoneVal.equals("")) phoneVal = emptyVal;
                    if (browserVal.equals("")) browserVal = emptyVal;

                    logF("diffs", elementID + "|" + browserVal);

                    UIElement currentElement = targetForm.getElementById(elementID);

                    int textHeight = (int)Imgproc.getTextSize(browserVal, defaultFont, comparisonFontScale, comparisonFontThickness, new int[1]).height;
                    Imgproc.putText(rotatedUpperPart, browserVal, new Point(currentElement.box.tl().x, currentElement.box.tl().y + textHeight - 40),
                            defaultFont, comparisonFontScale, expectedOCRColor, comparisonFontThickness);

                    textHeight = (int)Imgproc.getTextSize(phoneVal, defaultFont, comparisonFontScale, comparisonFontThickness, new int[1]).height;
                    Imgproc.putText(rotatedUpperPart, phoneVal, new Point(currentElement.box.tl().x, currentElement.box.tl().y + textHeight + 60),
                            defaultFont, comparisonFontScale, detectedOCRColor, comparisonFontThickness);

                    Imgproc.rectangle(rotatedUpperPart, currentElement.box.tl(), currentElement.box.br(), new Scalar(255, 0, 0), 4);
                }

            } catch (JSONException e) {
                e.printStackTrace();
                logF("ListOfForms", e.getMessage());
            }

        } else {
            extractAndDisplayTextFromFrame(rotatedUpperPart, new Scalar(255, 0, 0));
        }

        return false;
    }

    boolean handleUpperPart(Mat currentFrameMat, long mid_delim) {
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

        if (shouldDetectTransformation(currentISState)) {
            ISUpperFrameContinuousRealigner.detectFrameAndComputeTransformation(rotatedScreenPart, color_border_hue, false,10);

            if (currentISState == ISState.REALIGNING_AFTER_FORM_LOAD) {
                transitionISSTo(ISState.SUPERVISING_USER_INPUT);
            }
        }

        if (realignCheckbox.isChecked()) {
            Vector<Point> detectedCorners = ISUpperFrameContinuousRealigner.realignImage(rotatedScreenPart);

            // Draw just for perspective
//            for( Point P: detectedCorners) {
//                Imgproc.circle(rotatedScreenPart, P, 5, new Scalar(255, 0, 0), 5);
//                Imgproc.circle(rotatedScreenPart, P, 15, new Scalar(0, 255, 0), 5);
//            }

        }

        if (currentISState == ISState.DETECTING_FRAME && formsListManager.isReady()) {   // make sure that forms are already downloaded
            String potentialFormUrl = detectFormNameAndGetUrl(rotatedScreenPart);
            TargetForm newForm = loadFormBasedOnUrl(potentialFormUrl, rotatedScreenPart);
            if (newForm != null) {
                targetForm = newForm;
                transitionISSTo(ISState.LOADING_FORM);
            }
        }

        // Depending on the app state, run detection of text, find changes, etc.
        boolean acceptingInput = processRotatedUpperPart(rotatedScreenPart, currentFrameMat);

        rotate270(rotatedScreenPart, screenPart);
        rotatedScreenPart.release();

        if (screenPart.channels() == 1)
            Imgproc.cvtColor(screenPart, screenPart, Imgproc.COLOR_GRAY2RGBA);

        // Combine the two parts
        screenPart.copyTo(currentFrameMat.submat(screenBox));
        screenPart.release();

        return acceptingInput;
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
        boolean acceptingInput = handleUpperPart(currentFrameMat, mid_delim);


        // ======== Handle the lower part of the screen
        // This function detects the hands, updates the currentFrameMat and sets handActivityDetected variable
        detectHandsAndUpdateActivity(currentFrameMat, mid_delim);
        // =============================================


        // NOTE!!!! Before I started using a helper rotatedScreenMat, this caused memory leaks!
        Mat rotatedScreenMat = new Mat(1, 1, 1);
        if (currentISState == ISState.EVERYTHING_OK || currentISState == ISState.DATA_MISMATCH
                || currentISState == ISState.SUPERVISING_USER_INPUT || currentISState == ISState.SUBMITTING_DATA)
            rotate90(currentFrameMat, rotatedScreenMat);

        int textX = 300;
        int textY = 1400;
        // int handLineOffset = 150;
        int handLineOffset = (int)mid_delim + 10;

        if (currentISState == ISState.SUPERVISING_USER_INPUT) {
            String textToShow = (acceptingInput ? "All OK, continue" : "STOP Input!");
            Scalar textColor = (acceptingInput ? new Scalar(0, 255, 0): new Scalar(255, 0, 0));
            int fontScale = (acceptingInput ? 2 : 3);

            Imgproc.putText(rotatedScreenMat, textToShow, new Point(textX, textY),
                    defaultFont, fontScale, textColor,3);

            if (!acceptingInput) {
                Imgproc.putText(rotatedScreenMat, "Expected values", new Point(textX, textY + 200),
                        defaultFont, 2, expectedOCRColor, 3);

                Imgproc.putText(rotatedScreenMat, "Detected values", new Point(textX, textY + 300),
                        defaultFont, 2, detectedOCRColor, 3);
            }

            // Draw the separating line, choose color depending on activity
            Scalar handLineColor = (handActivityDetected) ? new Scalar(0, 255, 0) : new Scalar(255, 0, 0);
//            line(rotatedScreenMat, new Point(0, textY-handLineOffset), new Point(rotatedScreenMat.height (), textY-handLineOffset), handLineColor, 5);
            line(rotatedScreenMat, new Point(0, handLineOffset), new Point(rotatedScreenMat.height (), handLineOffset), handLineColor, 5);

        }
        else if (currentISState == ISState.SUBMITTING_DATA) {
            Imgproc.putText(rotatedScreenMat, "DATA SUBMITTED.", new Point(textX, textY),
                    defaultFont, 1.5, new Scalar(150, 150, 0), 3);

            Imgproc.putText(rotatedScreenMat, "Now submit from the browser.", new Point(textX, textY+70),
                    defaultFont, 1.5, new Scalar(150, 150, 0), 3);

        } else if (currentISState == ISState.EVERYTHING_OK) {
            Imgproc.putText(rotatedScreenMat, "PERFECT MATCH!", new Point(textX, textY),
                    defaultFont, 2, new Scalar(0, 255, 0), 5);
        } else if (currentISState == ISState.DATA_MISMATCH) {
            Imgproc.putText(rotatedScreenMat, "DATA MISMATCH!", new Point(textX, textY),
                    defaultFont, 2, new Scalar(255, 0, 0), 5);

            Imgproc.putText(rotatedScreenMat, "Browser submitted", new Point(textX, textY + 200),
                    defaultFont, 2, expectedOCRColor, 3);

            Imgproc.putText(rotatedScreenMat, "Phone submitted", new Point(textX, textY + 300),
                    defaultFont, 2, detectedOCRColor, 3);
        }

        if (currentISState == ISState.EVERYTHING_OK || currentISState == ISState.DATA_MISMATCH ||
                currentISState == ISState.SUPERVISING_USER_INPUT || currentISState == ISState.SUBMITTING_DATA)
            rotate270(rotatedScreenMat, currentFrameMat);

        rotatedScreenMat.release();

        return currentFrameMat;
    }


    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        currentFrameId = currentFrameId + 1;
        currentFrameTimestamp = currentTimeMillis();


        Mat currentFrameMat = inputFrame.rgba();
        frameSize = currentFrameMat.size();
//        logF(TAG, "Frame size: " + currentFrameMat.rows() + "x" + currentFrameMat.cols());

        if (currentOutputSelection == OutputSelection.INTEGRISCREEN) {
            return executeISStateMachine(currentFrameMat);
        }

        if (currentOutputSelection == OutputSelection.DETECT_TRANSFORMATION) {
            int hueCenter = huePicker.getProgress(); // get progress value from the progress bar, divide by 2 since this is what OpenCV expects
            int detection_option = detectPicker.getProgress();
            if (detection_option == 0) // just show the color detection
                PerspectiveRealigner.detectColor(currentFrameMat, currentFrameMat, hueCenter);
            else
                cameraFrameRealigner.detectFrameAndComputeTransformation(currentFrameMat, hueCenter, true);

            return currentFrameMat;
        }

        if (realignCheckbox.isChecked()) {
            if (liveCheckbox.isChecked() && currentOutputSelection != OutputSelection.DIFF && currentOutputSelection != OutputSelection.CANNY) { // Only continuiously realign if live is turned on?
                cameraFrameRealigner.detectFrameAndComputeTransformation(currentFrameMat, color_border_hue);
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

    private UIElement findActiveElementFromCorners(Vector<Point> corners) {
        for(int i = 0; i < targetForm.allElements.size(); ++i) {
            UIElement currentElement = targetForm.allElements.get(i);

            if (PerspectiveRealigner.similar(PerspectiveRealigner.rectToPointVector(currentElement.box), corners))
                return currentElement;
        }

        return null;
    }

    private void stopWarnings() {
        if (r != null && r.isPlaying()) {
            v.cancel();
            r.stop();
        }
    }

    private void makeWarningSound(){
        stopWarnings();

        if (snoozedWarnings)
            return;

        // Get instance of Vibrator from current Context
        v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Start without a delay
        // Vibrate for 400 milliseconds
        // Sleep for 0 milliseconds
        long[] pattern = {0, 400, 0};

        // The '0' here means to repeat indefinitely
        // '0' is actually the index at which the pattern keeps repeating from (the start)
        // To repeat the pattern from any other point, you could increase the index, e.g. '1'

//        v.vibrate(pattern, 1);
//       v.cancel(); // to stop the vibration

        Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        // RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if(alert == null)
            alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

        r = RingtoneManager.getRingtone(getApplicationContext(), alert);
        r.play();

        // Stop after 400ms
//        new Timer().schedule(new TimerTask() {
//            @Override
//            public void run() {
//                r.stop();
//            }
//        }, 400);
    }

    /**
     * This method takes as input the list of changes on the screen and confirms the integrity
     * of screen changes
     */

    long timeOfLastFocusChange; // last time that active element switched
    long timeOfLastUpdateOfActiveElementValue;

    long minTimeOnEditedActiveElement = 1500;
    long minTimeAfterActiveElementEdit = 200;
    UIElement previousActiveElement = null;
    private boolean superviseUIChanges(Mat realignedUpperFrame, List<Pair<Rect, Integer>> changedLocations) {
        long diffTimestamp = currentFrameTimestamp - previousFrameTimestamp;

        int hueActiveElement = 220;
        Vector<Point> activeElementCorners = ISUpperFrameContinuousRealigner.detectRectangleCoordinates(realignedUpperFrame, hueActiveElement);
        UIElement activeElement = findActiveElementFromCorners(activeElementCorners);

        // Store the active element for every frame
        String activeElementId = "null";
        if (activeElement != null)
            activeElementId = activeElement.id;

        ChangeEventLog eventLog = new ChangeEventLog(currentFrameTimestamp,
                activeElementId,
                true,
                "noocr",
                "noocr");
        allChangeLogs.add(eventLog);

//        logF("ElementChanges", "Total changes: " + changedLocations.size());


        String activeElementNewValue = null;
        boolean allowChanges = true;


        // Check if Active element is changing too quickly
        if (activeElement != previousActiveElement) {
            if (previousActiveElement != null && timeOfLastUpdateOfActiveElementValue > timeOfLastFocusChange) {
                // This happens if someone changes the active element and moves focus in less than 2 seconds in total
                if (currentFrameTimestamp - timeOfLastFocusChange < minTimeOnEditedActiveElement) {
                    logW("Potential attack (UI not acting according to specification!)", "Active element changing too quickly! " + previousActiveElement.id);
                    allowChanges = false;
                }

                // This happens if after element changes, the focus changes too quickly
                if (currentFrameTimestamp - timeOfLastUpdateOfActiveElementValue < minTimeAfterActiveElementEdit) {
                    logW("Potential attack (UI not acting according to specification!)", "Focus changing too quickly after element edit: " + previousActiveElement.id);
                    allowChanges = false;
                }
            }

            timeOfLastFocusChange = currentFrameTimestamp;
            previousActiveElement = activeElement;
        }

        if (limitAreaCheckbox.isChecked()) {
            // Only re-detect on every 5-th frame because users anyways don't notice otherwise
            if (currentFrameId % detectUnspecifiedTextFrequency == 0 || detectedTextBlocks == null) {
                Mat noUIElementsMat = new Mat(1, 1, 1);
                realignedUpperFrame.copyTo(noUIElementsMat);

                detectedTextBlocks = detectUnspecifiedText(noUIElementsMat);
                noUIElementsMat.release();
            }

            String detectedString = displayTextBlocksOnFrame(realignedUpperFrame, detectedTextBlocks, detectedOCRColor, true);
            foundAdditionalTextOnFrame = (detectedString.length() > 0);
        } else {
            foundAdditionalTextOnFrame = false;
        }

        // If anything additional was found, don't allow changes!
        if (foundAdditionalTextOnFrame) {
            logW("Potential attack", "Unspecified text on the screen");
            allowChanges = false;
        }

        for(UIElement currentElement : targetForm.allElements) {
            // If it has been Ok before and nothing seems to have changed, skip this element
            if (!currentElement.dirty && !impactedByChanges(currentElement.box, changedLocations))
                continue;


            // Read the element value from the current frame
            String newValue = concatTextBlocks(detect_text(realignedUpperFrame.submat(currentElement.box)));

            // Check if diff there is a change in comparison to previous value
            if (ISStringProcessor.almostIdenticalString(currentElement.currentValue, newValue, false)) {
                // Set this element to not be dirty and continue to next one
                currentElement.dirty = false;
                currentElement.everVerified = true;
                continue;
            }

            // Here we store the last mismatch that an element has had before it was ever accepted
            //   as successfully verified.
            currentElement.lastMismatch = new Pair<>(currentElement.currentValue, newValue);

            // Check if this changing element is not the active element
            if (currentElement != activeElement) {
                currentElement.dirty = true;

                allowChanges = false;
                logF("Potential attack", "Non-active element changing from: |" + currentElement.currentValue + "|  ___ to ___ |" + newValue + "|");

                ChangeEventLog activeElementEventLog = new ChangeEventLog(currentFrameTimestamp,
                        currentElement.id,
                        false,
                        currentElement.currentValue,
                        newValue);
                allChangeLogs.add(activeElementEventLog);

                continue;
            }


            timeOfLastUpdateOfActiveElementValue = currentFrameTimestamp;


            // Ok, when active element is changing, are hands also active?
            if (!handActivityDetected) {  // We are now an active element --> Has there been any hand activity?
                logW("Potential attack", "No hand activity, but active element changing from: |" + currentElement.currentValue + "|  ___ to ___ |" + newValue + "|");
                // TODO: at the moment, we are just logging this, but not storing it anywhere
                //allowChanges = false;
                //continue;
            }

            if (!isChangeLegit(currentElement.currentValue, newValue, diffTimestamp)) {
                String message = "Non-legit change from |" + currentElement.currentValue + "| to |" + newValue + "| in " + diffTimestamp + "ms";
                logW("Potential attack", message);
                // TODO: do we want to enforce this one yet?
                // allowChanges = false;
                // continue;
            }

            if (currentElement.editable == false) {
                logW("Potential attack", "Non-editable active element is changing from |" + currentElement.currentValue + "| to |" + newValue + "|", true);
                allowChanges = false;
                continue;
            }



            // Store the active element value update that will happen if changes are allowed
            activeElementNewValue = newValue;
        }

        // Plot out all the elements
        for(UIElement currentElement : targetForm.allElements) {
            Scalar rectangle_color;
            int rectangle_thickness;
            if (currentElement.dirty) {
                // Use red rectangle
                rectangle_color = new Scalar(255, 0, 0);
                rectangle_thickness = 4;

                String expectedVal = (currentElement.currentValue.equals("")) ? emptyVal : currentElement.currentValue;
                String detectedVal  = (currentElement.lastMismatch.second.equals("")) ? emptyVal : currentElement.lastMismatch.second;

                // Output the text on the UI elements
                int textHeight = (int) Imgproc.getTextSize(expectedVal, defaultFont, comparisonFontScale, comparisonFontThickness, new int[1]).height;
                Imgproc.putText(realignedUpperFrame, expectedVal, new Point(currentElement.box.tl().x, currentElement.box.tl().y + textHeight - 40),
                        defaultFont, comparisonFontScale, expectedOCRColor, comparisonFontThickness);

                // Show the OCR-d value which is different than expected
                textHeight = (int) Imgproc.getTextSize(detectedVal, defaultFont, comparisonFontScale, comparisonFontThickness, new int[1]).height;
                Imgproc.putText(realignedUpperFrame, detectedVal, new Point(currentElement.box.tl().x, currentElement.box.tl().y + textHeight + 60),
                        defaultFont, comparisonFontScale, detectedOCRColor, comparisonFontThickness);

            } else {
                if (allowChanges) {
                    // Draw all rectangles in green
                    rectangle_color = new Scalar(0, 255, 0);
                } else {
                    // Draw all others with some gray-ish color
                    rectangle_color = new Scalar(180, 180, 180);
                }
                rectangle_thickness = 2;
            }

            // Plot the borders of the UI elements
            Imgproc.rectangle(realignedUpperFrame, currentElement.box.tl(), currentElement.box.br(), rectangle_color, rectangle_thickness);
        }

        // TODO: this needs to be fixed / removed
        // This is an ugly hack for now.

        if (activeElement != null && allowChanges) {
            // If we found an active element, we draw a green rectangle
            Imgproc.rectangle(realignedUpperFrame, activeElement.box.tl(), activeElement.box.br(), new Scalar(0, 255, 0), 8);
        } else {
            // If we haven't been able to correlat with any UI element, we draw a red rectangle where we find it
            Rect myRect = new Rect(activeElementCorners.get(0), activeElementCorners.get(2));
            // But only if it's large enough, otherwise it's likely just noise
            if (myRect.area() > 500)
                Imgproc.rectangle(realignedUpperFrame, myRect.tl(), myRect.br(), new Scalar(255, 0, 0), 8);
        }

        if (activeElementNewValue != null) {
            if (allowChanges) {
                // Ok, everything seems to be fine, we can update the current active element
                targetForm.activeElementLastEdit = currentFrameTimestamp;

                String oldValue = activeElement.currentValue;

                // Update the value
                activeElement.currentValue = activeElementNewValue;

                // Store in the logs
                ActiveElementLog newLogEntry = new ActiveElementLog(activeElement.id, activeElement.id,
                        oldValue, activeElementNewValue, currentFrameTimestamp, diffTimestamp);
                // todo eu: store this info targetForm.pageId in the logs
                activeElementLogs.add(newLogEntry);
            } else {
                // We beep to the user to indicate that they should not continue editing
                makeWarningSound();
            }
        }

        previousFrameTimestamp = currentFrameTimestamp;
        return allowChanges;
    }


    /**
     *  This method audits the logs of changes on the user's computer as received on the phone.
     *  @lastEvents is the number of events to consider in the audit, 0 means all events
     */
    private void auditActiveElementsLogs(int lastEvents) {
        // tresholds
        long minTimeActive = 500;   // minimum duration that one element should be active
        int logsSize = activeElementLogs.size();
        int start = 0;
        if (lastEvents > 0)
            start = logsSize-lastEvents;

        // stores the duration an active element has been edited
        long sequentialEdits = 0;
        for (int i = start; i < logsSize; i++) {
            ActiveElementLog cLog = activeElementLogs.get(i);

            if (!isChangeLegit(cLog.oldValue, cLog.newValue, cLog.duration)) {
                Log.d(TAG, "------ Alert!!!!!");
            }
            
            if (cLog.oldId == null) {                       // first time an elements becomes active
                sequentialEdits = cLog.duration;
            }
            else if (cLog.newId.equals(cLog.oldId)) {       // same element being edited
                sequentialEdits += cLog.duration;
            }
            else {
                if (sequentialEdits < minTimeActive) {      // if one element has been updated very fast
                    Log.d(TAG, "------ Alert!!!!!");
                }
                sequentialEdits = cLog.duration;            // reset sequentialEdits
            }
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

    private void acceptDetectedValue(TargetForm targetForm, float x, float y) {
        double mxFormX = frameSize.width;
        double mxFormY = frameSize.height;
        double mxScreenX = _cameraBridgeViewBase.getWidth();
        double mxScreenY = _cameraBridgeViewBase.getHeight();

        double offsetX = ( mxScreenX - mxFormX * (mxScreenY / mxFormY) ) / 2;

        double resizedX = (x - offsetX) / ( (mxScreenX - offsetX) / mxFormX );
        double resizedY = y / (mxScreenY / mxFormY);

        double rotatedX = mxFormY - resizedY;
        double rotatedY = resizedX;

        touchCenter = new Point(rotatedX, rotatedY);
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                touchCenter = null;
            }
        }, 1000);

        for(UIElement currentElement: targetForm.allElements) {
            if (currentElement.dirty && currentElement.box.contains(new Point(rotatedX, rotatedY))) {
                logR("Snoozed warning", "ID: " + currentElement.id + ", expected: |" + currentElement.lastMismatch.first + "| detected: |" + currentElement.lastMismatch.second + "|");
                currentElement.currentValue = currentElement.lastMismatch.second;
            }
        }
    }

    // get motion events
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();

            logF("Screen Click", "Coordinates: " + x + ", " + y
                    + ", at: " + event.getEventTime());

            if (currentISState == ISState.SUPERVISING_USER_INPUT) {
                acceptDetectedValue(targetForm, x, y);
            } else {
                _cameraBridgeViewBase.focusAt(x, y, 100);
            }
        }
        return false;
    }

}

