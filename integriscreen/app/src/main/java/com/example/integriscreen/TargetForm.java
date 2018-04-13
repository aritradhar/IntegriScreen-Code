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

    public TargetForm(Context context, String targetUrl) {
        allElements = null;
        applicationContext = context;
        queue = Volley.newRequestQueue(applicationContext);
        makeJsonObjectRequest(targetUrl);
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
