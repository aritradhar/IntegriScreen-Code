package com.example.integriscreen;

import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class PerspectiveRealigner {
    private Mat lambda;
    public int hueCenter;

    public PerspectiveRealigner(int colorHueToDetect) {
        lambda = new Mat(1, 1, CvType.CV_8UC1);
        hueCenter = colorHueToDetect;
    }

    public void detectFrameAndComputeTransformation(Mat currentFrameMat, long outWidth, long outHeight) {
        Mat tmpClone = currentFrameMat.clone();
        Log.d("lambda", lambda.toString());
        color_detector(tmpClone.getNativeObjAddr(), outWidth, outHeight, hueCenter, 1, lambda.getNativeObjAddr());
        Log.d("lambda", lambda.toString());
        tmpClone.release();
    }

    public void realignImage(Mat inputMat, Mat outputMat) {
        Mat tmpClone = inputMat.clone();
        Imgproc.warpPerspective(inputMat, outputMat, lambda, outputMat.size());
        tmpClone.release();
    }

    public void realignImage(Mat inputMat) {
        Mat outputMat = inputMat.clone();
        realignImage(inputMat, outputMat);
        outputMat.copyTo(inputMat);
        outputMat.release();
    }

    public native void color_detector(long matAddrRGB, long width, long height, long hueCenter, long shouldUpdate, long lambda);
}
