package com.example.integriscreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.opencv.android.JavaCameraView;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import static com.example.integriscreen.LogManager.logF;


public class CustomCameraView extends JavaCameraView implements PictureCallback {

    private static final String TAG = "OCV::CustomCameraView";
    private OnDataLoadedEventListener parentActivity;

    // eu: variables related to sqare view for touch focus
    private DrawingView drawingView;
    public boolean drawingViewSet = false;

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
     *  Set focus at given coordinates x,y and square size size
     */
    public void focusAt(float x, float y, int size) {
        logF("FocusMode", "Focus At: " + x + ", " + y + ", sqare size: " + size);

        android.graphics.Rect screenRect = new android.graphics.Rect(
                (int)(x - size),
                (int)(y - size),
                (int)(x + size),
                (int)(y + size));

        focusAtRectOnScreen(screenRect);
    }


    /**
     *  Set focus at given coordinates at the green box of the form
     */
    public void focusAt(org.opencv.core.Rect rect)   {
        logF("FocusMode", "Focus at rectangle: " + rect.x + ", " + rect.y
                + ", " + rect.width + ", " + rect.height);

        // convert opencv.core.Rect to android.graphics.Rect
        android.graphics.Rect screenRect = new android.graphics.Rect(
                rect.x,
                rect.y,
                rect.x + rect.width,
                rect.y + rect.height);

        focusAtRectOnScreen(screenRect);
    }

    public void focusAtRectOnScreen(Rect screenRect) {

        // convert rect to the camera specific
        final android.graphics.Rect targetFocusRect = new android.graphics.Rect(
                screenRect.left * 2000/this.getWidth() - 1000,
                screenRect.top * 2000/this.getHeight() - 1000,
                screenRect.right * 2000/this.getWidth() - 1000,
                screenRect.bottom * 2000/this.getHeight() - 1000);

        doTouchFocus(targetFocusRect);

        if (drawingViewSet) {
            drawingView.setHaveTouch(true, screenRect);
            drawingView.invalidate();

            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    drawingView.setHaveTouch(false, new android.graphics.Rect(0, 0, 0, 0));
                    drawingView.invalidate();
                }
            }, 1000);
        }
    }


    /**
     * Called from PreviewSurfaceView to set touch focus.
     *
     * @param - Rect - new area for auto focus
     */
    public void doTouchFocus(final Rect tfocusRect) {
        logF("FocusMode", "inside doTouchFocus() method");
        try {
            logF("FocusMode", "Focus rect: (" + tfocusRect.left + ", " + tfocusRect.top
                    + ") x (" + tfocusRect.right + ", " + tfocusRect.bottom +  ")");

            final List<Camera.Area> focusList = new ArrayList<Camera.Area>();
            Camera.Area focusArea = new Camera.Area(tfocusRect, 1000);
            focusList.add(focusArea);

            Camera.Parameters para = mCamera.getParameters();
            para.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            para.setFocusAreas(focusList);
            para.setMeteringAreas(focusList);
            mCamera.setParameters(para);

            mCamera.autoFocus(myAutoFocusCallback);
        } catch (Exception e) {
            e.printStackTrace();
            logF("FocusMode", "Unable to autofocus");
        }

    }

    /**
     * AutoFocus callback
     */
    Camera.AutoFocusCallback myAutoFocusCallback = new Camera.AutoFocusCallback(){

        @Override
        public void onAutoFocus(boolean arg0, Camera arg1) {
            logF("FocusMode", "inside onAutoFocus callback, arg0: " + arg0
                    + ", arg1: " + arg1.toString());

            try {
                if (arg0){
                    mCamera.cancelAutoFocus();
                }
            } catch (Exception e) {
                e.printStackTrace();
                logF("FocusMode", "Error on myAutoFocusCallback");
            }
        }
    };

    /**
     * set DrawingView instance for touch focus indication.
     */
    public void setDrawingView(DrawingView dView) {
        drawingView = dView;
        drawingViewSet = true;
    }

    /**
     * This method sets the size of picture taken within the app.
     * Quality set to 0 takes the best picture.
     */
    public void setPictureSize(int quality) {
        Camera.Parameters params = mCamera.getParameters();
        List<Size> sizes = params.getSupportedPictureSizes();

//        for (int i = 0; i < sizes.size(); i++)
//            logF(TAG, "Supported PicSize - Height: " + sizes.get(i).height + ", Width: " + sizes.get(i).width);

        mCamera.stopPreview(); // Preview should be stopped in order to update camera parameters

        params.setPictureSize(sizes.get(quality).width, sizes.get(quality).height);
        mCamera.setParameters(params);

//        logF(TAG, "Picture Size (Width x Height px): " + mCamera.getParameters().getPictureSize().width
//                + " x " + mCamera.getParameters().getPictureSize().height);
        mCamera.startPreview();
    }

    public void takePicture(OnDataLoadedEventListener parent) {
        try {
            logF(TAG, "Taking picture");
            parentActivity = parent;
            // Postview and jpeg are sent in the same buffers if the queue is not empty when performing a capture.
            // Clear up buffers to avoid mCamera.takePicture to be stuck because of a memory issue
            mCamera.setPreviewCallback(null);

            // PictureCallback is implemented by the current class
            mCamera.takePicture(null, null, this);
        } catch (Exception e) {
            e.printStackTrace();
            logF(TAG, "Error on takePicture()");
        }
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        try {
            logF(TAG, "Saving a bitmap to file");
            // The camera preview was automatically stopped. Start it again.
            mCamera.startPreview();
            mCamera.setPreviewCallback(this);

            parentActivity.onPicTaken(data);
        } catch (Exception e) {
            e.printStackTrace();
            logF(TAG, "Error on onPictureTaken()");
        }
    }

}