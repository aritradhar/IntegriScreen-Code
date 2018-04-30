package com.example.integriscreen;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
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
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static org.opencv.imgproc.Imgproc.blur;
import static org.opencv.imgproc.Imgproc.line;
import static org.opencv.imgproc.Imgproc.rectangle;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2, OnDataLoadedEventListener {

    private static final String TAG = "MainActivity";

    private enum OutputSelection { RAW, CANNY, DIFF, DETECT_TRANSFORMATION, DETECT_TEXT, INTEGRISCREEN};
    private OutputSelection currentOutputSelection;
    private SeekBar huePicker;
    private TextView colorLabel;
    private TextView textOutput;
    private SeekBar detectPicker;
    private CheckBox realignCheckbox;
    private CheckBox limitAreaCheckbox;
    private CheckBox liveCheckbox;

    private Mat previousFrameMat;
    private Mat tmpMat;
    private Mat previousFrameMat2;
    private Mat tmpMat2;
    private Mat outputMat;

    private Mat matPic;

    // this is currently for "limited OCR"
    private int h_border_perc = 30;
    private int v_border_perc = 47;
    private Point upper_left, lower_right;

    private int skin_hue_estimate = 26;
    private int color_border_hue = 120;

    // Store the RequestQueue.
    public static RequestQueue queue;

    // the form created based on specs received from Server
    private TargetForm targetForm;

    // static address of the server to fetch list of forms
    private static String serverURL = "http://tildem.inf.ethz.ch/IntegriScreenServer/MainServer";
    private static String serverPageTypeURLParam = "?page_type=mobile_form";

    private HashMap<String, String> knownForms;

    //    private CameraBridgeViewBase _cameraBridgeViewBase;
    private CustomCameraView _cameraBridgeViewBase;

    // TextRecognizer is the native vision API for text extraction
    private TextRecognizer textRecognizer;


    private enum ISState {  DETECTING_FRAME,       // Start detecting the green frame
                            DETECTING_TITLE,       // Start OCR-ing to find the title
                            LOADING_FORM,          // Load the form based on title
                            REALIGNING_AFTER_FORM_LOAD,    // Realign once more, this time speficially to the form ratio
                            VERIFYING_UI,          // Start verifying that the UI is as expected
                            SUPERVISING_USER_INPUT,  // Accept user's input for as long as everything is OK
                            SUBMITTING_DATA,       // Keep sending user data until server responds
                            EVERYTHING_OK,         // Tell the user that everything is OK
                            DATA_MISMATCH,        // There was a mismatch on the server. Show the diff to the user.
                            ERROR_DURING_INPUT };  // We might end up here in case we detect something strange during user input
    private ISState currentISState;
    private boolean activityDetected;
    private JSONObject receivedJSONObject;
    private Timer submitDataTimer;
    private TimerTask submitDataTimerTask;

    private PerspectiveRealigner cameraFrameRealigner;
    private PerspectiveRealigner ISUpperFrameContinuousRealigner;

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

        // initialize the RequestQueue of volley
        queue = Volley.newRequestQueue(getApplicationContext());

        currentOutputSelection = OutputSelection.RAW;

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

        knownForms = new HashMap<String, String>();
        getListOfForms(serverURL);

        cameraFrameRealigner = new PerspectiveRealigner();
        ISUpperFrameContinuousRealigner = new PerspectiveRealigner();

        // We store without spaces to prevent problems with whitespace in OCR
//        knownForms.put("ComposeEmail1920x1080", "email_1920_1080.json");
//        knownForms.put("ComposeEmail1080x960", "email_1080_960.json");
//        knownForms.put("ComposeEmail", "email.json");

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

    private void outputOnToast(final String outString) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), outString, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void outputOnUILabel(final String textToShow) {
//        final String textToShow = outputText;
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
        Log.d("clickSubmit", currentISState.name());

        if (targetForm.isLoaded)
            transitionISSTo(ISState.SUBMITTING_DATA);
        else
            outputOnToast("The form is not loaded yet!");
    }

    public void onClickShowDiff(View view) {
        cleanSharedPreviousMats();
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

    public void onClickStartIS(View view) {
        currentOutputSelection = OutputSelection.INTEGRISCREEN;

        transitionISSTo(ISState.DETECTING_FRAME);
        outputOnUILabel("Make the green frame visible in the top part, then click Realign.");
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
        outputOnUILabel(responseJSON.toString());
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
        Log.d(TAG, "onPicTaken: " + System.currentTimeMillis());

        //convert to mat
        matPic = Imgcodecs.imdecode(new MatOfByte(data), Imgcodecs.IMREAD_COLOR);
        Log.d(TAG, "afterImdecode: " + System.currentTimeMillis());

        if (currentISState == ISState.VERIFYING_UI) {
            Mat rotatedFullRes = new Mat(1, 1, 1);
            rotate90(matPic.getNativeObjAddr(), rotatedFullRes.getNativeObjAddr());
            Rect frameLimit = targetForm.getFrameSize(matPic.height(), matPic.width());

//            storePic(rotatedFullRes, "_rotated");

            Mat croppedRotatedFullRes = new Mat(1, 1, 1);
            rotatedFullRes.submat(frameLimit).copyTo(croppedRotatedFullRes);

            PerspectiveRealigner fullResolutionPerspective = new PerspectiveRealigner();
            fullResolutionPerspective.detectFrameAndComputeTransformation(croppedRotatedFullRes, color_border_hue);
            fullResolutionPerspective.realignImage(croppedRotatedFullRes);

            if (validateAndPlotForm(croppedRotatedFullRes, targetForm)) {
                transitionISSTo(ISState.SUPERVISING_USER_INPUT);
            }
            else {
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
        Log.d(TAG, "afterCvtColor: " + System.currentTimeMillis());

        //        matPic = matPic.submat(new Rect(0, 0, matPic.width() / 2, matPic.height()));

        // detect the green framebox
        PerspectiveRealigner myPerspective = new PerspectiveRealigner();
        myPerspective.detectFrameAndComputeTransformation(matPic, color_border_hue, matPic.width(), matPic.height());
        myPerspective.realignImage(matPic);

        // rotate the mat so we get the proper orientation
        rotate90(matPic.getNativeObjAddr(), matPic.getNativeObjAddr());

        Log.d("TextDetection", "Detected: " + extractAndDisplayTextFromFrame(matPic));

        // store mat image in drive
        storePic(matPic, "_mat");
    }

    private void storePic(byte[] data, String extension) {
        String fileName = genFileName(extension);

        // Write the image in a file (in jpeg format)
        try {
            Log.d(TAG, "Saving byte[] to file: " + fileName);

            FileOutputStream fos = new FileOutputStream(fileName);
            fos.write(data);
            fos.close();

        } catch (java.io.IOException e) {
            Log.e("PictureDemo", "Exception in photoCallback", e);
        }
    }

    private void storePic(Mat mat, String extension) {
        String fileName = genFileName(extension);

        //convert Mat to Bitmap
        Bitmap bmpPic = Bitmap.createBitmap(mat.cols(), mat.rows(),
                Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bmpPic);

        // Write the image in a file (in jpeg format)
        try {
            Log.d(TAG, "Saving bitmap to file: " + fileName);

            FileOutputStream fos = new FileOutputStream(fileName);
//            fos.write(data);
            bmpPic.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();

        } catch (java.io.IOException e) {
            Log.e("PictureDemo", "Exception in photoCallback", e);
        }
    }

    private String genFileName(String extension) {
        // check if directory exists
        File dirIS = new File(Environment.getExternalStorageDirectory(), "Integriscreen");
        if(!dirIS.exists()) {
            dirIS.mkdirs();
        }

        // generate filename
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd_HH-mm-ss");
        String fileName = dirIS.getPath() +
                "/IS_" + sdf.format(new Date()) + extension + ".jpg";

        return fileName;
    }


    private boolean validateAndPlotForm(Mat currentFrameMat, TargetForm form) {
        boolean allElementsAsExpected = true;

        double scaleX = (double)currentFrameMat.width() / form.form_w_abs;
        double scaleY = (double)currentFrameMat.height() / form.form_h_abs;

        for(int i = 0; i < form.allElements.size(); ++i) {
            UIElement element = form.allElements.get(i);
            Rect rescaledBox = element.getRescaledBox(scaleX, scaleY);

            Log.d("box: ", rescaledBox.toString() + "|" + currentFrameMat.size());
            String detected = extractAndDisplayTextFromFrame(currentFrameMat.submat(rescaledBox));

            Scalar rectangle_color;
            if (detected.equals(element.defaultVal)) {
                rectangle_color = new Scalar(255, 255, 0);
            } else {
                rectangle_color = new Scalar( 255, 0, 0);
                // Output the text on the UI elements
                int textHeight = (int) Imgproc.getTextSize(element.defaultVal, Core.FONT_HERSHEY_SIMPLEX, 1.3, 1, new int[1]).height;
                Imgproc.putText(currentFrameMat, element.defaultVal, new Point(rescaledBox.x, rescaledBox.y + textHeight + 20),
                        Core.FONT_HERSHEY_SIMPLEX, 1.3, new Scalar(255, 0, 0));

                allElementsAsExpected = false;
            }

            // Plot the borders of the UI elements
            Imgproc.rectangle(currentFrameMat, rescaledBox.tl(), rescaledBox.br(), rectangle_color, 4);
        }

        return allElementsAsExpected;
    }


    // This method:
    // 1) extracts all the text from a (specific part of) frame
    // 2) concatenates it
    // 3) draws it on the screen
    // 4) displays it on an UI label
    // 5) returns the concatenated text
    private String extractAndDisplayTextFromFrame(Mat frameMat) {

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
        if (currentOutputSelection == OutputSelection.RAW && !liveCheckbox.isChecked())
            outputOnUILabel(concatenatedText);

        return concatenatedText;
    }

    /**
     * The method to take a high resolution picture programatically.
     */
    private void takePicHighRes() {
        _cameraBridgeViewBase.setPictureSize(0);    // the best quality is set by default for pictures

        Log.d(TAG, "before the takePicture: " + System.currentTimeMillis());
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
        outputMat = new Mat(1, 1, CvType.CV_8UC4);
        previousFrameMat = new Mat(1, 1, CvType.CV_8UC4);
        tmpMat = new Mat(1, 1, CvType.CV_8UC1);
        previousFrameMat2 = new Mat(1, 1, CvType.CV_8UC4);
        tmpMat2 = new Mat(1, 1, CvType.CV_8UC1);
    }

    public void onCameraViewStopped() {
        outputMat.release();
        previousFrameMat.release();
        tmpMat.release();
        previousFrameMat2.release();
        tmpMat2.release();
    }

    // based on https://docs.opencv.org/2.4/doc/tutorials/imgproc/imgtrans/canny_detector/canny_detector.html
    // This is using Java-based openCV
    public void applyCanny(Mat input_gray, Mat output_gray) {
        double lowThreshold = 30;
        double ratio = 2;
        int kernel_size = 3;

        Mat src_blurred = new Mat(3, 3, CvType.CV_8UC4);

        /// Reduce noise with a kernel 3x3
        blur( input_gray, src_blurred, new Size(3,3) );

        /// Canny detector
        Imgproc.Canny( src_blurred, output_gray, lowThreshold, lowThreshold*ratio, kernel_size, true );

        // This copies using the "detected_edges" as a mask
        // src_gray.copyTo( dst, detected_edges);


        src_blurred.release();
    }

    // this one will probably be of size 1080 / 960 -> since the form needs to know how much height it is allowed to take
    public boolean loadFormBasedOnName(Mat rotatedUpperFrameMat, int maxScreenHeight) {
        Rect formTitleBox = new Rect(0, 0, rotatedUpperFrameMat.width() / 2, rotatedUpperFrameMat.height() / 8); // about 15%

        Imgproc.rectangle(rotatedUpperFrameMat, formTitleBox.tl(), formTitleBox.br(), new Scalar(255, 0, 0), 4);

        // At the moment we are strongly and implicitly!!! hardcoding the values "string -> URL"
        String formToLoad = concatTextBlocks(detect_text(rotatedUpperFrameMat.submat(formTitleBox)));

        formToLoad = formToLoad.replaceAll("\\s+","");

        String urlToLoad = knownForms.get(formToLoad);
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

    private void cleanSharedPreviousMats() {
        // Clean previous data
        previousFrameMat.release();
        previousFrameMat = new Mat(1, 1, CvType.CV_8UC1);
        previousFrameMat2.release();
        previousFrameMat2 = new Mat(1, 1, CvType.CV_8UC1);
    }

    private void executeISStateEntryActions(ISState newState)
    {
        if (newState == ISState.DETECTING_FRAME) {
            cancelTimers();
            cleanSharedPreviousMats();

            realignCheckbox.setChecked(true);
        }
        else if (newState == ISState.VERIFYING_UI) {
            // Once it is ready, we use this to verify as well
            takePicHighRes();

            // Stop refocusing!
            _cameraBridgeViewBase.stopRefocusing();
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

        PerspectiveRealigner.detectColor(handsPart, handsPart, skin_hue_estimate);

        // Since handsPart will get changed in the process, store the current purely black and white version now for later.
        handsPart.copyTo(tmpMat2);

        // If we don't have a stored previous frame, just use the latest one
        if (!previousFrameMat2.size().equals(handsPart.size()) ||
                previousFrameMat2.type() != handsPart.type()) {
            handsPart.copyTo(previousFrameMat2);
        }

        compute_diff(handsPart.getNativeObjAddr(),
                previousFrameMat2.getNativeObjAddr(),
                handsPart.getNativeObjAddr(),
                2, 1);

        // This is where we start computing the components
        Mat matLabels = new Mat(1, 1, CvType.CV_8UC1);
        Mat matStats = new Mat(1, 1, CvType.CV_8UC1);
        Mat matCentroids = new Mat(1, 1, CvType.CV_8UC1);
        int numComponents = find_components(handsPart.getNativeObjAddr(),
                matLabels.getNativeObjAddr(),
                matStats.getNativeObjAddr(),
                matCentroids.getNativeObjAddr());
        Log.d("num_comp", String.valueOf(numComponents));

        if (numComponents > 1)
            activityDetected = true;
        else
            activityDetected = false;

        matLabels.release();
        matStats.release();
        matCentroids.release();

        // Store for the next frame
        tmpMat2.copyTo(previousFrameMat2);

        // --------------------------------------

        // Convert back to 4 channel colors
        Imgproc.cvtColor(handsPart, handsPart, Imgproc.COLOR_GRAY2RGBA);

        handsPart.copyTo(currentFrameMat.submat(handsBox));
        handsPart.release();
    }

    // This changes the frameMat and also computes the locations of all changes
    List<Rect> diffFramesAndGetAllChangeLocations(Mat frameMat, int morphSize, int downscaleFactor) {

        // TODO(ivo): I need to play with parameters for min area, etc.

        // Convert to black and white
        Mat frameMatBW = new Mat(frameMat.size(), CvType.CV_8UC1);
        Imgproc.cvtColor(frameMat, frameMatBW, Imgproc.COLOR_RGBA2GRAY);

        // Since frameMatBW will get changed, store the current purely black and white version now for later.
        frameMatBW.copyTo(tmpMat);

        // If we don't have a stored previous frame, just use the latest one
        if (!previousFrameMat.size().equals(frameMatBW.size()) ||
                previousFrameMat.type() != frameMatBW.type()) {
            frameMatBW.copyTo(previousFrameMat);
        }

        // TODO(ivo): allow specifying diff parameteres from here (threshold, min size, etc.)
        // TODO(ivo): diffing should also probably be a separate class given the "previous frame"
        compute_diff(frameMatBW.getNativeObjAddr(),
                previousFrameMat.getNativeObjAddr(),
                frameMatBW.getNativeObjAddr(),
                morphSize, downscaleFactor);

        // Store for the next frame
        tmpMat.copyTo(previousFrameMat);



        Mat labeled = new Mat(frameMatBW.size(), frameMatBW.type());

        // Extract components
        Mat rectComponents = Mat.zeros(new Size(0, 0), 0);
        Mat centComponents = Mat.zeros(new Size(0, 0), 0);
        Imgproc.connectedComponentsWithStats(frameMatBW, labeled, rectComponents, centComponents);

        // Collect regions info
        int[] rectangleInfo = new int[5];
        double[] centroidInfo = new double[2];

        List<Rect> allRects = new ArrayList<>();
        for(int i = 1; i < rectComponents.rows(); i++) {
            // Extract bounding box
            rectComponents.row(i).get(0, 0, rectangleInfo);
            Rect rectangle = new Rect(rectangleInfo[0], rectangleInfo[1], rectangleInfo[2], rectangleInfo[3]);
            allRects.add(rectangle);

            // Extract centroids
//            centComponents.row(i).get(0, 0, centroidInfo);
//            Point centroid = new Point(centroidInfo[0], centroidInfo[1]);

            Log.d("comps rect", rectangleInfo[0] + " | " +rectangleInfo[1] + " | " +rectangleInfo[2] + " | " +rectangleInfo[3] + " | " +rectangleInfo[4]);
            Log.d("comps cent", centroidInfo[0] + " | " + centroidInfo[1]);
        }

        // Convert back to RGBA to be shown on the screen
        Imgproc.cvtColor(frameMatBW, frameMat, Imgproc.COLOR_GRAY2RGBA);

        frameMatBW.release();
        labeled.release();
        rectComponents.release();
        centComponents.release();

        return allRects;
    }

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
            List<Rect> changedLocations = diffFramesAndGetAllChangeLocations(rotatedUpperPart, 2, 1);

            // TODO(enis): this is where you continue

            outputOnUILabel("DIFF components: " + changedLocations.size());

//            Log.d("num_comp", String.valueOf(numComponents));
            if (changedLocations.size() > 1 && !activityDetected)
                outputOnUILabel("Warning: UI changes, but no hand movement!");

        } else if (currentISState == ISState.SUBMITTING_DATA) {
            Log.d("SubmittingData", "bla");
        } else if (currentISState == ISState.EVERYTHING_OK) {
            //          outputOnUILabel(receivedJSONObject.toString());
        } else if (currentISState == ISState.DATA_MISMATCH) {
            //    outputOnUILabel(receivedJSONObject.toString());

            try {
                JSONArray mismatchElements = receivedJSONObject.getJSONArray("diffs");

                // iterate through all elements
                // for(JSONObject currDiff : mismatchElements) {
                for(int i = 0; i < mismatchElements.length(); ++i) {
                    JSONObject currDiff = mismatchElements.getJSONObject(i);

                    String elementID = currDiff.getString("key");
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
        rotate90(screenPart.getNativeObjAddr(), rotatedScreenPart.getNativeObjAddr());

        if (shouldDetectTransformation(currentISState)) { // during "verifying UI", we need to have a still screen
            ISUpperFrameContinuousRealigner.detectFrameAndComputeTransformation(rotatedScreenPart, color_border_hue);

            if (currentISState == ISState.REALIGNING_AFTER_FORM_LOAD) {
                transitionISSTo(ISState.VERIFYING_UI);
            }
        }

        if (realignCheckbox.isChecked()) {
            ISUpperFrameContinuousRealigner.realignImage(rotatedScreenPart);
        }

        if (currentISState == ISState.DETECTING_FRAME && !knownForms.isEmpty()) {   // make sure that forms are already downloaded
            if (loadFormBasedOnName(rotatedScreenPart, currentFrameMat.width())) { // It is important that this function only returns true if such a form exists!
                transitionISSTo(ISState.LOADING_FORM);
            }
        }

        // Depending on the app state, run detection of text, find changes, etc.
        processRotatedUpperPart(rotatedScreenPart);

        rotate270(rotatedScreenPart.getNativeObjAddr(), screenPart.getNativeObjAddr());
        rotatedScreenPart.release();

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


        int textX = 1000;
        int textY = 500;
        if (currentISState == ISState.EVERYTHING_OK) {
            Imgproc.putText(currentFrameMat, "EVERYTHING OK!", new Point(textX, textY),
                    Core.FONT_HERSHEY_SIMPLEX, 3, new Scalar(0, 255, 0),3);
        } else if (currentISState == ISState.DATA_MISMATCH) {
            Imgproc.putText(currentFrameMat, "MISMATCH!", new Point(textX, textY),
                    Core.FONT_HERSHEY_SIMPLEX, 5, new Scalar(255, 0, 0), 5);

            Imgproc.putText(currentFrameMat, "BROWSER", new Point(textX, textY + 200),
                    Core.FONT_HERSHEY_SIMPLEX, 3, new Scalar(0, 255, 255), 5);

            Imgproc.putText(currentFrameMat, "PHONE", new Point(textX, textY + 300),
                    Core.FONT_HERSHEY_SIMPLEX, 3, new Scalar(0, 255, 0), 5);

        } else {
            // Draw the separating line, choose color depending on activity
            Scalar lineColor = (activityDetected) ? new Scalar(0, 255, 0) : new Scalar(255, 0, 0);
            line(currentFrameMat, new Point(mid_delim, currentFrameMat.height()), new Point(mid_delim, 0), lineColor, 3);
        }

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
            if (liveCheckbox.isChecked() && currentOutputSelection != OutputSelection.DIFF) { // Only continuiously realign if live is turned on?
                cameraFrameRealigner.detectFrameAndComputeTransformation(currentFrameMat, color_border_hue, currentFrameMat.width(), currentFrameMat.height());
            }

            cameraFrameRealigner.realignImage(currentFrameMat, currentFrameMat);
        }

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
            Imgproc.cvtColor(currentFrameMat, tmpMat, Imgproc.COLOR_RGB2GRAY);
            applyCanny(tmpMat, currentFrameMat);
            return currentFrameMat;
        }

        if (currentOutputSelection == OutputSelection.DIFF) {
            // Convert to black and white
            Imgproc.cvtColor(currentFrameMat, tmpMat, Imgproc.COLOR_RGB2GRAY);

            tmpMat.copyTo(currentFrameMat);

            if (!previousFrameMat.size().equals(currentFrameMat.size()) ||
                    previousFrameMat.type() != currentFrameMat.type())
                currentFrameMat.copyTo(previousFrameMat);

            compute_diff(currentFrameMat.getNativeObjAddr(),
                    previousFrameMat.getNativeObjAddr(),
                    currentFrameMat.getNativeObjAddr(),
                    2, 2);

            // Store for next frame
            tmpMat.copyTo(previousFrameMat);

            if (liveCheckbox.isChecked()) { // display the frame around the changes
                Mat matLabels = new Mat(1, 1, CvType.CV_8UC1);
                Mat matStats = new Mat(1, 1, CvType.CV_8UC1);
                Mat matCentroids = new Mat(1, 1, CvType.CV_8UC1);

                int numComponents = find_components(currentFrameMat.getNativeObjAddr(),
                        matLabels.getNativeObjAddr(),
                        matStats.getNativeObjAddr(),
                        matCentroids.getNativeObjAddr());
                Log.d("num_comp", String.valueOf(numComponents));

                outputOnUILabel("DIFF components: " + Integer.toString(numComponents));

                matLabels.release();
                matStats.release();
                matCentroids.release();
            }

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
     * Returns the value of an UI element that includes a given point
     */
    public String readElementValueAtDiff(Mat currentFrame, Point point) {
        String output = "";
        int index = targetForm.matchElFromPoint(point);
        UIElement tmp = targetForm.getElement(index);
        output = concatTextBlocks(detect_text(currentFrame.submat(tmp.box)));
        return output;
    }

    /**
     * Returns the value of an UI element that includes a given rectangle
     */
    public String readElementValueAtDiff(Mat currentFrame, Rect box) {
        String output = "";
        int index = targetForm.matchElFromRect(box);
        UIElement tmp = targetForm.getElement(index);
        output = concatTextBlocks(detect_text(currentFrame.submat(tmp.box)));
        return output;
    }

    public void updateUIElement(int i, String text) {
        // check if the element is active, TODO: does time since active help?
        if (targetForm.getElement(i).id.equals(targetForm.activEl)) {
            targetForm.getElement(i).currentVal = text;
            targetForm.getElement(i).lastUpdated = System.currentTimeMillis();
        }
        else {
            // raise an alarm flag
            Log.d(TAG, "***Attack*** Take things seriously :-) " +
                    "Trying to modify element with ID: " + targetForm.getElement(i).id
                    + ", while active is: " + targetForm.activEl);
        }
    }

    /**
     * This method returns a SparseArry of TextBlocs found in a frame, or a subframe if box is not null
     */
    private SparseArray<TextBlock> detect_text(Mat matFrame) {
        //convert Mat to Bitmap
        Bitmap bmp = Bitmap.createBitmap(matFrame.cols(), matFrame.rows(),
                Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(matFrame, bmp);
        //convert Bitmap to Frame. TODO: Optimize conversions if we need more FPS
        Frame frame = new Frame.Builder().setBitmap(bmp).build();

        SparseArray<TextBlock> texts = textRecognizer.detect(frame);
        return texts;
    }

    private String concatTextBlocks (SparseArray<TextBlock> texts) {
        String textConcat = "";
        for (int i = 0; i < texts.size(); ++i) {
            TextBlock item = texts.valueAt(i);
            if (item != null && item.getValue() != null) {
                textConcat += item.getValue() + " ";
            }
        }
        return textConcat;
    }

    private void getListOfForms(String url) {
        Log.d("ListOfForms", "trying to get the listOfForms from: " + url);

        JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.POST,
                url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d("ListOfForms", "response: " + response.toString());

                        try {
                            // Parsing json object response
                            JSONArray JSONElements = response.getJSONArray("response");
                            Log.d("ListOfForms", "ArrayList: " + JSONElements.toString());

                            // iterate through all elements
                            for (int i = 0; i < JSONElements.length(); i++) {
                                JSONObject currEl = JSONElements.getJSONObject(i);
                                String formID = currEl.getString("page_title");
                                formID = formID.replaceAll("\\s+","");
                                String formJson = currEl.getString("json");
                                Log.d(TAG, "FormID: " + formID + " -> " + formJson);
                                knownForms.put(formID, formJson);
                            }

                        } catch (JSONException e) {
                            e.printStackTrace();
                            Log.d("ListOfForms", e.getMessage());
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        VolleyLog.d("ListOfForms", "Error: " + error.getMessage());
                        Log.d("ListOfForms", String.valueOf(error.getStackTrace()));
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String>  params = new HashMap<String, String>();
                params.put("Content-Type", "multipart/form-data");
                return params;
            }
        };

        // Adding request to request queue
        queue.add(jsonObjReq);
    }

    public native void compute_diff(long matFirst, long matSecond, long matDiff, long morphSize, long downscaleFactor);
    public native int find_components(long currentFrameMat, long matLabels, long matStats, long matCentroids);
    public native void rotate90(long inputAddr, long outputAddr);
    public native void rotate270(long inputAddr, long outputAddr);
}

