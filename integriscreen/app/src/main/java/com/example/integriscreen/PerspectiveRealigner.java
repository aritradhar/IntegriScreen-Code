package com.example.integriscreen;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import java.util.Vector;

import static com.example.integriscreen.LogManager.logF;

public class PerspectiveRealigner {
    private Mat lambda;
    private int hueCenter;
    private long outWidth, outHeight;
    private int defaultHueCenter = 120;


    static void detectColor(Mat currentFrameMat, Mat outputMat, int hueCenter)
    {
        color_detector(currentFrameMat.getNativeObjAddr(), outputMat.getNativeObjAddr(), hueCenter);
    }

    Vector<Point> detectRectangleCoordinates(Mat currentFrameMat, int hueCenter) {
        return detectRectangleCorners(currentFrameMat, hueCenter, false);
    }

    void update_transformation_lambda(Vector<Point> detectedCorners, Vector<Point> outputCorners) {
        Mat inMat = Converters.vector_Point2f_to_Mat(detectedCorners);
        Mat outMat = Converters.vector_Point2f_to_Mat(outputCorners);

        Imgproc.getPerspectiveTransform(inMat, outMat).copyTo(lambda);

        inMat.release();
        outMat.release();
    }

    Vector<Point> getOutputCorners(Size currentFrameMat) {
        Vector<Point> outputCorners = new Vector<>();

        // This stretches the across the whole frame
        outputCorners.add(new Point(0, 0));
        outputCorners.add(new Point(currentFrameMat.width, 0));
        outputCorners.add(new Point(currentFrameMat.width, currentFrameMat.height));
        outputCorners.add(new Point(0, currentFrameMat.height));

        return outputCorners;
    }

    void detectFrameAndComputeTransformation(Mat currentFrameMat, int _hueCenter, boolean shouldReturnColorMask) {
        Vector<Point> detectedCorners = detectRectangleCorners(currentFrameMat, _hueCenter, shouldReturnColorMask);

        if (lambda == null)
            lambda = new Mat(1, 1, CvType.CV_8UC1);

        Vector<Point> outputCorners = getOutputCorners(currentFrameMat.size());
        update_transformation_lambda(detectedCorners, outputCorners);
    }

    Vector<Point> detectRectangleCorners(Mat currentFrameMat, int _hueCenter, boolean shouldUpdateCurrentFrame) {
        hueCenter = _hueCenter;

        Mat colorMask = new Mat(1, 1, CvType.CV_8SC1);
        detectColor(currentFrameMat, colorMask, hueCenter);

        Mat cornersMat = new Mat(4, 2, CvType.CV_32S);
        find_rectangle_corners(colorMask.getNativeObjAddr(), cornersMat.getNativeObjAddr());

        Vector<Point> detectedCorners = new Vector<>();
        int[] corner = new int[2];
        for(int i = 0; i < 4; ++i) {
            cornersMat.row(i).get(0, 0, corner);
            detectedCorners.add(new Point(corner[0], corner[1]));
        }
        cornersMat.release();


        // Output the detected corners
        // logF("Detected corners", "-------"
        // for(int i = 0; i < (int)detectedCorners.size(); ++i)
        //    logF("Corner " + String.valueOf(i), detectedCorners.get(i).toString());


        if (shouldUpdateCurrentFrame)
            colorMask.copyTo(currentFrameMat);
        colorMask.release();

        return detectedCorners;
    }

    void realignImage(Mat inputMat, Mat outputMat) {
        if (lambda == null)
            detectFrameAndComputeTransformation(inputMat, defaultHueCenter);

        Imgproc.warpPerspective(inputMat, outputMat, lambda, outputMat.size());
    }

    void realignImage(Mat inputMat) {
        Mat outputMat = inputMat.clone();
        realignImage(inputMat, outputMat);
        outputMat.copyTo(inputMat);
        outputMat.release();
    }


    void detectFrameAndComputeTransformation(Mat currentFrameMat, int hueCenter) {
        detectFrameAndComputeTransformation(currentFrameMat, hueCenter, false);
    }



    public native void find_rectangle_corners(long colorMask, long cornersAddr);
    public static native void color_detector(long matInput, long matOutput, long hueCenter);
}
