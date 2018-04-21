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

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.opencv.imgproc.Imgproc.blur;
import static org.opencv.imgproc.Imgproc.line;
import static org.opencv.imgproc.Imgproc.rectangle;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2, OnDataLoadedEventListener {

    private static final String TAG = "MainActivity";

    private enum OutputSelection { RAW, CANNY, DIFF, DETECT_TRANSFORMATION, DETECT_TEXT, DETECT_HANDS};
    private OutputSelection currentOutputSelection;
    private SeekBar huePicker;
    private TextView colorLabel;
    private TextView textOutput;
    private SeekBar detectPicker;
    private CheckBox realignCheckbox;
    private CheckBox limitAreaCheckbox;
    private CheckBox liveOCRCheckbox;

    // this is currently for "limited OCR"
    private int h_border_perc = 15;
    private int v_border_perc = 46;
    Point upper_left, lower_right;

    int skin_hue_estimate = 22;
    int color_border_hue = 120;

    // the form created based on specs received from Server
    TargetForm targetForm;

//    String formURL = "https://drive.google.com/uc?id=10lC35oOTiTI_kdwoKwR6hSsi8c5bOMdc&export=download";
    // String formURL = "https://tinyurl.com/y8uu2r5t"; // 1920x1080
    String formURL = "https://tinyurl.com/y7mwg5e3"; // 1080x960

    //    String formURL = "http://enis.ulqinaku.com/rs/integri/json.php";

    //    private CameraBridgeViewBase _cameraBridgeViewBase;
    private CustomCameraView _cameraBridgeViewBase;

    // TextRecognizer is the native vision API for text extraction
    TextRecognizer textRecognizer;

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


        // Download the target form values
        targetForm = new TargetForm(getApplicationContext(), formURL, this);

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
        liveOCRCheckbox = (CheckBox)findViewById(R.id.liveOCRCheckBox);
        // Un-checking "live" should stop the OCR mode
/*        liveOCRCheckbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ( !((CheckBox)v).isChecked() ) {
                    currentOutputSelection = OutputSelection.RAW;
                }
            }
        });*/


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
        if (liveOCRCheckbox.isChecked())
            currentOutputSelection = OutputSelection.DETECT_TEXT;
        else {
            if (limitAreaCheckbox.isChecked())
                extractAndDisplayTextFromFrame(previousFrameMat.submat(new Rect(upper_left, lower_right)));
            else
                extractAndDisplayTextFromFrame(previousFrameMat);
        }
    }

    public void onClickDetectHands(View view) {
        currentOutputSelection = OutputSelection.DETECT_HANDS;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            huePicker.setProgress(skin_hue_estimate, true);
        }
    }

    // Button callback to handle taking a picture
    public void onClickTakePic(View view) {
        Log.d(TAG, "Take picture button clicled.");
        takePicHighRes();
    }

    // Button callback to handle downloading raw data
    public void onClickDownloadSpec(View view) {
        Log.d(TAG, "Start downloading specs of TargetForm from server...");
        targetForm = new TargetForm(getApplicationContext(), formURL, this);
        Log.d(TAG, targetForm.toString());
    }

    // Callback which is called by TargetForm class once the data is ready.
    public void onFormLoaded() {
        Log.d(TAG, "Form loaded!" + targetForm.toString());
        Toast.makeText(getApplicationContext(),
                targetForm.formUrl, Toast.LENGTH_SHORT).show();
//        Toast.makeText(getApplicationContext(),
//                targetForm.toString(), Toast.LENGTH_SHORT).show();
    }


    void validateAndPlotForm(Mat currentFrameMat, TargetForm form) {

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
            long offset = 5; // Offset to ensure that OCR does not fail due to tight limits on rectangles
            Point P1 = new Point(
                    Math.max(x - offset, 0),    // make sure its not negative
                    Math.max(y - offset, 0));
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
            }

            // Plot the borders of the UI elements
            Imgproc.rectangle(currentFrameMat, P1, P2, rectangle_color, 4);
        }
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
        outputOnUILabel(concatenatedText);

        return concatenatedText;
    }

    private void takePicHighRes() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String currentDateandTime = sdf.format(new Date());

        String fileName = Environment.getExternalStorageDirectory().getPath() +
                "/opencv_" + currentDateandTime + ".jpg";
        Log.d(TAG, "Picture saved in: " + fileName);
//        List<Camera.Size> res = _cameraBridgeViewBase.getResolutionList();
//        for (int i=0; i<res.size(); i++) {
//            Camera.Size r = res.get(i);
//            Log.d(TAG, "Picture resolution #" + i + ": " + r.height + "x" + r.width);
//        }
//        Camera.Size tmpR = res.get(0);
//        tmpR.width = 3264;
//        tmpR.height = 1836;
//        _cameraBridgeViewBase.setResolution(tmpR);

        _cameraBridgeViewBase.takePicture(fileName);


    }
    
    public void onDestroy() {
        super.onDestroy();
        disableCamera();
    }

    public void disableCamera() {
        if (_cameraBridgeViewBase != null)
            _cameraBridgeViewBase.disableView();
    }

    private Mat previousFrameMat;
    private Mat outputMat;
    private Mat tmpMat;
    public void onCameraViewStarted(int width, int height) {
        outputMat = new Mat(1, 1, CvType.CV_8UC4);
        previousFrameMat = new Mat(1, 1, CvType.CV_8UC4);
        tmpMat = new Mat(1, 1, CvType.CV_8UC1);
    }

    public void onCameraViewStopped() {
        outputMat.release();
        previousFrameMat.release();
        tmpMat.release();
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

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat currentFrameMat = inputFrame.rgba();
//        Log.d(TAG, "Frame size: " + currentFrameMat.rows() + "x" + currentFrameMat.cols());

        if (currentOutputSelection == OutputSelection.DETECT_HANDS) {
            // Use the whole height, take the proper amount of width based on the form ratio
            long mid_delim = Math.round((double)currentFrameMat.height() * targetForm.ratio_h / targetForm.ratio_w);

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

            if (realignCheckbox.isChecked()) {
                color_detector(rotatedScreenPart.clone().getNativeObjAddr(), color_border_hue / 2, 1); // 0 - None; 1 - rectangle; 2 - circle
                realign_perspective(rotatedScreenPart.getNativeObjAddr());
            }

            // If liveOCR is true, I run OCR on the whole upper part of the screen
            if (liveOCRCheckbox.isChecked()) {
                validateAndPlotForm(rotatedScreenPart, targetForm);
                // extractAndDisplayTextFromFrame(rotatedScreenPart);
            }

            // TODO PERF: if we need it, implement a faster 270 degree rotation!
            rotate90(rotatedScreenPart.getNativeObjAddr(), screenPart.getNativeObjAddr());
            rotate90(screenPart.getNativeObjAddr(), rotatedScreenPart.getNativeObjAddr());
            rotate90(rotatedScreenPart.getNativeObjAddr(), screenPart.getNativeObjAddr());

            rotatedScreenPart.release();



            // ===== 2) Handle the lower part
            // -- apply detect_color(human_skin)
            // TODO: -- apply diff to detect human skin
            // TODO: -- detect if changes are happening
            Rect handsBox = new Rect(mid_right, new Point(currentFrameMat.width(), currentFrameMat.height()));
            Mat handsPart = currentFrameMat.submat(handsBox);

            color_detector(handsPart.getNativeObjAddr(), huePicker.getProgress() / 2, 0);
            // Convert back to 4 channel colors
            Imgproc.cvtColor(handsPart, handsPart, Imgproc.COLOR_GRAY2RGBA);



            // Combine the two parts
            screenPart.copyTo(currentFrameMat.submat(screenBox));
            // TODO PERF: I shouldn't be recreating and then releasing, but re-using Mats
            screenPart.release();

            handsPart.copyTo(currentFrameMat.submat(handsBox));
            handsPart.release();

            return currentFrameMat;
        }

        if (currentOutputSelection == OutputSelection.DETECT_TRANSFORMATION) {
            int hueCenter = huePicker.getProgress() / 2; // get progress value from the progress bar, divide by 2 since this is what OpenCV expects
            int detection_option = detectPicker.getProgress();
            color_detector(currentFrameMat.getNativeObjAddr(), hueCenter, detection_option); // 0 - None; 1 - rectangle; 2 - circle

            return currentFrameMat;
        }

        if (realignCheckbox.isChecked()) {
            if (liveOCRCheckbox.isChecked()) { // Only constantly realign if live is turned on?
                int hueCenter = color_border_hue / 2; // get progress value from the progress bar, divide by 2 since this is what OpenCV expects
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
            if (liveOCRCheckbox.isChecked()) {
                if (targetForm != null && targetForm.isLoaded)
                    validateAndPlotForm(currentFrameMat, targetForm);
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
                    currentFrameMat.getNativeObjAddr());

            // Store for next frame
            tmpMat.copyTo(previousFrameMat);

            // TODO: this kind of UI selection does not make too much sense logically, but it will be OK for now.
            if (liveOCRCheckbox.isChecked()) {
                Mat matLabels = new Mat(1, 1, CvType.CV_8UC1);
                Mat matStats = new Mat(1, 1, CvType.CV_8UC1);
                Mat matCentroids = new Mat(1, 1, CvType.CV_8UC1);

                int numComponents = find_components(currentFrameMat.getNativeObjAddr(),
                        matLabels.getNativeObjAddr(),
                        matStats.getNativeObjAddr(),
                        matCentroids.getNativeObjAddr());
                Log.d("num_comp", String.valueOf(numComponents));

                outputOnUILabel(Integer.toString(numComponents));

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

    public native void compute_diff(long matFirst, long matSecond, long matDiff);
    public native int find_components(long currentFrameMat, long matLabels, long matStats, long matCentroids);
    public native void color_detector(long matAddrRGB, long hueCenter, long detection_option);
    public native void realign_perspective(long inputAddr);
    public native void rotate90(long inputAddr, long outputAddr);
}
