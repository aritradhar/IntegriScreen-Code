package com.example.integriscreen;

import java.io.FileOutputStream;
import java.util.List;

import org.opencv.android.JavaCameraView;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.util.AttributeSet;
import android.util.Log;

/**
 * Created by eulqinaku on 09/04/2018.
 */

public class CustomCameraView extends JavaCameraView implements PictureCallback {

    private static final String TAG = "OCV::CustomCameraView";
    private String mPictureFileName;
    private OnDataLoadedEventListener parentActivity;

    public CustomCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public List<String> getEffectList() {
        return mCamera.getParameters().getSupportedColorEffects();
    }

    public boolean isEffectSupported() {
        return (mCamera.getParameters().getColorEffect() != null);
    }

    public String getEffect() {
        return mCamera.getParameters().getColorEffect();
    }

    public void setEffect(String effect) {
        Camera.Parameters params = mCamera.getParameters();
        params.setColorEffect(effect);
        mCamera.setParameters(params);
    }

    public List<Size> getResolutionList() {
        return mCamera.getParameters().getSupportedPreviewSizes();
    }

    public void setResolution(Size resolution) {
        disconnectCamera();
        mMaxHeight = resolution.height;
        mMaxWidth = resolution.width;
        connectCamera(getWidth(), getHeight());
    }

    public Size getResolution() {
        return mCamera.getParameters().getPreviewSize();
    }

    /**
     * TODO: check if this method is correct
     */
    public void stopRefocusing() {
        mCamera.cancelAutoFocus();
    }

    /**
     * This method sets the size of picture taken within the app.
     * Quality set to 0 takes the best picture.
     */
    public void setPictureSize(int quality) {
        Camera.Parameters params = mCamera.getParameters();
        List<Size> sizes = params.getSupportedPictureSizes();

        for (int i = 0; i < sizes.size(); i++)
            Log.d(TAG, "Supported PicSize - Height: " + sizes.get(i).height + ", Width: " + sizes.get(i).width);

        mCamera.stopPreview(); // Preview should be stopped in order to update camera parameters

        params.setPictureSize(sizes.get(quality).width, sizes.get(quality).height);
        mCamera.setParameters(params);

        Log.d(TAG, "Picture Size (Width x Height px): " + mCamera.getParameters().getPictureSize().width
                + " x " + mCamera.getParameters().getPictureSize().height);
        mCamera.startPreview();
    }

    public void takePicture(final String fileName, OnDataLoadedEventListener parent) {
        Log.i(TAG, "Taking picture");
        parentActivity = parent;
        this.mPictureFileName = fileName;
        // Postview and jpeg are sent in the same buffers if the queue is not empty when performing a capture.
        // Clear up buffers to avoid mCamera.takePicture to be stuck because of a memory issue
        mCamera.setPreviewCallback(null);

        // PictureCallback is implemented by the current class
        mCamera.takePicture(null, null, this);
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        Log.i(TAG, "Saving a bitmap to file");
        // The camera preview was automatically stopped. Start it again.
        mCamera.startPreview();
        mCamera.setPreviewCallback(this);

        parentActivity.onPicTaken(data);

        // Write the image in a file (in jpeg format)
//        try {
//            FileOutputStream fos = new FileOutputStream(mPictureFileName);
//
//            fos.write(data);
//            fos.close();
//
//        } catch (java.io.IOException e) {
//            Log.e("PictureDemo", "Exception in photoCallback", e);
//        }

    }
}