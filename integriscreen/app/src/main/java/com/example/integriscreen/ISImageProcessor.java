package com.example.integriscreen;

import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;
import android.util.Pair;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
        applyCanny(inputMat, outputMat, false);
    }

    public static void applyCanny(Mat inputMat, Mat outputMat, boolean shouldStore) {
        Mat inputMatGray = new Mat(1, 1, 1);
        Imgproc.cvtColor(inputMat, inputMatGray, Imgproc.COLOR_RGB2GRAY);

        double lowThreshold = 30;
        double ratio = 2.5;
        int kernel_size = 3;

        Mat inputMatBlurred = new Mat(1, 1, 1);
        /// Reduce noise with a kernel 3x3
        // TODO: play with how large should the blur be!
        blur( inputMatGray, inputMatBlurred, new Size(3,3) );
        if (shouldStore) storePic(inputMatBlurred, "_blurred");

        /// Canny detector
        Imgproc.Canny( inputMatBlurred, inputMatBlurred, lowThreshold, lowThreshold*ratio, kernel_size, true );

        inputMatBlurred.copyTo(outputMat);

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

    private static boolean isBorderRect(Rect R, int width, int height) {
        int limit = 10;
        return (R.tl().x < limit || R.tl().y < limit || R.br().x + limit >= width || R.br().y + limit >= height);
    }

    public static List<Pair<Rect, Integer>> findLargeComponents(Mat frameMatBW, Mat labels, int minArea) {
        // Extract components
        Mat rectComponents = Mat.zeros(new Size(0, 0), 0);
        Mat centComponents = Mat.zeros(new Size(0, 0), 0);
        Imgproc.connectedComponentsWithStats(frameMatBW, labels, rectComponents, centComponents);

        // Collect regions info
        int[] rectangleInfo = new int[5];
        double[] centroidInfo = new double[2];

        Rect largeRectsBoundingBox = new Rect(frameMatBW.width(), frameMatBW.height(), -frameMatBW.width(), -frameMatBW.height());

        String allDiffAreas = "";

        List<Pair<Rect, Integer>> largeRects = new ArrayList<>();
        for(int i = 1; i < rectComponents.rows(); i++) {
            // Extract bounding box
            rectComponents.row(i).get(0, 0, rectangleInfo);
            Rect currentRectBound = new Rect(rectangleInfo[0], rectangleInfo[1], rectangleInfo[2], rectangleInfo[3]);
            allDiffAreas += " | " + String.valueOf(rectangleInfo[4]);

//            Log.d("comps rect", rectangleInfo[0] + " | " +rectangleInfo[1] + " | " +rectangleInfo[2] + " | " +rectangleInfo[3] + " | " +rectangleInfo[4]);
//            Log.d("comps cent", centroidInfo[0] + " | " + centroidInfo[1]);


            int component_area = rectangleInfo[4];
            if (component_area > minArea && !isBorderRect(currentRectBound, frameMatBW.width(), frameMatBW.height())) {
                largeRects.add(new Pair<>(currentRectBound, rectangleInfo[4]));
                largeRectsBoundingBox = update_bounding_box(largeRectsBoundingBox, currentRectBound);
                Imgproc.rectangle(frameMatBW, currentRectBound.tl(), currentRectBound.br(), new Scalar(255, 0, 0), 2);
            }
        }
        Log.i("all areas", "No. of diffs: " + String.valueOf(rectComponents.rows()) + ", areas: " + allDiffAreas);


        // This is just to showcase what I am finding
        labels.convertTo(labels, CV_8UC1, 10.0);

        if (largeRects.size() > 0)
            Imgproc.rectangle(frameMatBW, largeRectsBoundingBox.tl(), largeRectsBoundingBox.br(), new Scalar(255, 0, 0), 4);

        rectComponents.release();
        centComponents.release();

        return largeRects;
    }
    
    // This changes the inputMat and also computes the locations of all changes
    List<Pair<Rect, Integer>> diffFramesAndGetAllChangeLocations(Mat inputMat, Mat outputMat, int morphSize, int downscaleFactor, int minArea) {
        Mat frameDiffBW = new Mat(1, 1, 1);
        diffWithPreviousFrame(inputMat, frameDiffBW, morphSize, downscaleFactor);

        Mat labels = new Mat(1, 1, 1);
        List<Pair<Rect, Integer> > allRects = findLargeComponents(frameDiffBW, labels, minArea);

        // Depending on what I want to show
//        labels.copyTo(outputMat);
        frameDiffBW.copyTo(outputMat);

        frameDiffBW.release();
        labels.release();

        return allRects;
    }

    List<Pair<Rect, Integer>> diffFramesAndGetAllChangeLocations(Mat inputMat, int morphSize, int downscaleFactor, int minArea) {
        Mat outputMat = new Mat(1, 1, 1);
        List<Pair<Rect, Integer>> retList = diffFramesAndGetAllChangeLocations(inputMat, outputMat, morphSize, downscaleFactor, minArea);
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



    public static void storePic(byte[] data, String extension) {
        String fileName = genFileName(extension);

        // Write the image in a file (in jpeg format)
        try {
            Log.d("Saving data[] ", "Saving byte[] to file: " + fileName);

            FileOutputStream fos = new FileOutputStream(fileName);
            fos.write(data);
            fos.close();

        } catch (java.io.IOException e) {
            Log.e("PictureDemo", "Exception in photoCallback", e);
        }
    }

    public static void storePic(Mat mat, String extension) {
        String fileName = genFileName(extension);

        //convert Mat to Bitmap
        Bitmap bmpPic = Bitmap.createBitmap(mat.cols(), mat.rows(),
                Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bmpPic);

        // Write the image in a file (in jpeg format)
        try {
            Log.d("Saving Mat:", "Saving bitmap to file: " + fileName);

            FileOutputStream fos = new FileOutputStream(fileName);
//            fos.write(data);
            bmpPic.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();

        } catch (java.io.IOException e) {
            Log.e("PictureDemo", "Exception in photoCallback", e);
        }
    }

    private static String genFileName(String extension) {
        // check if directory exists
        File dirIS = new File(Environment.getExternalStorageDirectory(), "Integriscreen");
        if(!dirIS.exists()) {
            dirIS.mkdirs();
        }

        // generate filename
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd_HH-mm-ss");
        String fileName = dirIS.getPath() +
                "/IS_" + sdf.format(new Date()) + extension + ".jpg";

        return fileName;
    }





    public static native void apply_diff(long matFirst, long matSecond, long matDiff, long morphSize, long downscaleFactor);
}
