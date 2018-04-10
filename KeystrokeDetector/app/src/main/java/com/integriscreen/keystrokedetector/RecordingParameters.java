package com.integriscreen.keystrokedetector;

/**
 * com.integriscreen.keystrokedetector - Thread to record from mic
 * <p>
 * Created by daniele on 4/9/18.
 */
public class RecordingParameters {

    public static final int WINDOW_SIZE = 512;
    public static final int SAMPLE_RATE = 44100;
    public static final int BG_NOISE_LENGTH = 200;
    public static final float WINDOW_LENGTH = 1.0f * WINDOW_SIZE / SAMPLE_RATE;
    public static final int BG_SAMPLES = Math.round(BG_NOISE_LENGTH / WINDOW_LENGTH);
    public static final int KEYSTROKE_MIN_DISTANCE = Math.round(50 / WINDOW_LENGTH);

}
