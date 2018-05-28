package com.example.integriscreen;

import org.json.JSONObject;

import java.util.HashMap;

public interface OnDataLoadedEventListener{
    public void onFormLoaded();

    // callback when picture is taken
    public void onPicTaken(byte[] data);

    public void onReceivedSubmitDataResponse(JSONObject responseJSON);
}
