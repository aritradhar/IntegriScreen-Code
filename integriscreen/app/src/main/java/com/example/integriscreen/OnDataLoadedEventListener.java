package com.example.integriscreen;

public interface OnDataLoadedEventListener{
    public void onFormLoaded();

    // callback when picture is taken
    public void onPicTaken(byte[] data);
}
