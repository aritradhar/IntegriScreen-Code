package com.example.integriscreen;

import android.content.Context;
import android.util.Log;

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

import java.util.HashMap;
import java.util.Map;

public class ISServerCommunicationManager {
    private static HashMap<String, String> knownForms;
    // Store the RequestQueue.
    public static RequestQueue queue;

    public ISServerCommunicationManager(String serverURL, Context applicationContext) {
        knownForms = new HashMap<>();
        // initialize the RequestQueue of volley
        queue = Volley.newRequestQueue(applicationContext);
        getListOfForms(serverURL);
    }

    public String getFormURLFromName(String formName) {
        if (knownForms != null)
            return knownForms.get(formName);
        else
            return null;
    }

    public boolean isReady() {
        return !knownForms.isEmpty();
    }

    private void getListOfForms(String url) {
        Log.d("ListOfForms", "trying to get the listOfForms from: " + url);

        JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.POST,
                url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d("ListOfForms", "response: " + response.toString());

                        try {
                            // Parsing json object response
                            JSONArray JSONElements = response.getJSONArray("response");
                            Log.d("ListOfForms", "ArrayList: " + JSONElements.toString());

                            // iterate through all elements
                            for (int i = 0; i < JSONElements.length(); i++) {
                                JSONObject currEl = JSONElements.getJSONObject(i);
                                String pageTitle = currEl.getString("page_title");
                                pageTitle = pageTitle.replaceAll("\\s+","");
                                String formJsonURL = currEl.getString("json");
                                Log.d("Loading forms", "FormID: " + pageTitle + " -> " + formJsonURL);
                                knownForms.put(pageTitle, formJsonURL);
                            }

                        } catch (JSONException e) {
                            e.printStackTrace();
                            Log.d("ListOfForms", e.getMessage());
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        VolleyLog.d("ListOfForms", "Error: " + error.getMessage());
                        Log.d("ListOfForms", String.valueOf(error.getStackTrace()));
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String>  params = new HashMap<String, String>();
                params.put("Content-Type", "multipart/form-data");
                return params;
            }
        };

        // Adding request to request queue
        queue.add(jsonObjReq);
    }
}