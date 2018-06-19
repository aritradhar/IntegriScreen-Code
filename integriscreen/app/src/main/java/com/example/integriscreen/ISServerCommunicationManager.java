package com.example.integriscreen;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import static com.example.integriscreen.LogManager.logF;

public class ISServerCommunicationManager {
    private static HashMap<String, String> allExistingForms;
    // Store the RequestQueue.
    public static RequestQueue queue;

    public ISServerCommunicationManager(String serverURL, Context applicationContext) {
        allExistingForms = new HashMap<>();
        // initialize the RequestQueue of volley
        queue = Volley.newRequestQueue(applicationContext);
        getListOfForms(serverURL + "/generated/fileList.txt");
    }

    public String getFormURLFromName(String formName) {
        if (allExistingForms != null)
            return allExistingForms.get(formName);
        else
            return null;
    }

    public boolean isReady() {
        return !allExistingForms.isEmpty();
    }

    private void getListOfForms(String url) {
        logF("ListOfForms", "trying to get the listOfForms from: " + url);

        JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.GET,
                url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            // Parsing json object response
                            JSONArray JSONElements = response.getJSONArray("response");
                            // logF("ListOfForms", "ArrayList: " + JSONElements.toString());

                            logF("ListOfForms", "Total number:" + JSONElements.length());

                            // iterate through all elements
                            for (int i = 0; i < JSONElements.length(); i++) {
                                JSONObject currEl = JSONElements.getJSONObject(i);
                                String pageTitle = currEl.getString("page_title");
                                pageTitle = pageTitle.replaceAll("\\s+","");
                                String formJsonURL = currEl.getString("json");
                                // logF("Loading forms", "FormID: " + pageTitle + " -> " + formJsonURL);
                                allExistingForms.put(pageTitle, formJsonURL);
                            }

                        } catch (JSONException e) {
                            e.printStackTrace();
                            logF("Error Loading ListOfForms", e.getMessage());
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        logF("ListOfForms", String.valueOf(error.getStackTrace()));
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
