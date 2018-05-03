package com.example.integriscreen;

import android.util.Log;
import android.util.SparseArray;

import com.google.android.gms.vision.text.TextBlock;

public class ISStringProcessor {

    static boolean[][] similar = new boolean[256][256];
    // We use this function for now to allow for some slack in how well does OCR work, since e.g. i and j are often confused with the current font.
    public static boolean similarChar(char A, char B) {
        if (A >= 256 || B >= 256) return (A == B);

        for(int i = 0; i < 256; ++i)
            for(int j = 0; j < 256; ++j)
                similar[i][j] = (i == j);

        similar['i']['j'] = similar['j']['i'] = true;
        similar['8']['b'] = similar['b']['8'] = true;
        similar['0']['8'] = similar['8']['0'] = true;
        similar['0']['o'] = similar['o']['0'] = true;

        return similar[A][B];
    }

    // This method compares two strings, but loosely: ignoring whitesace and punctuation, and allowing that some characters are "similar"
    public static boolean almostIdenticalString(String A, String B) {
        A = OCRTrim(A);
        B = OCRTrim(B);

        if (A.length() != B.length()) return false;
        for(int i = 0; i < A.length(); ++i)
            if (!similarChar(A.charAt(i), B.charAt(i)))
                return false;

        return true;
    }

    public static String OCRTrim(String s) {
        String punctuationRegex = "[.,:!?\\-]";
        s = s.toLowerCase().replaceAll("\\s+","");
        s = s.replaceAll(punctuationRegex,"");
        return s;
    }

    /**
     *  This method verifies if the change from oldValue to newValue is legit
     */
    // NOTE: TODO: this does not yet assume that the user could e.g. copy/cut/paste or move around / delete words with ctrl+delete or ctrl+arrows
    public static boolean isChangeLegit(String sOld, String sNew, double duration) {
        int maxKeypressesPerSecond = 40;   // Based on Ivo's test of bashing on the keyboard for 10 seconds

        Log.i("changeLegit: old:", "|" + sOld + "|");
        Log.i("changeLegit: new:", "|" + sNew + "|");

        // First we remove the whitespace characters and punctuation before comparison!
        sOld = OCRTrim(sOld);
        sNew = OCRTrim(sNew);

        Log.i("changeLegit: trim old:", "|" + sOld + "|");
        Log.i("changeLegit: trim new:", "|" + sNew + "|");
        // We assume duration of 1000 is one second
        double durationSeconds = duration / 1000;

        // Find the shared prefix of the two strings: this is what the user does not need to edit.
        int sharedPrefixLength = 0;
        for(int i = 0; i < Math.min(sOld.length(), sNew.length()); ++i)
            if (similarChar(sOld.charAt(i), sNew.charAt(i)))
                ++sharedPrefixLength;
            else
                break;

        // trim the shared prefix
        sOld = sOld.substring(sharedPrefixLength);
        sNew = sNew.substring(sharedPrefixLength);

        Log.i("changeLegit:", "after prefix trim: |" + sOld + "|");
        Log.i("changeLegit:", "after prefix trim: |" + sNew + "|");


        // Find the shared sufix of the two strings: we can assume that the user does not need to edit this
        int sharedSuffixLength = 0;
        for(int i = 0; i < Math.min(sOld.length(), sNew.length()); ++i)
            if (similarChar(sOld.charAt(sOld.length() - i - 1), sNew.charAt(sNew.length() - i - 1)))
                ++sharedSuffixLength;
            else
                break;

        sOld = sOld.substring(0, sOld.length() - sharedSuffixLength);
        sNew = sNew.substring(0, sNew.length() - sharedSuffixLength);

        Log.i("changeLegit:", "after suffix trim: |" + sOld + "|");
        Log.i("changeLegit:", "after suffix trim: |" + sNew + "|");

        // Since each change costs at least 1, the minimal number of changes is the larger of the two remaining strings.
        //   If this is already larger than the maximum allowed number of keypresses in the given duration, return false
        if (Math.max(sOld.length(), sNew.length()) >  durationSeconds * maxKeypressesPerSecond) {
            Log.i("changeLegit: max len > ", sOld.length() + "|" + sNew.length() + "|" + durationSeconds + "|" + maxKeypressesPerSecond);
            return false;
        }

        int[][] dp = new int[sOld.length()+1][sNew.length()+1];
        dp[0][0] = 0;
        for(int i = 1; i <= sOld.length(); ++i) dp[i][0] = i;    // The cost of typing the whole sNew
        for(int i = 1; i <= sNew.length(); ++i) dp[0][i] = i;    // The cost of deleting the whole sOld

        for(int i = 1; i <= sOld.length(); ++i)
            for(int j = 1; j <= sNew.length(); ++j) {
                dp[i][j] = Math.min(dp[i-1][j] + 1, dp[i][j-1] + 1); // Either delete a char to old, or add one to new

                if (sOld.charAt(i-1) == sNew.charAt(j-1)) {    // In case chars are the same, you can also just use the left cursor arrow
                    dp[i][j] = Math.min(dp[i][j], dp[i-1][j-1] + 1);
                }
            }

        if (dp[sOld.length()][sNew.length()] >  durationSeconds * maxKeypressesPerSecond) {
            Log.i("changeLegit: dp > ", dp[sOld.length()][sNew.length()] + "|" + durationSeconds + "|" + maxKeypressesPerSecond);
            return false;
        }

        return true;
    }

    public static String concatTextBlocks (SparseArray<TextBlock> texts) {
        String textConcat = "";
        for (int i = 0; i < texts.size(); ++i) {
            TextBlock item = texts.valueAt(i);
            if (item != null && item.getValue() != null) {
                textConcat += item.getValue();
            }
        }
        return textConcat;
    }
}
