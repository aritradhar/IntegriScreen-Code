package com.example.integriscreen;

import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

public class EvaluationController {
    public EvaluationListener mainActivity;

    private Timer reloadTimer;
    private TimerTask reloadTimerTask;

    int maxCnt = 200;
    int secondsToVerify = 10;

    private void cancelTimers() {
        // if (submitDataTimer != null) outputOnToast("Cancelling the existing timer.");

        if (reloadTimerTask!= null) reloadTimerTask.cancel();
        if (reloadTimer != null) reloadTimer.cancel();
    }


    public EvaluationController(EvaluationListener parentActivity) {
        mainActivity = parentActivity;
    }

    public void finishEvaluation(int cntSuccess, int cntTotal)
    {
        cancelTimers();

        Log.d("Evaluation Finished:", "Success: " + cntSuccess + "/ " + cntTotal + " = " + (double)cntSuccess / cntTotal);

        mainActivity.reportEvaluationResult(cntSuccess, cntTotal);
    }

    public void startEvaluation() {
        cancelTimers();

        reloadTimer = new Timer();
        reloadTimerTask = new TimerTask() {
            int cntSuccess = 0;
            int cntTotal = 0;

            @Override
            public void run() {
                if (cntTotal > 0 && mainActivity.previousFormSuccessfullyVerified())
                    ++cntSuccess;

                if (cntTotal > 0) {
                    String message = "Success rate: " + cntSuccess + " / " + cntTotal + " = " + (double)cntSuccess / cntTotal;
                    Log.d("Evaluation in progress:", message);
                    mainActivity.outputOnToast(message);
                }

                if (cntTotal <= maxCnt && !mainActivity.shouldStopEvaluation())
                    mainActivity.startIntegriScreen();
                else {
                    finishEvaluation(cntSuccess, cntTotal - 1); // reduce the cntTotal that was increased at the beginning!
                }
                ++cntTotal;
            }
        };

        // TODO: note: the time it takes to load the form is also a parameter that we could evaluate: "we are able to load 90% of forms in 2 seconds, and 95% in 5 seconds..."
        reloadTimer.schedule(reloadTimerTask, 0, 1000 * secondsToVerify);
    }

}