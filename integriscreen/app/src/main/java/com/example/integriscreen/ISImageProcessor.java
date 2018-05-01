package com.example.integriscreen;

import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

import static org.opencv.core.Core.flip;
import static org.opencv.core.Core.transpose;
import static org.opencv.core.CvType.CV_8UC1;
import static org.opencv.imgproc.Imgproc.blur;


public class ISImageProcessor {

    Mat previousFrameMat;

    public ISImageProcessor() {
    }

    // based on https://docs.opencv.org/2.4/doc/tutorials/imgproc/imgtrans/canny_detector/canny_detector.html
    // This is using Java-based openCV
    public static void applyCanny(Mat inputMat, Mat outputMat) {
        Mat inputMatGray = new Mat(1, 1, 1);
        Imgproc.cvtColor(inputMat, inputMatGray, Imgproc.COLOR_RGB2GRAY);

        double lowThreshold = 30;
        double ratio = 2;
        int kernel_size = 3;

        Mat inputMatBlurred = new Mat(3, 3, CvType.CV_8UC4);

        /// Reduce noise with a kernel 3x3
        blur( inputMatGray, inputMatBlurred, new Size(3,3) );

        /// Canny detector
        Imgproc.Canny( inputMatBlurred, outputMat, lowThreshold, lowThreshold*ratio, kernel_size, true );

        // This copies using the "detected_edges" as a mask
        // src_gray.copyTo( dst, detected_edges);

        inputMatGray.release();
        inputMatBlurred.release();
    }

    public static Rect update_bounding_box(Rect A, Rect B)
    {
        // U has been calculated as upper-left and lower-right corner.
        Rect U = new Rect(Math.min(A.x, B.x),
                          Math.min(A.y, B.y),
                          Math.max(A.x + A.width, B.x + B.width),
                          Math.max(A.y + A.height, B.y + B.height));

        // Before returning, convert it to upper-left and width, height
        return new Rect(U.x, U.y, U.width - U.x, U.height - U.y);
    }

    public void diffWithPreviousFrame(Mat inputMat, Mat outputMat, int morphSize, int downscaleFactor) {
        // TODO(ivo): I need to play with parameters for min area, etc.

        // Convert to black and white if needed
        Mat frameMatBW = new Mat(inputMat.size(), CV_8UC1);
        if (inputMat.channels() > 1) {
            Imgproc.cvtColor(inputMat, frameMatBW, Imgproc.COLOR_RGBA2GRAY);
        } else
            inputMat.copyTo(frameMatBW);

        // Since frameMatBW will get changed, store the current purely black and white version now for later.
        Mat tmpMat = frameMatBW.clone();

        if (previousFrameMat == null) previousFrameMat = new Mat(1, 1, 1);

        // If we don't have a stored previous frame, just use the latest one
        if (!previousFrameMat.size().equals(frameMatBW.size()) ||
                previousFrameMat.type() != frameMatBW.type()) {
            frameMatBW.copyTo(previousFrameMat);
        }

        // TODO(ivo): allow specifying diff parameteres from here (threshold, min size, etc.)
        // TODO(ivo): diffing should also probably be a separate class given the "previous frame"
        apply_diff(frameMatBW, previousFrameMat, frameMatBW, morphSize, downscaleFactor);

        // Store for the next frame
        tmpMat.copyTo(previousFrameMat);

        frameMatBW.copyTo(outputMat);

        tmpMat.release();
        frameMatBW.release();
    }

    public static List<Rect> findLargeComponents(Mat frameMatBW, Mat labels, int minArea) {
        // Extract components
        Mat rectComponents = Mat.zeros(new Size(0, 0), 0);
        Mat centComponents = Mat.zeros(new Size(0, 0), 0);
        Imgproc.connectedComponentsWithStats(frameMatBW, labels, rectComponents, centComponents);

        // Collect regions info
        int[] rectangleInfo = new int[5];
        double[] centroidInfo = new double[2];

        Rect largeRectsBoundingBox = new Rect(frameMatBW.width(), frameMatBW.height(), -frameMatBW.width(), -frameMatBW.height());

        List<Rect> allRects = new ArrayList<>();
        for(int i = 1; i < rectComponents.rows(); i++) {
            // Extract bounding box
            rectComponents.row(i).get(0, 0, rectangleInfo);
            Rect currentRectBound = new Rect(rectangleInfo[0], rectangleInfo[1], rectangleInfo[2], rectangleInfo[3]);

            Log.d("comps rect", rectangleInfo[0] + " | " +rectangleInfo[1] + " | " +rectangleInfo[2] + " | " +rectangleInfo[3] + " | " +rectangleInfo[4]);
            Log.d("comps cent", centroidInfo[0] + " | " + centroidInfo[1]);


            int component_area = rectangleInfo[4];
            if (component_area > minArea) {
                allRects.add(currentRectBound);
                largeRectsBoundingBox = update_bounding_box(largeRectsBoundingBox, currentRectBound);
            }
        }

        // This is just to showcase what I am finding
        labels.convertTo(labels, CV_8UC1, 50.0);

        if (allRects.size() > 0)
            Imgproc.rectangle(labels, largeRectsBoundingBox.tl(), largeRectsBoundingBox.br(), new Scalar(255, 0, 0), 2);


        // Convert back to RGBA to be shown on the screen
        Imgproc.cvtColor(labels, labels, Imgproc.COLOR_GRAY2RGBA);

        rectComponents.release();
        centComponents.release();

        return allRects;
    }
    
    // This changes the inputMat and also computes the locations of all changes
    List<Rect> diffFramesAndGetAllChangeLocations(Mat inputMat, Mat outputMat, int morphSize, int downscaleFactor, int minArea) {
        Mat frameMatBW = new Mat(1, 1, 1);
        diffWithPreviousFrame(inputMat, frameMatBW, morphSize, downscaleFactor);

        Mat labels = new Mat(1, 1, 1);
        List<Rect> allRects = findLargeComponents(frameMatBW, labels, minArea);

        // Depending on what I want to show
        labels.copyTo(outputMat);
//        frameMatBW.copyTo(outputMat);

        frameMatBW.release();
        labels.release();

        return allRects;
    }

    List<Rect> diffFramesAndGetAllChangeLocations(Mat inputMat, int morphSize, int downscaleFactor, int minArea) {
        Mat outputMat = new Mat(1, 1, 1);
        List<Rect> retList = diffFramesAndGetAllChangeLocations(inputMat, outputMat, morphSize, downscaleFactor, minArea);
        outputMat.copyTo(inputMat);
        outputMat.release();
        return retList;
    }

    static void rotate90(Mat inputMat, Mat outputMat) {
        Mat transposedMat = new Mat(1, 1, 1);
        transpose(inputMat, transposedMat);
        flip(transposedMat, outputMat, +1);
    }

    static void rotate270(Mat inputMat, Mat outputMat) {
        Mat transposedMat = new Mat(1, 1, 1);
        transpose(inputMat, transposedMat);
        flip(transposedMat, outputMat, 0);
    }

    public static void apply_diff(Mat matFirst, Mat matSecond, Mat matDiff, long morphSize, long downscaleFactor) {
        apply_diff(matFirst.getNativeObjAddr(), matSecond.getNativeObjAddr(), matDiff.getNativeObjAddr(), morphSize, downscaleFactor);
    }

    public static native void apply_diff(long matFirst, long matSecond, long matDiff, long morphSize, long downscaleFactor);
}
