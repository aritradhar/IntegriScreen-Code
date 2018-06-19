package com.example.integriscreen;


import android.content.Context;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.core.Point;
import org.opencv.core.Rect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.example.integriscreen.LogManager.logF;


public class TargetForm {
    private String TAG = "TargetForm";
    private Context applicationContext;

    public String formUrl;

    public UIElement titleElement;

    // store UI elements
    public ArrayList<UIElement> allElements;

    // keeps track of active UI element
    public String activEl;
    public double activeSince;
    public double activeElementLastEdit;

    // This defines the max values of the coordinates
    public int resolution = 100;

    // form ratio
    public int form_ratio_w, form_ratio_h;

    // form page id
    public String pageId;

    public boolean isLoaded;

    public boolean initiallyVerified;

    // url to submit the form
//    private String submitURL;

    private OnDataLoadedEventListener parentActivity;
    public int form_w_abs, form_h_abs;  // the total amount of space available to draw the form
    public int maxScreenH = 1920; // This is hardcoded!

    public TargetForm(Context context, String targetUrl, int screenWidth, OnDataLoadedEventListener parent) {
        parentActivity = parent;
        pageId = "";
        isLoaded = false;
        initiallyVerified = false;
        activEl = "";
        activeSince = 0;
        activeElementLastEdit = 0;
        form_w_abs = screenWidth;
        // form_h_abs can not yet be computed -> we need to find out the form ratio first.
        allElements = null;
        applicationContext = context;

        formUrl = targetUrl;
        fetchAndParseFormData(targetUrl);
    }

    public void makeAllDirty() {
        for(UIElement element: allElements)
            element.dirty = true;
    }



    public UIElement getElementById(String elementId) {
        for(UIElement el : allElements) {
            if (el.id.equals(elementId))
                return el;
        }
        return null;
    }



    // if X is < low, return low, if larger than high, return high
    private long limitLowHigh(long x, long low, long high) { return Math.min(Math.max(x, low), high); }

    public String toString() {
        String outputString = "formUrl: " + formUrl;
        if (allElements != null) {
            for (int i = 0; i < allElements.size(); ++i)
                outputString = outputString + "|" + allElements.get(i).toString();
        }

        return outputString;
    }



    /**
     * Method to make json object request where json response starts wtih {
     * */
    private void fetchAndParseFormData(String url) {
        JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.GET,
                url, null, new Response.Listener<JSONObject>() {

            @Override
            public void onResponse(JSONObject response) {
                logF(TAG, response.toString());

                try {
                    String ratio = response.getString("ratio");

                    String[] parts = ratio.split(":");  // ATM 3:2 means width:height
                    form_ratio_w = Integer.parseInt(parts[0]);
                    form_ratio_h = Integer.parseInt(parts[1]);

                    form_h_abs = Math.min((int)Math.round(form_w_abs * form_ratio_h / (double) form_ratio_w),
                                            maxScreenH);  // this one we compute based on the form ratios


                    pageId = response.getString("page_id");

                    // Parsing json object response
                    JSONArray JSONElements = response.getJSONArray("elements");
                    logF(TAG, JSONElements.toString());

                    allElements = new ArrayList<UIElement>();

                    // iterate through all elements, compute their absolute coordinates
                    for (int i = 0; i < JSONElements.length(); i++) {
                        JSONObject currEl = JSONElements.getJSONObject(i);
//                        logF(TAG, "Parsing JSONObject: " + currEl.toString());
                        String id = currEl.getString("id");
                        Boolean editable = currEl.getBoolean("editable");
                        String type = currEl.getString("type");

                        String defaultVal = currEl.getString("initialvalue");


                        long a_x1 = Math.round(currEl.getDouble("ulc_x") * form_w_abs / (double)resolution);
                        long a_y1 = Math.round(currEl.getDouble("ulc_y") * form_h_abs / (double) resolution);
                        long a_width = Math.round(currEl.getDouble("width") * form_w_abs / (double) resolution);
                        long a_height = Math.round(currEl.getDouble("height") * form_h_abs / (double) resolution);
                        long a_x2 = a_x1 + a_width;
                        long a_y2 = a_y1 + a_height;


                        // --- If needed, add offsets ---
                        long offset = 0; // Offset to ensure that OCR does not fail due to tight limits on rectangles
                        a_x1 = limitLowHigh(a_x1 - offset, 0, form_w_abs-1);
                        a_y1 = limitLowHigh(a_y1 - offset, 0, form_h_abs -1);
                        a_x2 = limitLowHigh(a_x1 + a_width + offset, a_x1+1, form_w_abs);   // prevent overflows
                        a_y2 = limitLowHigh(a_y1 + a_height + offset, a_y1+1 , form_h_abs);   // prevent overflows



                        UIElement newElement = new UIElement(id, editable, type, new Rect(new Point(a_x1, a_y1), new Point(a_x2, a_y2)), defaultVal);
                        logF(TAG+" parsed element: ", newElement.toString());

                        if (type.equals("title"))
                            titleElement = newElement;

                                    // add the element in the arraylist of all UI elements
                        allElements.add(newElement);
                    }

                    // Set the form as isLoaded.
                    isLoaded = true;
                    // Notify the callback in the main activity
                    parentActivity.onFormLoaded();

                } catch (JSONException e) {
                    e.printStackTrace();
                    logF(TAG, e.getMessage());
                    Toast.makeText(applicationContext, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                logF(TAG, "Error: " + error.getMessage());
                Toast.makeText(applicationContext,
                        error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Adding request to request queue
        MainActivity.addToCommunicationQueue(jsonObjReq);
    }

    /**
     * This method sends a POST request to the server with a json including form data
     */
    public void submitFormData(String submitUrl) {
        logF("submitform", "Submit form with id: " + pageId);
        Map<String, String> postParam = new HashMap<String, String>();
        postParam.put("page_id", pageId);

        // Store all pairs editable elements in a hashmap
        for (UIElement currentElement: allElements) {
            if (!currentElement.editable) // skip elements that take no input
                continue;

            postParam.put(currentElement.id, currentElement.currentValue);
            logF("submitform", "key: " + currentElement.id + " -> " + currentElement.currentValue);
        }

        JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.POST,
                submitUrl, new JSONObject(postParam),
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        logF(TAG + "submitform", "Reply of form submit" + response.toString());
                        parentActivity.onReceivedSubmitDataResponse(response);
                    }
                },

                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        logF(TAG+ "submitform", "Error: " + error.getMessage());
                        logF("ListOfForms", String.valueOf(error.getStackTrace()));
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String>  params = new HashMap<String, String>();
                params.put("Content-Type", "multipart/mixed");
                return params;
            }
        };

        // Adding request to request queue
        MainActivity.addToCommunicationQueue(jsonObjReq);
    }
}