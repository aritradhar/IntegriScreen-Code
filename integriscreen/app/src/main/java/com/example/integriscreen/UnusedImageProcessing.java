package com.example.integriscreen;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

import static org.opencv.core.CvType.CV_64F;
import static org.opencv.core.CvType.CV_8UC1;


public class UnusedImageProcessing {
    // This fill compute some simple background substraction over several frames
    private static List<Mat> previousMats;
    private static int indexOfOldestPositiveMat;
    private static Mat sumMat;

    public static void applyTimeAverage(Mat currentFrameMat, int filterLength) {
        if (previousMats == null) {
            previousMats = new ArrayList<>();
            indexOfOldestPositiveMat = 0;
        }

        if (sumMat == null) {
            sumMat = new Mat(currentFrameMat.size(), CV_64F, new Scalar(0 )); // all black, *double* image
        }

        if (previousMats.size() != 2 * filterLength) {
            for( int i = 0; i < previousMats.size(); ++i)
                previousMats.get(i).release();

            for( ; previousMats.size() < 2 * filterLength; )
                previousMats.add(new Mat(currentFrameMat.size(), currentFrameMat.type(), new Scalar(0)));
        }


        // Add the latest mat
        Imgproc.accumulate(currentFrameMat, sumMat);

        // Move the "oldest positive" to negative by subtracting it twice.
        Mat matToSubtract = new Mat(1, 1, 1);
        previousMats.get(indexOfOldestPositiveMat).convertTo(matToSubtract, CV_64F); // This makes sure that both mats are of same type
        Core.subtract(sumMat, matToSubtract, sumMat);
        Core.subtract(sumMat, matToSubtract, sumMat); // subtract 2 times because I want to reduce the background
        matToSubtract.release();

        // The oldest negative Mat should be added one more time to reduce its influence
        int indexOfOldestNegativeMat = (indexOfOldestPositiveMat + filterLength) % (2*filterLength);
        Imgproc.accumulate(previousMats.get(indexOfOldestNegativeMat), sumMat);

        // The currentFrame is moved where the oldest negative used to be and will stay as "positive" for filterLength frames
        currentFrameMat.copyTo(previousMats.get(indexOfOldestNegativeMat));

        // Update the oldest positive
        indexOfOldestPositiveMat = (indexOfOldestPositiveMat + 1) % (2 * filterLength);

        // Compute the abs value
        //    This crashes in openCV 3.1. :/
        // Core.absdiff(sumMat, new Scalar( 0 ), sumMat);

        // Divide the mat by the average
        sumMat.convertTo(currentFrameMat, CV_8UC1,1.0/(2*filterLength)); // back to u8 land, divide by count
    }

    private static boolean isBorderRect(Rect R, int width, int height, int limit) {
        return (R.tl().x < limit || R.tl().y < limit || R.br().x + limit >= width || R.br().y + limit >= height);
    }

    // We don't use this function for now
    private static boolean couldBeCharacterChange(Rect R, int rectArea, int frameWidth, int frameHeight) {
        int minArea = 40;
        int maxArea = 1000;
        double minRectRatio = 0.1;

        if (rectArea < minArea || rectArea > maxArea) return false;
        // double areaRatio = (double)rectArea / boundingArea(R); // areaRatio = 1 if it's a perfect rectangle

        double whRatio = (double)R.width / R.height;
        // This checks if the shape is far from a square-like rectangle
        if (whRatio < minRectRatio || 1/whRatio < minRectRatio) return false;

        int borderOffset = 10; // Any component that is closer than offset to the border will be ignored
        if (isBorderRect(R, frameWidth, frameHeight, borderOffset)) return false;

        return true;
    }


}
