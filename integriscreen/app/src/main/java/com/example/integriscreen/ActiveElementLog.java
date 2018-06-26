package com.example.integriscreen;

import android.util.Log;

import static com.example.integriscreen.ISStringProcessor.OCRTrim;
import static com.example.integriscreen.ISStringProcessor.similarChar;

public class ActiveElementLog {
    public String oldId;            // id of the previous active element -- maybe can be removed
    public String newId;            // id of the new active element
    public String oldValue;         // old value of the current active element
    public String newValue;         // new value of the current active element
    public long timestamp;          // timestamp of the event
    public long duration;           // duration of change

    public ActiveElementLog(String oldElementID, String newElementID, String oldVal, String newVal, long ts, long dur) {
        oldId = oldElementID;
        newId = newElementID;
        oldValue = oldVal;
        newValue = newVal;
        timestamp = ts;
        duration = dur;
    }


    // extract the diff part for oldValue and newValue
    public static void extractDiffOfStrings(String str1, String str2) {
        // First we remove the whitespace characters and punctuation before comparison!
        str1 = OCRTrim(str1);
        str2 = OCRTrim(str2);

//        Log.d("changeLegit: trim old:", "|" + str1 + "|");
//        Log.d("changeLegit: trim new:", "|" + str2 + "|");

        // Find the shared prefix of the two strings: this is what the user does not need to edit.
        int sharedPrefixLength = 0;
        for(int i = 0; i < Math.min(str1.length(), str2.length()); ++i)
            if (similarChar(str1.charAt(i), str2.charAt(i), true))
                ++sharedPrefixLength;
            else
                break;

        // trim the shared prefix
        str1 = str1.substring(sharedPrefixLength);
        str2 = str2.substring(sharedPrefixLength);

//        Log.d("changeLegit:", "after prefix trim: |" + str1 + "|");
//        Log.d("changeLegit:", "after prefix trim: |" + str2 + "|");


        // Find the shared sufix of the two strings: we can assume that the user does not need to edit this
        int sharedSuffixLength = 0;
        for(int i = 0; i < Math.min(str1.length(), str2.length()); ++i)
            if (similarChar(str1.charAt(str1.length() - i - 1), str2.charAt(str2.length() - i - 1), true))
                ++sharedSuffixLength;
            else
                break;

        str1 = str1.substring(0, str1.length() - sharedSuffixLength);
        str2 = str2.substring(0, str2.length() - sharedSuffixLength);

//        Log.d("changeLegit:", "after suffix trim: |" + str1 + "|");
//        Log.d("changeLegit:", "after suffix trim: |" + str2 + "|");
    }
}
