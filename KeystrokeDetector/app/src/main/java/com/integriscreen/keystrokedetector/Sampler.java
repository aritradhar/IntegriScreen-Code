package com.integriscreen.keystrokedetector;

import android.icu.text.AlphabeticIndex;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.widget.Button;
import android.widget.TextView;


import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.paramsen.noise.Noise;
import com.paramsen.noise.NoiseOptimized;

import java.util.Arrays;
import java.util.stream.DoubleStream;

/**
 * Thread to record from mic
 *
 * Created by daniele on 3/28/18.
 */

public class Sampler extends Thread {

    private NoiseOptimized fft;
    private float[] fftInput;
    private float[] fftOutput;
    private AudioRecord recorder;
    private MainActivity mainActivity;
    private int sampleCount = 0;
    private int keystrokeCount = 0;
    public Boolean isRunning;

    Sampler(MainActivity mA) {
        mainActivity = mA;
    }

    public void run() {

        fftInput = new float[RecordingParameters.WINDOW_SIZE];

        fft = Noise.real()
                .optimized()
                .init(RecordingParameters.WINDOW_SIZE, false);

        recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RecordingParameters.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, RecordingParameters.WINDOW_SIZE);

        recorder.startRecording();

        isRunning = Boolean.TRUE;
        int status = 0;
        while (isRunning) {
            // Read data
            status = recorder.read(fftInput, 0, RecordingParameters.WINDOW_SIZE, AudioRecord.READ_BLOCKING);
            fftOutput = new float[RecordingParameters.WINDOW_SIZE + 2];
            fft.fft(fftInput, fftOutput);
            sampleCount += 1;

            double[] absOutput = new double[fftOutput.length / 2];

            for(int i = 0; i < fftOutput.length / 2; i++) {
                float realPart = fftOutput[i * 2];
                float imPart = fftOutput[i * 2 + 1];
                absOutput[i] = Math.sqrt(realPart*realPart + imPart*imPart);
                //mainActivity.updateXChart(res);
            }
            double energy = DoubleStream.of(absOutput).sum();
            mainActivity.updateXChart(energy);
            mainActivity.energySerie.add(energy);
            mainActivity.runOnUiThread(() -> ((TextView) mainActivity.findViewById(R.id.threshold)).setText(String.valueOf(energy)));

            // TODO: we need a way to decide how much above BG noise to set the threshold
                // A threshold exists &&
            if (mainActivity.thresholdValue != -1 &&
                    // Current window energy is above threshold &&
                    energy > mainActivity.thresholdValue + RecordingParameters.THRESHOLD_DELTA &&
                    // last event was enough milliseconds ago
                    mainActivity.last_event_window + RecordingParameters.KEYSTROKE_MIN_WINDOW <= sampleCount) {
                // event detected
                // Is it a keystroke?
                mainActivity.isKeystroke(Arrays.copyOfRange(fftOutput, 0, RecordingParameters.WINDOW_SIZE), 0);
            }

        }

    }

}
