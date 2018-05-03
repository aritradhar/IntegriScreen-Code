package com.example.integriscreen;

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
