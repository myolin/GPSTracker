package com.myolin.followme.Volley;

import android.net.Uri;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.myolin.followme.Activity.TripLeadActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;

public class AddTripPointAPIVolley {

    private static final String dataUrl =
            "http://christopherhield-001-site4.htempurl.com/api/Datapoints/AddTripPoint";
    public static final SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
    private static final String TAG = "AddTripPointAPIVolley";

    private final TripLeadActivity tripLeadActivity;
    private final RequestQueue queue;
    private final String urlToUse;

    public AddTripPointAPIVolley(TripLeadActivity tripLeadActivity) {
        this.tripLeadActivity = tripLeadActivity;
        this.queue = Volley.newRequestQueue(tripLeadActivity);
        Uri.Builder buildURL = Uri.parse(dataUrl).buildUpon();
        urlToUse = buildURL.build().toString();
    }

    public void sendPoint(String tripId, double latitude, double longitude, Date datetime, String username) {
        Response.Listener<JSONObject> listener = jsonObject -> {
            String tripId1 = jsonObject.optString("tripId");
            String latitude1 = jsonObject.optString("latitude");
            String longitude1 = jsonObject.optString("longitude");
            String datetime1 = jsonObject.optString("datetime");
            String userName = jsonObject.optString("userName");
            tripLeadActivity.handleAddTripPointSuccess(tripId1, latitude1, longitude1, datetime1, userName);
        };

        Response.ErrorListener error = volleyError -> {
            Log.d(TAG, "onErrorResponse: " + volleyError.getMessage());

            String s = "";
            if (volleyError.networkResponse != null) {
                s = new String(volleyError.networkResponse.data);
                Log.d(TAG, "onErrorResponse: " + s);
            }
            tripLeadActivity.handleAddTripPointFail(s);
        };

        String t = sdf.format(datetime);

        String s = "{\n" +
                "  \"tripId\": \"" + tripId + "\",\n" +
                "  \"latitude\": " + latitude + ",\n" +
                "  \"longitude\": " + longitude + ",\n" +
                "  \"datetime\": \"" + t + "\",\n" +
                "  \"userName\": \"" + username + "\"\n" +
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
                        Request.Method.POST,
                        urlToUse,
                        jsonParams,
                        listener,
                        error);
        queue.add(jsonObjectRequest);
    }
}
