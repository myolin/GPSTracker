package com.myolin.followme.Volley;

import android.net.Uri;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.myolin.followme.Activity.MainActivity;

import org.json.JSONException;
import org.json.JSONObject;

public class VerifyUserCredentialsAPIVolley {

    private static final String dataUrl =
            "http://christopherhield-001-site4.htempurl.com/api/UserAccounts/VerifyUserCredentials";

    private static final String TAG = "VerifyUserCredentialsAP";

    private final RequestQueue queue;
    private final String urlToUse;
    private final MainActivity mainActivity;

    public VerifyUserCredentialsAPIVolley(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
        this.queue = Volley.newRequestQueue(mainActivity);
        Uri.Builder buildURL = Uri.parse(dataUrl).buildUpon();
        urlToUse = buildURL.build().toString();
    }

    public void checkCredentials(String username, String password) {

        Response.Listener<JSONObject> listener = jsonObject ->
                mainActivity.handleVerifyUserCredentialsSuccess(
                        jsonObject.optString("userName"),
                        jsonObject.optString("firstName"),
                        jsonObject.optString("lastName"));

        Response.ErrorListener error = volleyError -> {
            if (volleyError.networkResponse != null) {
                String s = new String(volleyError.networkResponse.data);
                Log.d(TAG, "onErrorResponse: " + s);
            }
            Log.d(TAG, "checkCredentials: " + volleyError.getMessage());
            mainActivity.handleVerifyUserCredentialsFail();
        };

        String s = "{\n" +
                "  \"userName\": \"" + username + "\",\n" +
                "  \"password\": \"" + password + "\"\n" +
                "}";
        JSONObject jsonParams;
        try {
            jsonParams = new JSONObject(s);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        // Request a string response from the provided URL.
        JsonObjectRequest jsonObjectRequest =
                new JsonObjectRequest(
                        Request.Method.PUT,
                        urlToUse,
                        jsonParams,
                        listener,
                        error);

        queue.add(jsonObjectRequest);
    }
}
