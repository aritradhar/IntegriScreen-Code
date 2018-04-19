package com.integriscreen.keystrokedetector;

/**
 * com.integriscreen.keystrokedetector - Thread to record from mic
 * <p>
 * Created by daniele on 4/9/18.
 */
public class RecordingParameters {

    public static final int WINDOW_SIZE = 2048;
    public static final int SAMPLE_RATE = 44100;
    // Length of the BG noise window, in milliseconds
    public static final int BG_NOISE_LENGTH = 200;
    // Length of the FFT window, in milliseconds
    public static final float WINDOW_LENGTH = 1000f * WINDOW_SIZE / SAMPLE_RATE;
    // Length of the BG noise window, in samples
    public static final int BG_SAMPLES = Math.round(BG_NOISE_LENGTH / WINDOW_LENGTH);
    // Minimum distance between keystrokes, in milliseconds
    public static final int KEYSTROKE_MIN_DISTANCE = 120;
    // Minimum distance between keystrokes, in samples
    public static final int KEYSTROKE_MIN_WINDOW = Math.round(KEYSTROKE_MIN_DISTANCE / WINDOW_LENGTH);

}
