package com.example.integriscreen;


import android.content.Context;
import android.util.Log;
import android.widget.Toast;

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

/**
 * Created by eulqinaku on 11/04/2018.
 */

public class TargetForm {
    private String TAG = "TargetForm";
    Context applicationContext;

    // Instantiate the RequestQueue.
    RequestQueue queue;

    // store UI elements
    public ArrayList<UIElement> allElements;

    // keeps track of active UI element
    public String activEl;
    public double timeTurnedActive;

    // form ratio
    public String ratio;
    // form page id
    public String pageId;


    public TargetForm(Context context, String targetUrl) {
        ratio = "16:9";     // default ratio
        pageId = "";
        activEl = "";
        timeTurnedActive = 0;
        allElements = null;
        applicationContext = context;
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
    public int matchElFromDiffAt(Point point) {
        int index = -1; // -1 represents no element is found on the diff location
        for (int i = 0; i < allElements.size(); i++) {
            if (allElements.get(i).box.contains(point)) {
                index = i;
                break;
            }
        }
        return index;
    }

    /**
     * This method finds the corresponding Element from a rectangle where diff happens
     */
    public int matchElFromDiffAt(Rect rect) {
        int index = -1; // -1 represents no element is found on the diff location
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
                    ratio = response.getString("ratio");
                    pageId = response.getString("page");
                    // Parsing json object response
                    JSONArray allElements = response.getJSONArray("elements");
                    Log.d(TAG,allElements.toString());

                    // iterate through all elements
                    int totalElements = allElements.length();
                    for (int i = 0; i < totalElements; i++) {
                        JSONObject tmpObject = allElements.getJSONObject(i);
//                        Log.d(TAG, "Parsing JSONObject: " + tmpObject.toString());
                        String id = tmpObject.getString("id");
                        String editable = tmpObject.getString("editable");
                        String type = tmpObject.getString("type");
                        int x1 = tmpObject.getInt("ulc_x");
                        int y1 = tmpObject.getInt("ulc_y");
                        int x2 = tmpObject.getInt("lrc_x");
                        int y2 = tmpObject.getInt("lrc_y");
                        String defaultVal = tmpObject.getString("initialvalue");

                        UIElement tmpElement = new UIElement(id, editable, type, x1, y1, x2, y2, defaultVal);
                        Log.d(TAG, tmpElement.toString());
                        // add the element in the arraylist
                        allElements.put(tmpElement);
                    }

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

}
