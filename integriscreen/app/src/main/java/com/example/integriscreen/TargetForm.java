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

import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by eulqinaku on 11/04/2018.
 */

public class TargetForm {
    private String TAG = "TargetForm";
    Context applicationContext;
    public String formUrl;

    // Instantiate the RequestQueue.
    RequestQueue queue;

    // store UI elements
    public ArrayList<UIElement> allElements;

    // keeps track of active UI element
    public String activEl;
    public double timeTurnedActive;

    // This defines the max values of the coordinates
    public int resolution = 10000;

    public int inner_h, inner_w;
    public int border;

    // form ratio
    public int ratio_w, ratio_h;

    public int horizontal_denom;
    // form page id
    public String pageId;

    public boolean isLoaded;

    // url to submit the form
    private String submitURL;

    private OnDataLoadedEventListener parentActivity;

    public TargetForm(Context context, String targetUrl, OnDataLoadedEventListener parent) {
        parentActivity = parent;
        pageId = "";
        formUrl = targetUrl;
        isLoaded = false;
        activEl = "";
        timeTurnedActive = 0;
        allElements = null;
        applicationContext = context;
        submitURL = "";
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
                    ratio_w = Integer.parseInt(parts[0]);
                    ratio_h = Integer.parseInt(parts[1]);

                    // TODO: sto su stvarni ratio_w i ratio_h

                    // This is a factor by which I multiply all values that are based on vx or percentages
                    int res_scale = resolution / 100;

                    // vspace and border are in the same unit!
                    inner_h = res_scale * response.getInt("vspace");
                    border = res_scale * response.getInt("border_thickness");
                    inner_w = (int)Math.round((double)inner_h * ratio_w / ratio_h);


                    pageId = response.getString("page");

                    // Parsing json object response
                    JSONArray JSONElements = response.getJSONArray("elements");
                    Log.d(TAG, JSONElements.toString());

                    allElements = new ArrayList<UIElement>();

                    // iterate through all elements
                    for (int i = 0; i < JSONElements.length(); i++) {
                        JSONObject tmpObject = JSONElements.getJSONObject(i);
//                        Log.d(TAG, "Parsing JSONObject: " + tmpObject.toString());
                        String id = tmpObject.getString("id");
                        String editable = tmpObject.getString("editable");
                        String type = tmpObject.getString("type");

                        String defaultVal = tmpObject.getString("initialvalue");

                        double x1 = res_scale * tmpObject.getDouble("ulc_x");
                        double y1 = res_scale * tmpObject.getDouble("ulc_y");
                        double w1 = res_scale * tmpObject.getDouble("width");
                        double h1 = res_scale * tmpObject.getDouble("height");

                        /*
                        // Coefficients to convert from the coordinate system in which (0,0) is in
                        //   the internal corner of the green border to the coord. system
                        //   in which (0, 0) is in the external corner of the green border.
                        double x_coef = ((double)border + inner_w) / (2 * border + inner_w);
                        // delta values need to be in percentages
                        double x_delta = (double)100 * res_scale * border / (2 * border + inner_w);
                        double y_coef = ((double)border + inner_h) / (2 * border + inner_h);;
                        double y_delta = (double)100 * res_scale * border / (2 * border + inner_h);
                        double w_coef = (double)inner_w / (2 * border + inner_w);
                        double h_coef = (double)inner_h / (2 * border + inner_h);

                        int x = (int)Math.round(x_delta + x1 * x_coef);
                        int y = (int)Math.round(y_delta + y1 * y_coef) ;
                        int h = (int)Math.round(h1 * h_coef);
                        int w = (int)Math.round(w1 * w_coef);
*/
                        int x = (int)Math.round(x1);
                        int y = (int)Math.round(y1) ;
                        int h = (int)Math.round(h1);
                        int w = (int)Math.round(w1);


                        UIElement tmpElement = new UIElement(id, editable, type, new Rect(x, y, w, h), defaultVal);
                        Log.d(TAG, tmpElement.toString());

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
        for (int i = 0; i < allElements.size(); i++) {
            UIElement tmpEl = allElements.get(i);
            postParam.put(tmpEl.id, tmpEl.currentVal);
        }


        JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.POST,
                submitURL, new JSONObject(postParam), new Response.Listener<JSONObject>() {

            @Override
            public void onResponse(JSONObject response) {
                Log.d(TAG, response.toString());
            }
        }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                VolleyLog.d(TAG, "Error: " + error.getMessage());
                Log.d(TAG, error.getMessage());
            }
        });

        // Adding request to request queue
        queue.add(jsonObjReq);
    }

}
