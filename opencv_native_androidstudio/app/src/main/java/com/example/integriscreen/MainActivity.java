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
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
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
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.w3c.dom.Text;

import static org.opencv.imgproc.Imgproc.blur;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "OCVSample::Activity";

    private enum OutputSelection { RAW, CANNY, DIFF, COLOR, REALIGN };
    private OutputSelection currentOutputSelection;
    private SeekBar huePicker;
    private TextView colorLabel;
    private SeekBar detectPicker;
    private CheckBox realignCheckBox;

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        currentOutputSelection = OutputSelection.COLOR;

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        // Permissions for Android 6+
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.CAMERA},
                1);

        _cameraBridgeViewBase = (CameraBridgeViewBase) findViewById(R.id.main_surface);
        // Change camera resolution:
        // _cameraBridgeViewBase.setMaxFrameSize(1024, 768);
        _cameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        _cameraBridgeViewBase.setCvCameraViewListener(this);


        // Deal with the UI element bindings
        colorLabel = (TextView)findViewById(R.id.colorLabel);
        detectPicker = (SeekBar)findViewById(R.id.detect_method);
        realignCheckBox = (CheckBox)findViewById(R.id.realichCheckBox);
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
        currentOutputSelection = OutputSelection.COLOR;
    }
    public void onClickShowRaw(View view) {
        currentOutputSelection = OutputSelection.RAW;
    }
    public void onClickDetectText(View view) { currentOutputSelection = OutputSelection.REALIGN; }

    public void onDestroy() {
        super.onDestroy();
        disableCamera();
    }

    public void disableCamera() {
        if (_cameraBridgeViewBase != null)
            _cameraBridgeViewBase.disableView();
    }

    private Mat previousMatGray;
    private Mat outputMat;
    public void onCameraViewStarted(int width, int height) {
        outputMat = new Mat(1, 1, CvType.CV_8UC4);
        previousMatGray = new Mat(1, 1, CvType.CV_8UC4);
    }

    public void onCameraViewStopped() {
        outputMat.release();
        previousMatGray.release();
    }

    // based on https://docs.opencv.org/2.4/doc/tutorials/imgproc/imgtrans/canny_detector/canny_detector.html
    // This is using Java-based openCV
    public Mat computeCanny(Mat src_gray) {
        double lowThreshold = 30;   // TODO: this threshold could be optimized
        double ratio = 2;
        int kernel_size = 3;

        Mat src_blurred = new Mat(3, 3, CvType.CV_8UC4);

        /// Reduce noise with a kernel 3x3
        blur( src_gray, src_blurred, new Size(3,3) );

        /// Canny detector
        Imgproc.Canny( src_blurred, src_gray, lowThreshold, lowThreshold*ratio, kernel_size, true );

        // This copies using the "detected_edges" as a mask
        // src_gray.copyTo( dst, detected_edges);


        src_blurred.release();

        return src_gray;
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        if (currentOutputSelection == OutputSelection.CANNY) {
            Mat matOut = inputFrame.gray();
            return computeCanny(matOut);
        }

        if (currentOutputSelection == OutputSelection.DIFF) {
            Mat matGray = inputFrame.gray();

            if (previousMatGray.width() == 1)
                matGray.copyTo(previousMatGray);

            Mat matLabels = new Mat(1, 1, CvType.CV_8UC1);
            Mat matStats = new Mat(1, 1, CvType.CV_8UC1);
            Mat matCentroids = new Mat(1, 1, CvType.CV_8UC1);

            int numComponents = compute_diff(matGray.getNativeObjAddr(),
                                             previousMatGray.getNativeObjAddr(),
                                             outputMat.getNativeObjAddr(),
                                            matLabels.getNativeObjAddr(),
                                            matStats.getNativeObjAddr(),
                                            matCentroids.getNativeObjAddr());
            // Store for next frame
            inputFrame.gray().copyTo(previousMatGray);

            matLabels.release();
            matStats.release();
            matCentroids.release();

            Log.d("num_comp", String.valueOf(numComponents));

            // This would show only the parts of the inputFrame that are actually changing
            // inputFrame.rgba().copyTo(outputMat, outputMat);
            return outputMat;
        }

        if (currentOutputSelection == OutputSelection.COLOR) {
            Mat currentFrameMat = inputFrame.rgba();
            int hueCenter = huePicker.getProgress() / 2; // get progress value from the progress bar, divide by 2 since this is what OpenCV expects
            int detection_option = detectPicker.getProgress();
            color_detector(currentFrameMat.getNativeObjAddr(), hueCenter, detection_option); // 0 - None; 1 - rectangle; 2 - circle

            if (realignCheckBox.isChecked())
                realign_perspective(inputFrame.rgba().getNativeObjAddr());

            return currentFrameMat;
        }

        if (currentOutputSelection == OutputSelection.REALIGN) {
            Mat currentFrameMat = inputFrame.rgba();
            realign_perspective(currentFrameMat.getNativeObjAddr());

            // extract texts from a frame and store in a SparseArray
            SparseArray<TextBlock> texts = detect_text(currentFrameMat);

            Log.d("TextDetected", texts.size()+" words");
            for (int i = 0; i < texts.size(); ++i) {
                TextBlock item = texts.valueAt(i);
                if (item != null && item.getValue() != null) {
                    Log.d("TextDetected", item.getValue());
                }
            }

            return currentFrameMat;
        }

        // currentOutputSelection == OutputSelection.RAW
        return inputFrame.rgba();
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

    public native int compute_diff(long matFirst, long matSecond, long matDiff, long matLabels, long matStats, long matCentroids);
    public native void color_detector(long matAddrRGB, long hueCenter, long detection_option);
    public native void realign_perspective(long inputAddr);
}
