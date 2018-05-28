package com.example.integriscreen;

import android.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static com.example.integriscreen.LogManager.logF;
import static com.example.integriscreen.LogManager.logR;

public class EvaluationController {
    public MainActivity mainActivity;

    private Timer reloadTimer;
    private TimerTask reloadTimerTask;

    int maxCnt = 200;
    int secondsToVerify = 4;

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

    public void finishEvaluation(int cntSuccess, int cntTotal, List<String> failIndices)
    {
        cancelTimers();

        logR("Evaluation Finished:", "Success: " + cntSuccess + "/ " + cntTotal + " = " + (double)cntSuccess / cntTotal);
        String failIndicesFormatted = "";
        for(String fail : failIndices)
            failIndicesFormatted += fail + ".html\n";

        logR("UI Verification failed on forms: ", failIndices.toString());
        logR("UI Verification failed on forms (formatted):\n", failIndicesFormatted);

        mainActivity.reportEvaluationResult(cntSuccess, cntTotal);
    }

    public void abortAll() {
        cancelTimers();
    }

    // TODO: for each evaluation, we need a list of string mismatches: why did we misclassify what?

    public void startEvaluation() {
        cancelTimers();

        reloadTimer = new Timer();
        reloadTimerTask = new TimerTask() {
            int cntSuccess = 0;
            int cntTotal = 0;

            List<String> filedFormIDs = new ArrayList<>();

            @Override
            public void run() {
                if (cntTotal > 0) {
                    if (mainActivity.previousFormSuccessfullyVerified()) {
                        ++cntSuccess;
                    }
                    else
                        filedFormIDs.add(mainActivity.getCurrentFormName());

                    String message = "Success rate: " + cntSuccess + " / " + cntTotal + " = " + (double)cntSuccess / cntTotal;
                    logF("Evaluation in progress:", message);
                    mainActivity.outputOnToast(message);
                }

                if (cntTotal <= maxCnt && !mainActivity.shouldStopEvaluation()) {
                    mainActivity.evaluationStarting = false;
                    mainActivity.startIntegriScreen(cntTotal);
                }
                else {
                    finishEvaluation(cntSuccess, cntTotal + 1, filedFormIDs); // reduce the cntTotal that was increased at the beginning!
                }
                ++cntTotal;
            }
        };

        // TODO: note: the time it takes to load the form is also a parameter that we could evaluate: "we are able to load 90% of forms in 2 seconds, and 95% in 5 seconds..."
        reloadTimer.schedule(reloadTimerTask, 0, 1000 * secondsToVerify);
    }

}
