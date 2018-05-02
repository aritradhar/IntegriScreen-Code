package com.example.integriscreen;

/**
 * Created by eulqinaku on 02/05/2018.
 */

public class ActiveElementLogs {
    public String oldId;            // id of the previous active element -- maybe can be removed
    public String newId;            // id of the new active element
    public double timestamp;        // timestamp of the event

    public ActiveElementLogs(String oldElement, String newElement, double ts) {
        oldId = oldElement;
        newId = newElement;
        timestamp = ts;
    }
}
