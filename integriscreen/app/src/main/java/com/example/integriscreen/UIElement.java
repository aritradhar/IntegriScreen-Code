package com.example.integriscreen;



import org.opencv.core.Rect;


public class UIElement {
    public String id;
    public Boolean editable;      // 0-No, 1-Yes, 2-Enumerate
    public String type;         // label, textbox, textbox, radio, checkbox, textarea   //TODO: add restrictions for sensitive elements, NUMBER_ONLY, BTC_ADDRESS_ONLY
    public Rect box;                   // location of the UI on the screen, in apsolute coordinates!
    public String defaultVal;   // default value of the UI element
    public String currentValue;   // current value of the UI element extracted through OCR
    public long lastUpdated;  // track the time of last update
    public boolean dirty;       // true if the element's value should be checked before allowing any model updates

//    public ArrayList<> traces;  // list all changes for an UI element
//    public int certainity;      // store the confidence


    public UIElement(String i, Boolean ed, String tp, Rect bounding_box, String dV) {
        id = i;
        editable = ed;
        type = tp;
        box = bounding_box;
        defaultVal = dV;
        currentValue = defaultVal;
        lastUpdated = System.currentTimeMillis();
        dirty = true;
    }

    public Rect getRescaledBox(double scaleX, double scaleY) {
        return new Rect((int)Math.round(box.tl().x * scaleX),
                        (int)Math.round(box.tl().y * scaleY),
                        (int)Math.round(box.width * scaleX),
                        (int)Math.round(box.height * scaleY));
    }

    public String toString() {
        return "UI element[id: " + id + ", editable: " + editable + ", type: " + type
                + ", position: (" + box.x + ", " + box.y + ", "
                + box.width + ", " + box.height + "), default: " + defaultVal
                + ", currentValue: " + currentValue + ", dirty: " + dirty + "]";

    }

}
