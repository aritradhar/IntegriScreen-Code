package com.example.integriscreen;

import android.graphics.Rect;

import java.util.ArrayList;

/**
 * Created by eulqinaku on 11/04/2018.
 */

public class UIElement {
    public String id;
    public String editable;      // 0-No, 1-Yes, 2-Enumerate
    public String type;         // label, textbox, radio, checkbox, textarea
    int x1, y1, x2, y2;         // left, top, right, bottom
    public String defaultVal;   // default value of the UI element
    public String currentVal;   // current value of the UI element extracted through OCR
    public boolean correctlyLocated;
//    public ArrayList<> traces;  // list all changes for an UI element
//    public int certainity;      // store the confidence


    public UIElement(String i, String ed, String tp, int _x1, int _y1, int _x2, int _y2, String dV) {
        id = i;
        editable = ed;
        type = tp;
        x1 = _x1;
        y1 = _y1;
        x2 = _x2;
        y2 = _y2;
        defaultVal = dV;
        currentVal = "";
        correctlyLocated = false;
    }

    public String toString() {
        return "UI element[id: " + id + ", editable: " + editable + ", type: " + type
                + ", position: (" + x1 + ", " + y1 + ", "
                + x2 + ", " + y2 + "), default: " + defaultVal
                + ", currentVal: " + currentVal + ", located: " + correctlyLocated + "]";

    }

}
