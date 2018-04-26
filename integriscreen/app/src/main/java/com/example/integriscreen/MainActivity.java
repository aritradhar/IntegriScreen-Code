package com.example.integriscreen;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

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

import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

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

    // this is currently for "limited OCR"
    private int h_border_perc = 30;
    private int v_border_perc = 47;
    Point upper_left, lower_right;

    int skin_hue_estimate = 22;
    int color_border_hue = 120;

    // the form created based on specs received from Server
    TargetForm targetForm;

    String urlForm_1920_1080 = "https://tinyurl.com/y8uu2r5t";
    String urlForm_1080_960 = "https://tinyurl.com/y7mwg5e3";

    //    private CameraBridgeViewBase _cameraBridgeViewBase;
    private CustomCameraView _cameraBridgeViewBase;

    // TextRecognizer is the native vision API for text extraction
    TextRecognizer textRecognizer;


    private enum ISState { INITIALIZING,          // Set up global vars, etc.
                                    DETECTING_FRAME,       // Start detecting the green frame
                                    DETECTING_TITLE,       // Start OCR-ing to find the title
                                    LOADING_FORM,          // Load the form based on title
                                    REALIGNING_AFTER_FORM_LOAD,    // Realign once more, this time speficially to the form ratio
                                    VERIFYING_UI,          // Start verifying that the UI is as expected
                                    SUPERVISING_USER_INPUT,  // Accept user's input for as long as everything is OK
                                    SUBMITTING_DATA,       // Keep sending user data until server responds
                                    EVERYTHING_OK,         // Tell the user that everything is OK
                                    DATA_MISSMATCH,        // There was a mismatch on the server. Show the diff to the user.
                                    ERROR_DURING_INPUT };  // We might end up here in case we detect something strange during user input
    ISState currentISState;
    boolean submitDataClicked;


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

        currentOutputSelection = OutputSelection.RAW;

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        // Permissions for Android 6+
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.CAMERA},
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
        submitDataClicked = true;
    }

    public void onClickShowDiff(View view) {
        previousFrameMat.release();
        previousFrameMat = new Mat(1, 1, CvType.CV_8UC4);
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

        realignCheckbox.setChecked(true);
        transitionISSTo(ISState.INITIALIZING);
    }

    // Button callback to handle taking a picture
    public void onClickTakePic(View view) {
        Log.d(TAG, "Take picture button clicled.");
        takePicHighRes();
    }

    // Callback which is called by TargetForm class once the data is ready.
    public void onFormLoaded() {
        Log.d(TAG, "Form loaded!" + targetForm.toString());
        Toast.makeText(getApplicationContext(),
                "Loaded form: " + targetForm.formUrl, Toast.LENGTH_SHORT).show();
//        Toast.makeText(getApplicationContext(),
//                targetForm.toString(), Toast.LENGTH_SHORT).show();
    }

    // Callback when picture is taken
    public void onPicTaken(byte[] data) {
        Log.d(TAG, "onPicTaken callback");

        Bitmap bmpPic = BitmapFactory.decodeByteArray(data, 0, data.length);

        // TODO: following code should be commented
        // Write the image in a file (in jpeg format)
        try {
            Log.d(TAG, "Saving pic");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String fileName = Environment.getExternalStorageDirectory().getPath() +
                    "/opencv_" + sdf.format(new Date()) + ".jpg";
            FileOutputStream fos = new FileOutputStream(fileName);
            fos.write(data);
            fos.close();

        } catch (java.io.IOException e) {
            Log.e("PictureDemo", "Exception in photoCallback", e);
        }
    }


    private boolean validateAndPlotForm(Mat currentFrameMat, TargetForm form) {
        boolean allElementsAsExpected = true;

        for(int i = 0; i < form.allElements.size(); ++i) {
            UIElement element = form.allElements.get(i);

            // TODO: determine the area where to draw this.
            int frame_h = currentFrameMat.height();
//            int frame_w = currentFrameMat.width();
            // TODO: it could happen in some cases that this gives us too large width!
            int frame_w = (int)Math.round(frame_h * form.ratio_w / (double)form.ratio_h);

            long x = Math.round(element.box.x * frame_w / (double)form.resolution);
            long y = Math.round(element.box.y * frame_h / (double)form.resolution);
            long width = Math.round(element.box.width * frame_w / (double)form.resolution);
            long height = Math.round(element.box.height * frame_h / (double)form.resolution);

            // --- Add offsets ---
            long offset = 8; // Offset to ensure that OCR does not fail due to tight limits on rectangles
            Point P1 = new Point(
                    Math.min(Math.max(x - offset, 0), currentFrameMat.width() - 1),   // make sure that it is between 0 and currentFrameMat.height() and currentFrameMat.width()
                    Math.min(Math.max(y - offset, 0), currentFrameMat.height() - 1));
            Point P2 = new Point(
                    Math.min(x + width + offset, currentFrameMat.width()),   // prevent overflows
                    Math.min(y + height + offset, currentFrameMat.height()));

            String detected = extractAndDisplayTextFromFrame(currentFrameMat.submat(new Rect(P1, P2)));

            Scalar rectangle_color;
            if (detected.equals(element.defaultVal)) {
                rectangle_color = new Scalar(255, 255, 0);
            } else {
                rectangle_color = new Scalar( 255, 0, 0);
                // Output the text on the UI elements
                int textHeight = (int) Imgproc.getTextSize(element.defaultVal, Core.FONT_HERSHEY_SIMPLEX, 1.3, 1, new int[1]).height;
                Imgproc.putText(currentFrameMat, element.defaultVal, new Point(x, y + textHeight + 20),
                        Core.FONT_HERSHEY_SIMPLEX, 1.3, new Scalar(255, 0, 0));

                allElementsAsExpected = false;
            }

            // Plot the borders of the UI elements
            Imgproc.rectangle(currentFrameMat, P1, P2, rectangle_color, 4);
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

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String currentDateandTime = sdf.format(new Date());

        String fileName = Environment.getExternalStorageDirectory().getPath() +
                "/opencv_" + currentDateandTime + ".jpg";
        Log.d(TAG, "Picture saved in: " + fileName);

        _cameraBridgeViewBase.takePicture(fileName, this);


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

    public boolean loadFormBasedOnName(Mat currentFrameMat) {
        Rect formTitleBox = new Rect(0, 0, currentFrameMat.width() / 2, currentFrameMat.height() / 8); // about 15%

        Imgproc.rectangle(currentFrameMat, formTitleBox.tl(), formTitleBox.br(), new Scalar(255, 0, 0), 4);

        // TODO: we should probably implement this by loading a list of forms from some static address
        // At the moment we are strongly and implicitly!!! hardcoding the values "string -> URL"
        String formToLoad = concatTextBlocks(detect_text(currentFrameMat.submat(formTitleBox)));


        formToLoad = formToLoad.replaceAll("\\s+","");
        if (formToLoad.equals("ComposeEmail1920x1080")) {
            targetForm = new TargetForm(getApplicationContext(), urlForm_1920_1080, this);
        } else if (formToLoad.equals("ComposeEmail1080x960")) {
            targetForm = new TargetForm(getApplicationContext(), urlForm_1080_960, this);
        } else
            return false;

        outputOnToast("Loading form: " + formToLoad);
        return true;
    }

    boolean shouldDetectTransformation(ISState currentISState) {
        if (liveCheckbox.isChecked())
            return true;

        List<ISState> dontDetectTransformation = Arrays.asList(
                ISState.VERIFYING_UI,
                ISState.SUPERVISING_USER_INPUT,
                ISState.SUBMITTING_DATA);

        if (dontDetectTransformation.contains(currentISState))
            return false;

        return true;
    }


    void transitionISSTo(ISState newState)
    {
        currentISState = newState;
        // outputOnToast("Entering State: " + newState.name());
        outputOnUILabel("Current State: " + newState.name());
    }

    // This function runs a state machine, which will in each frame transition to the next state if specific conditions are met.
    public Mat executeISStateMachine(Mat currentFrameMat) {
        long mid_delim = currentFrameMat.width() / 2; // By default, take a half of the screen size

        if (currentISState == ISState.INITIALIZING) {
            // Clean previous data
            previousFrameMat.release();
            previousFrameMat = new Mat(1, 1, CvType.CV_8UC1);
            previousFrameMat2.release();
            previousFrameMat2 = new Mat(1, 1, CvType.CV_8UC1);

            submitDataClicked = false;

            transitionISSTo(ISState.DETECTING_FRAME);
            outputOnUILabel("Make the green frame visible in the top part, then click Realign.");
        }

        if (targetForm != null && targetForm.isLoaded) { // If form is loaded, start realinging to its shape, recompute the params
            mid_delim = Math.round((double) currentFrameMat.height() * targetForm.ratio_h / targetForm.ratio_w);
            if (currentISState == ISState.LOADING_FORM)
                transitionISSTo(ISState.REALIGNING_AFTER_FORM_LOAD);
        }

        // Compute the points that define the division line
        Point mid_right = new Point(mid_delim, 0);
        Point mid_left = new Point(mid_delim, currentFrameMat.height());

        // line(currentFrameMat, mid_right, mid_left, new Scalar(255, 0, 0), 8);

        // ==== 1) Handle the upper part of the screen:
        // -- Rotate by 90
        // -- Detect the transformation
        // -- Realign
        // -- Validate the form
        // -- Rotate back

        Rect screenBox = new Rect(new Point(0, 0), mid_left);
        Mat screenPart = currentFrameMat.submat(screenBox);

        Mat rotatedScreenPart = new Mat(1, 1, CvType.CV_8UC1);
        rotate90(screenPart.getNativeObjAddr(), rotatedScreenPart.getNativeObjAddr());

        if (shouldDetectTransformation(currentISState)) { // during "verifying UI", we need to have a still screen
            color_detector(rotatedScreenPart.clone().getNativeObjAddr(), color_border_hue / 2, 1); // 0 - None; 1 - rectangle; 2 - circle

            if (currentISState == ISState.REALIGNING_AFTER_FORM_LOAD) {
                // TODO (Enis):  We should stop refocusing here if we can.
                _cameraBridgeViewBase.stopRefocusing();
                transitionISSTo(ISState.VERIFYING_UI);

            }
        }

        if (realignCheckbox.isChecked()) {
            realign_perspective(rotatedScreenPart.getNativeObjAddr());
        }

        if (currentISState == ISState.DETECTING_FRAME) {
            if (loadFormBasedOnName(rotatedScreenPart)) { // It is important that this function only returns true if such a form exists!
                transitionISSTo(ISState.LOADING_FORM);
            }
        }

        if (currentISState == ISState.VERIFYING_UI) {
            if (validateAndPlotForm(rotatedScreenPart, targetForm) || limitAreaCheckbox.isChecked()) {
                submitDataClicked = false;
                transitionISSTo(ISState.SUPERVISING_USER_INPUT);
            }

        } else if (currentISState == ISState.SUPERVISING_USER_INPUT) {
            // TODO continue(ivo): something similar to diff should start happening here!

            // Convert to black and white
            Mat rotatedScreenPartBW = new Mat(rotatedScreenPart.size(), CvType.CV_8UC1);
            Imgproc.cvtColor(rotatedScreenPart, rotatedScreenPartBW, Imgproc.COLOR_RGBA2GRAY);

            // Since rotatedScreenPartBW will get changed, store the current purely black and white version now for later.
            rotatedScreenPartBW.copyTo(tmpMat);

            // If we don't have a stored previous frame, just use the latest one
            if (!previousFrameMat.size().equals(rotatedScreenPartBW.size()) ||
                   previousFrameMat.type() != rotatedScreenPartBW.type()) {
                rotatedScreenPartBW.copyTo(previousFrameMat);
            }

            compute_diff(rotatedScreenPartBW.getNativeObjAddr(),
                    previousFrameMat.getNativeObjAddr(),
                    rotatedScreenPartBW.getNativeObjAddr(),
                    1);

            // Store for the next frame
            tmpMat.copyTo(previousFrameMat);



            // This is where we start computing the components
            Mat matLabels = new Mat(1, 1, CvType.CV_8UC1);
            Mat matStats = new Mat(1, 1, CvType.CV_8UC1);
            Mat matCentroids = new Mat(1, 1, CvType.CV_8UC1);

            int numComponents = find_components(rotatedScreenPartBW.getNativeObjAddr(),
                    matLabels.getNativeObjAddr(),
                    matStats.getNativeObjAddr(),
                    matCentroids.getNativeObjAddr());
            Log.d("num_comp", String.valueOf(numComponents));

            outputOnUILabel("DIFF components: " + Integer.toString(numComponents));


            // Convert back to RGBA to be shown on the phone
            Imgproc.cvtColor(rotatedScreenPartBW, rotatedScreenPart, Imgproc.COLOR_GRAY2RGBA);

            rotatedScreenPartBW.release();
            matLabels.release();
            matStats.release();
            matCentroids.release();

            if (submitDataClicked) {
                transitionISSTo(ISState.SUBMITTING_DATA);
            }
        } else if (currentISState == ISState.SUBMITTING_DATA) {
            // TODO(enis,daniele): this is where we attempt to submit data to the server
            outputOnUILabel("Submitting data to the server (TODO)...");
        } else {
            extractAndDisplayTextFromFrame(rotatedScreenPart);
        }

        rotate270(rotatedScreenPart.getNativeObjAddr(), screenPart.getNativeObjAddr());
        rotatedScreenPart.release();



        // ===== 2) Handle the lower part
        // -- apply detect_color(human_skin)
        // -- apply diff on color of human_skin
        // TODO: -- detect if changes are happening
        int activityDetected = 0;

        Rect handsBox = new Rect(mid_right, new Point(currentFrameMat.width(), currentFrameMat.height()));
        Mat handsPart = currentFrameMat.submat(handsBox);

        color_detector(handsPart.getNativeObjAddr(),skin_hue_estimate / 2, 0);

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
                2);

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
            activityDetected = 1;

        matLabels.release();
        matStats.release();
        matCentroids.release();

        // Store for the next frame
        tmpMat2.copyTo(previousFrameMat2);


        // --------------------------------------

        // Convert back to 4 channel colors
        Imgproc.cvtColor(handsPart, handsPart, Imgproc.COLOR_GRAY2RGBA);



        // Combine the two parts
        screenPart.copyTo(currentFrameMat.submat(screenBox));
        // TODO PERF: I shouldn't be recreating and then releasing, but re-using Mats
        screenPart.release();

        handsPart.copyTo(currentFrameMat.submat(handsBox));
        handsPart.release();

        // Draw the separating line
        line(currentFrameMat, mid_left, mid_right, new Scalar(255, 255 * activityDetected, 0), 3);

        return currentFrameMat;
    }






    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat currentFrameMat = inputFrame.rgba();
//        Log.d(TAG, "Frame size: " + currentFrameMat.rows() + "x" + currentFrameMat.cols());

        if (currentOutputSelection == OutputSelection.INTEGRISCREEN) {
            return executeISStateMachine(currentFrameMat);
        }

        if (currentOutputSelection == OutputSelection.DETECT_TRANSFORMATION) {
            int hueCenter = huePicker.getProgress() / 2; // get progress value from the progress bar, divide by 2 since this is what OpenCV expects
            int detection_option = detectPicker.getProgress();
            color_detector(currentFrameMat.getNativeObjAddr(), hueCenter, detection_option); // 0 - None; 1 - rectangle; 2 - circle

            return currentFrameMat;
        }

        if (realignCheckbox.isChecked()) {
            if (liveCheckbox.isChecked() && currentOutputSelection != OutputSelection.DIFF) { // Only continuiously realign if live is turned on?
                int hueCenter = color_border_hue / 2;
                color_detector(currentFrameMat.clone().getNativeObjAddr(), hueCenter, 1); // 0 - None; 1 - rectangle; 2 - circle
            }

            realign_perspective(currentFrameMat.getNativeObjAddr());
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

            if (previousFrameMat.width() == 1)
                currentFrameMat.copyTo(previousFrameMat);

            compute_diff(currentFrameMat.getNativeObjAddr(),
                    previousFrameMat.getNativeObjAddr(),
                    currentFrameMat.getNativeObjAddr(),
                    1);

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

    public native void compute_diff(long matFirst, long matSecond, long matDiff, long morhpSize);
    public native int find_components(long currentFrameMat, long matLabels, long matStats, long matCentroids);
    public native void color_detector(long matAddrRGB, long hueCenter, long detection_option);
    public native void realign_perspective(long inputAddr);
    public native void rotate90(long inputAddr, long outputAddr);
    public native void rotate270(long inputAddr, long outputAddr);
}

