package com.example.integriscreen;

import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public class PerspectiveRealigner {
    private Mat lambda;
    private int hueCenter;
    private long outWidth, outHeight;
    private int defaultHueCenter = 120;


    static void detectColor(Mat currentFrameMat, Mat outputMat, int hueCenter)
    {
        color_detector(currentFrameMat.getNativeObjAddr(), outputMat.getNativeObjAddr(), hueCenter);
    }

    void detectFrameAndComputeTransformation(Mat currentFrameMat, int _hueCenter, long _outWidth, long _outHeight, boolean shouldReturnColorMask) {
        outWidth = _outWidth;
        outHeight = _outHeight;
        hueCenter = _hueCenter;

        if (lambda == null)
            lambda = new Mat(1, 1, CvType.CV_8UC1);

        Mat colorMask = new Mat(1, 1, CvType.CV_8SC1);
        detectColor(currentFrameMat, colorMask, hueCenter);
        compute_transformation(colorMask.getNativeObjAddr(), outWidth, outHeight, lambda.getNativeObjAddr());

        if (shouldReturnColorMask)
            colorMask.copyTo(currentFrameMat);
        colorMask.release();
    }

    void realignImage(Mat inputMat, Mat outputMat) {
        if (lambda == null)
            detectFrameAndComputeTransformation(inputMat, defaultHueCenter, outputMat.width(), outputMat.height());

        Imgproc.warpPerspective(inputMat, outputMat, lambda, outputMat.size());
    }

    void realignImage(Mat inputMat) {
        Mat outputMat = inputMat.clone();
        realignImage(inputMat, outputMat);
        outputMat.copyTo(inputMat);
        outputMat.release();
    }


    void detectFrameAndComputeTransformation(Mat currentFrameMat, int hueCenter, long outWidth, long outHeight) {
        detectFrameAndComputeTransformation(currentFrameMat, hueCenter, outWidth, outHeight, false);
    }

    void detectFrameAndComputeTransformation(Mat currentFrameMat, int hueCenter) {
        detectFrameAndComputeTransformation(currentFrameMat, hueCenter, currentFrameMat.width(), currentFrameMat.height(), false);
    }



    public native void compute_transformation(long colorMask, long width, long height, long lambda);
    public static native void color_detector(long matInput, long matOutput, long hueCenter);
}
