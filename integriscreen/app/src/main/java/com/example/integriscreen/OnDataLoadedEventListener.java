package com.example.integriscreen;

/**
 * Created by ivo on 19-Apr-18.
 */

public interface OnDataLoadedEventListener{
    public void onFormLoaded();

    // callback when picture is taken
    public void onPicTaken(byte[] data);
}
