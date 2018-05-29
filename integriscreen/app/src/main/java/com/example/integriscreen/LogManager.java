package com.example.integriscreen;


import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

import static com.example.integriscreen.ISImageProcessor.generatePathName;

public class LogManager {

    private static Context context;
    private static String pathName;
    private static MainActivity mainActivity;

    public LogManager(Context currentContext, MainActivity mActivity) {
        mainActivity = mActivity;
        context = currentContext;
        pathName = ISImageProcessor.generatePathName("_log", ".txt");
    }

    // Logging for final results
    public static void logR(String tag, String message) {
        Log.w(tag, message);
        writeToFile("RESULT: " + tag + ":" + message + "\n\n");
    }

    public static void logW(String tag, String message, boolean toastOuput) {
        Log.w(tag, message);
        writeToFile("WARNING:" + tag + ":" + message + "\n\n");
        if (toastOuput)
            mainActivity.outputOnToast(tag + ":" + message);
        else
            mainActivity.outputOnUILabel(tag + ":" + message);
    }
    // Logging decorator for warnings
    public static void logW(String tag, String message) {
        logW(tag, message, false);
    }

    // Standard Logging decorator
    public static void logF(String tag, String message) {
        Log.i(tag, message);
        writeToFile(tag + ":" + message + "\n\n");
    }

    public static void writeToFile(String str) {
        try {
            File yourFile = new File(pathName);
            yourFile.createNewFile();
            FileOutputStream outputStream = new FileOutputStream(yourFile, true);

            outputStream.write(str.getBytes());
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
