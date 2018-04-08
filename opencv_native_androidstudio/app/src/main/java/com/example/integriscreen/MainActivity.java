package com.example.integriscreen;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
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
import org.opencv.core.CvType;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import static org.opencv.imgproc.Imgproc.blur;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "OCVSample::Activity";

    private enum OutputSelection { RAW, CANNY, DIFF, DETECT_TRANSFORMATION, DETECT_TEXT, COUNT_DIFF};
    private OutputSelection currentOutputSelection;
    private SeekBar huePicker;
    private TextView colorLabel;
    private TextView textOutput;
    private SeekBar detectPicker;
    private CheckBox realignCheckbox;
    private CheckBox limitAreaCheckbox;

    private int h_border_perc = 15;
    private int v_border_perc = 46;
    Point upper_left, lower_right;


    private CameraBridgeViewBase _cameraBridgeViewBase;

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

        currentOutputSelection = OutputSelection.RAW;


        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        // Permissions for Android 6+
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.CAMERA},
                1);

        _cameraBridgeViewBase = (CameraBridgeViewBase) findViewById(R.id.main_surface);
        // Uncomment to set one of the upper bounds on the camera resolution (the other is the preview View size)
        // To hardcode the resolution, find "// calcWidth = 1920;" in CameraBridgeViewBase
        // Ivo's phone: 960x540, 1280x720 (1M), 1440x1080 (1.5M), 1920x1080 (2M)
        // _cameraBridgeViewBase.setMaxFrameSize(1280, 720);
        _cameraBridgeViewBase.setMaxFrameSize(960, 540);
        _cameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        _cameraBridgeViewBase.setCvCameraViewListener(this);
        _cameraBridgeViewBase.enableFpsMeter();

        // Deal with the UI element bindings
        colorLabel = (TextView)findViewById(R.id.colorLabel);
        textOutput = (TextView)findViewById(R.id.textOutput);
        detectPicker = (SeekBar)findViewById(R.id.detect_method);
        realignCheckbox = (CheckBox)findViewById(R.id.realignCheckBox);
        limitAreaCheckbox = (CheckBox)findViewById(R.id.limitAreaCheckBox);
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

    public void onClickShowCanny(View view) {
        currentOutputSelection = OutputSelection.CANNY;
    }
    public void onClickShowDiff(View view) {
        currentOutputSelection = OutputSelection.DIFF;
    }
    public void onClickShowColor(View view) {
        currentOutputSelection = OutputSelection.DETECT_TRANSFORMATION;
    }
    public void onClickShowRaw(View view) {
        currentOutputSelection = OutputSelection.RAW;
    }
    public void onClickDetectText(View view) {
        // currentOutputSelection = OutputSelection.DETECT_TEXT;
        detect_text_from_frame(previousFrameMat);
    }
    
    private void detect_text_from_frame(Mat frameMat)
    {
        // Setup border parameters
        Mat scanArea;
        if (limitAreaCheckbox.isChecked()) {
            scanArea = frameMat.submat(new Rect(upper_left, lower_right));
        } else
            scanArea = frameMat;

        SparseArray<TextBlock> texts = detect_text(scanArea);

        String textConcat = "";
        Log.d("TextDetected", texts.size()+" words");
        for (int i = 0; i < texts.size(); ++i) {
            TextBlock item = texts.valueAt(i);
            if (item != null && item.getValue() != null) {
                Log.d("TextDetected", item.getValue());
                textConcat += item.getValue() + " | ";
            }
        }

        // This is needed since we are not running on the UI thread usually
        final String textToShow = textConcat;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textOutput.setText(textToShow);
            }
        });
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

        // if detect_color -> apply sto treba
        if (currentOutputSelection == OutputSelection.DETECT_TRANSFORMATION) {
            // Mat currentFrameMat = inputFrame.rgba();
            int hueCenter = huePicker.getProgress() / 2; // get progress value from the progress bar, divide by 2 since this is what OpenCV expects
            int detection_option = detectPicker.getProgress();
            color_detector(currentFrameMat.getNativeObjAddr(), hueCenter, detection_option); // 0 - None; 1 - rectangle; 2 - circle

            // return currentFrameMat;
            return currentFrameMat;
        }

        if (realignCheckbox.isChecked())
            realign_perspective(currentFrameMat.getNativeObjAddr());


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
            Imgproc.cvtColor(currentFrameMat, tmpMat, Imgproc.COLOR_RGB2GRAY);

            tmpMat.copyTo(currentFrameMat);

            if (previousFrameMat.width() == 1)
                currentFrameMat.copyTo(previousFrameMat);

            compute_diff(currentFrameMat.getNativeObjAddr(),
                    previousFrameMat.getNativeObjAddr(),
                    currentFrameMat.getNativeObjAddr());

            // Store for next frame
            tmpMat.copyTo(previousFrameMat);

            return currentFrameMat;
        }

        // Final results: detect text, compute the number of changes, etc.

        if (currentOutputSelection == OutputSelection.COUNT_DIFF) {
            Mat matLabels = new Mat(1, 1, CvType.CV_8UC1);
            Mat matStats = new Mat(1, 1, CvType.CV_8UC1);
            Mat matCentroids = new Mat(1, 1, CvType.CV_8UC1);

            int numComponents = find_components(currentFrameMat.getNativeObjAddr(),
                                                matLabels.getNativeObjAddr(),
                                                matStats.getNativeObjAddr(),
                                                matCentroids.getNativeObjAddr());
            Log.d("num_comp", String.valueOf(numComponents));

            // TODO: process components

            matLabels.release();
            matStats.release();
            matCentroids.release();

            return currentFrameMat;
        }

        if (currentOutputSelection == OutputSelection.DETECT_TEXT) {
            detect_text_from_frame(currentFrameMat);
            return currentFrameMat;
        }

        return currentFrameMat;
    }

    private SparseArray<TextBlock> detect_text(Mat matFrame) {
        //convert Mat to Bitmap
        Bitmap bmp = Bitmap.createBitmap(matFrame.cols(), matFrame.rows(),
                Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(matFrame, bmp);
        //convert Bitmap to Frame. TODO: Optimize conversions
        Frame frame = new Frame.Builder().setBitmap(bmp).build();

        SparseArray<TextBlock> texts = textRecognizer.detect(frame);
        return texts;
    }

    public native void compute_diff(long matFirst, long matSecond, long matDiff);
    public native int find_components(long currentFrameMat, long matLabels, long matStats, long matCentroids);
    public native void color_detector(long matAddrRGB, long hueCenter, long detection_option);
    public native void realign_perspective(long inputAddr);
}

