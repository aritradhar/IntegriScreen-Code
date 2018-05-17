package com.example.integriscreen;


import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

import static com.example.integriscreen.ISImageProcessor.generatePathName;

public class LogManager {

    private static Context context;
    private static String pathName;

    public LogManager(Context currentContext) {
        context = currentContext;
        pathName = ISImageProcessor.generatePathName("log", ".txt");
    }

    public static void logW(String tag, String message) {
        Log.w(tag, message);
        writeToFile("WARNING:" + tag + ":" + message + "\n\n");
    }

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
