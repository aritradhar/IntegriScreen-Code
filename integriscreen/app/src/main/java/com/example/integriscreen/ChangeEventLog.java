package com.example.integriscreen;

/**
 * Created by eulqinaku on 31/05/2018.
 */

public class ChangeEventLog {
    public long timeStamp;
    public String elementId;
    public boolean isActive;
    public String oldValue;
    public String newValue;

    public ChangeEventLog(long ts, String elId, boolean act, String oldV, String newV) {
        timeStamp = ts;
        elementId = elId;
        isActive = act;
        oldValue = oldV;
        newValue = newV;
    }


}
