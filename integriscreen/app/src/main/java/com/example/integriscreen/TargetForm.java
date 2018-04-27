package com.example.integriscreen;


import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.core.Point;
import org.opencv.core.Rect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class TargetForm {
    private String TAG = "TargetForm";
    private Context applicationContext;
    public String formUrl;

    // Instantiate the RequestQueue.
    private RequestQueue queue;

    // store UI elements
    public ArrayList<UIElement> allElements;

    // keeps track of active UI element
    public String activEl;
    public double timeTurnedActive;

    // This defines the max values of the coordinates
    public int resolution = 100;

    // form ratio
    public int form_ratio_w, form_ratio_h;

    // form page id
    public String pageId;

    public boolean isLoaded;

    // url to submit the form
    private String submitURL;

    private OnDataLoadedEventListener parentActivity;
    public int form_w_abs, form_h_abs;  // the total amount of space available to draw the form
    public int maxScreenH;

    public TargetForm(Context context, String targetUrl, int screenWidth, int maxScreenHeight, OnDataLoadedEventListener parent) {
        parentActivity = parent;
        pageId = "";
        formUrl = targetUrl;
        isLoaded = false;
        activEl = "";
        timeTurnedActive = 0;
        form_w_abs = screenWidth;
        // form_h_abs can not yet be computed -> we need to find out the form ratio first.
        maxScreenH = maxScreenHeight;
        allElements = null;
        applicationContext = context;
        submitURL = "http://tildem.inf.ethz.ch/IntegriScreenServer/MainServer?page_type=mobile_form";
        queue = Volley.newRequestQueue(applicationContext);
        makeJsonObjectRequest(targetUrl);
    }

    /**
     * Set the active element based on the point where the diff happens
     */
    public void setActivElAtDiff(Point point) {
        for (int i = 0; i < allElements.size(); i++) {
            if (allElements.get(i).box.contains(point)) {
                activEl = allElements.get(i).id;
                timeTurnedActive = System.currentTimeMillis();
                break;
            }
        }
    }

    /**
     * Set the active element based on the rectangle where the diff happens
     */
    public void setActivElAtDiff(Rect rect) {
        Point tl = new Point(rect.x, rect.y);
        Point rb = new Point(rect.x + rect.width, rect.y + rect.height);

        for (int i = 0; i < allElements.size(); i++) {
            if (allElements.get(i).box.contains(tl) &&
                    allElements.get(i).box.contains(rb)) {
                activEl = allElements.get(i).id;
                timeTurnedActive = System.currentTimeMillis();
                break;
            }
        }
    }

    /**
     * This method finds the corresponding Element from a point of diff event
     */
    public int matchElFromPoint(Point point) {
        int index = -1; // -1 represents no element is found on the diff location
        for (int i = 0; i < allElements.size(); i++) {
            if (allElements.get(i).box.contains(point)) {
                index = i;
                break;
            }
        }
        return index;
    }

    // Returns the index of an element that *fully* contains rect
    public int matchElFromRect(Rect rect) {
        int index = -1;

        Point tl = new Point(rect.x, rect.y);
        Point rb = new Point(rect.x + rect.width, rect.y + rect.height);
        for (int i = 0; i < allElements.size(); i++) {
            if (allElements.get(i).box.contains(tl) &&
                    allElements.get(i).box.contains(rb)) {
                index = i;
                break;
            }
        }
        return index;
    }

    /**
     * Get UI Element with index i
     */
    public UIElement getElement(int index) {
        return allElements.get(index);
    }

    /**
     * Set an UI Element in a specific index
     */
    public void setElement(int index, UIElement element) {
        allElements.set(index, element);
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
     * get number of UI Elements in the form
     */
    public int getUIElNumber() {
        return allElements.size();
    }

    /**
     * Method to make json object request where json response starts wtih {
     * */
    private void makeJsonObjectRequest(String url) {
        JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.GET,
                url, null, new Response.Listener<JSONObject>() {

            @Override
            public void onResponse(JSONObject response) {
                Log.d(TAG, response.toString());

                try {
                    String ratio = response.getString("ratio");

                    String[] parts = ratio.split(":");  // ATM 3:2 means width:height
                    form_ratio_w = Integer.parseInt(parts[0]);
                    form_ratio_h = Integer.parseInt(parts[1]);

                    form_h_abs = Math.min((int)Math.round(form_w_abs * form_ratio_h / (double) form_ratio_w),
                                            maxScreenH);  // this one we compute based on the form ratios


                    pageId = response.getString("page_id");
                    submitURL += "&pageid=" + pageId; //Todo eu: check if variable is fine

                    // Parsing json object response
                    JSONArray JSONElements = response.getJSONArray("elements");
                    Log.d(TAG, JSONElements.toString());

                    allElements = new ArrayList<UIElement>();

                    // iterate through all elements, compute their absolute coordinates
                    for (int i = 0; i < JSONElements.length(); i++) {
                        JSONObject currEl = JSONElements.getJSONObject(i);
//                        Log.d(TAG, "Parsing JSONObject: " + currEl.toString());
                        String id = currEl.getString("id");
                        String editable = currEl.getString("editable");
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



                        UIElement tmpElement = new UIElement(id, editable, type, new Rect(new Point(a_x1, a_y1), new Point(a_x2, a_y2)), defaultVal);
                        Log.d(TAG+" parsed element: ", tmpElement.toString());

                        // add the element in the arraylist of all UI elements
                        allElements.add(tmpElement);
                    }

                    // Set the form as isLoaded.
                    isLoaded = true;
                    // Notify the callback in the main activity
                    parentActivity.onFormLoaded();

                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.d(TAG, e.getMessage());
                    Toast.makeText(applicationContext, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                VolleyLog.d(TAG, "Error: " + error.getMessage());
                Toast.makeText(applicationContext,
                        error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Adding request to request queue
        queue.add(jsonObjReq);
    }

    /**
     * This method sends a POST request to the server with a json including form data
     */
    public void submitFormData() {
        Map<String, String> postParam = new HashMap<String, String>();

        //store all pairs of elements in a hashmap
//        for (int i = 0; i < allElements.size(); i++) {
//            UIElement tmpEl = allElements.get(i);
//            postParam.put(tmpEl.id, tmpEl.currentVal);
//        }

        postParam.put("key", "value");
        postParam.put("key1", "value1");

        JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.POST,
                submitURL, new JSONObject(postParam),
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d(TAG, response.toString());
                    }
                },

                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        VolleyLog.d(TAG, "Error: " + error.getMessage());
                        Log.d(TAG, error.getMessage());
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String>  params = new HashMap<String, String>();
                params.put("enctype", "multipart/form-data");
                return params;
            }
        };

        // Adding request to request queue
        queue.add(jsonObjReq);
    }

}
