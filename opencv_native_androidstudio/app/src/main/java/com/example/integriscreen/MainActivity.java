package com.example.integriscreen;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import static org.opencv.imgproc.Imgproc.blur;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "OCVSample::Activity";

    private enum OutputSelection { SEL_RAW, SEL_CANNY, SEL_DIFF, SEL_GREEN };
    private OutputSelection currentOutputSelection;

    private CameraBridgeViewBase _cameraBridgeViewBase;

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


        currentOutputSelection = OutputSelection.SEL_CANNY;

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
        currentOutputSelection = OutputSelection.SEL_CANNY;
    }

    public void onClickShowDiff(View view) {
        currentOutputSelection = OutputSelection.SEL_DIFF;
    }

    public void onClickShowGreen(View view) {
        currentOutputSelection = OutputSelection.SEL_GREEN;
    }

    public void onClickShowRaw(View view) {
        currentOutputSelection = OutputSelection.SEL_RAW;
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
    }

    public void onCameraViewStopped() {
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


    // TODO: this still leaks memory from time to time. We need to fix it at some point.
    private Mat matGray;
    private Mat previousMatGray;
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        if (currentOutputSelection == OutputSelection.SEL_CANNY) {
            Mat matOut = inputFrame.gray();
            return computeCanny(matOut);
        }

        if (currentOutputSelection == OutputSelection.SEL_DIFF) {
            matGray = inputFrame.gray();

            Mat outputMat = new Mat(matGray.size(), CvType.CV_8UC1);

            if (previousMatGray == null)
                previousMatGray = matGray;

            compute_diff(matGray.getNativeObjAddr(), previousMatGray.getNativeObjAddr(), outputMat.getNativeObjAddr());
            previousMatGray = matGray;
            return outputMat;
        }

        if (currentOutputSelection == OutputSelection.SEL_GREEN) {
            Mat currentFrame = inputFrame.rgba();
            green_detector(currentFrame.getNativeObjAddr());
            return currentFrame;
        }

        // currentOutputSelection == OutputSelection.SEL_RAW
        return inputFrame.rgba();
    }

    public native void salt(long matAddrGray, int nbrElem);
    public native void compute_diff(long matFirst, long matSecond, long matDiff);
    public native void green_detector(long matAddrRGB);
    public native void apply_median(long matAddrGray, int filterSize);
    public native void realign_perspective(long inputAddr, long outputAddr);
}

