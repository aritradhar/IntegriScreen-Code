package com.example.integriscreen;

import org.json.JSONObject;

public interface EvaluationListener {
    public void startEvaluation(int startIndex, int endIndex);

    public void startIntegriScreen();
    public boolean shouldStopEvaluation();
    public boolean previousFormSuccessfullyVerified();
}
