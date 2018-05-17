package com.example.integriscreen;

import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static com.example.integriscreen.LogManager.logF;

public class EvaluationController {
    public MainActivity mainActivity;

    private Timer reloadTimer;
    private TimerTask reloadTimerTask;

    int maxCnt = 200;
    int secondsToVerify = 10;

    private void cancelTimers() {
        // if (submitDataTimer != null) outputOnToast("Cancelling the existing timer.");

        if (reloadTimerTask!= null) reloadTimerTask.cancel();
        if (reloadTimer != null) reloadTimer.cancel();
    }


    public void storeOCRMismatches(List<Pair<String, String>> OCRMismatches) {
        for(Pair<String, String> mismatch : OCRMismatches)
            logF("OCR mismatch: ", "|" + mismatch.first + "| vs |" + mismatch.second + "|");
    }

    public EvaluationController(MainActivity parentActivity) {
        mainActivity = parentActivity;
    }

    public void finishEvaluation(int cntSuccess, int cntTotal)
    {
        cancelTimers();

        logF("Evaluation Finished:", "Success: " + cntSuccess + "/ " + cntTotal + " = " + (double)cntSuccess / cntTotal);

        mainActivity.reportEvaluationResult(cntSuccess, cntTotal);
    }

    // TODO: for each evaluation, we need a list of string mismatches: how did we misclassify what?

    public void startEvaluation() {
        cancelTimers();

        reloadTimer = new Timer();
        reloadTimerTask = new TimerTask() {
            int cntSuccess = 0;
            int cntTotal = 0;

            List<Integer> failIndices = new ArrayList<>();

            @Override
            public void run() {
                if (cntTotal > 0) {
                    if (mainActivity.previousFormSuccessfullyVerified()) {
                        ++cntSuccess;
                    }
                    else
                        failIndices.add(cntTotal-1);

                    String message = "Success rate: " + cntSuccess + " / " + cntTotal + " = " + (double)cntSuccess / cntTotal;
                    logF("Evaluation in progress:", message);
                    mainActivity.outputOnToast(message);
                }

                if (cntTotal <= maxCnt && !mainActivity.shouldStopEvaluation())
                    mainActivity.startIntegriScreen(cntTotal);
                else {
                    finishEvaluation(cntSuccess, cntTotal + 1); // reduce the cntTotal that was increased at the beginning!
                }
                ++cntTotal;
            }
        };

        // TODO: note: the time it takes to load the form is also a parameter that we could evaluate: "we are able to load 90% of forms in 2 seconds, and 95% in 5 seconds..."
        reloadTimer.schedule(reloadTimerTask, 0, 1000 * secondsToVerify);
    }

}
