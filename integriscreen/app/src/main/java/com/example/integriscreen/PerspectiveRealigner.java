package com.example.integriscreen;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import java.util.Vector;

public class PerspectiveRealigner {
    private Mat lambda;
    private int hueCenter;
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

    private boolean allZero(Vector<Point> detectedCorners) {
        for(Point P: detectedCorners)
            if (P.x != 0 || P.y != 0)
                return false;
        return true;
    }

    private boolean couldBeRectangle(Vector<Point> detectedCorners) {
        if (detectedCorners.size() != 4 || allZero(detectedCorners)) return false;

        // Check if any two corners are closer than minDist appart
        int minDist = 200;
        for(int i = 0; i < 4; ++i)
            for(int j = i + 1; j < 4; ++j) {
                Point P1 = detectedCorners.get(i);
                Point P2 = detectedCorners.get(j);
                if (Math.pow(P1.x - P2.x, 2) + Math.pow(P1.y - P2.y, 2) < Math.pow(minDist, 2))
                    return false;
            }
        return true;
    }


    void detectFrameAndComputeTransformation(Mat currentFrameMat, int _hueCenter, boolean shouldReturnColorMask) {
        Vector<Point> detectedCorners = detectRectangleCorners(currentFrameMat, _hueCenter, shouldReturnColorMask);

        if (lambda == null)
            lambda = new Mat(1, 1, CvType.CV_8UC1);

        Vector<Point> outputCorners = getOutputCorners(currentFrameMat.size());

        if (!couldBeRectangle(detectedCorners)) {
            // If nothing was found, ensure that transformation leaves the frame unchanged
            detectedCorners = outputCorners;
        }

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
