package com.myolin.followme.Volley;

import android.net.Uri;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.myolin.followme.Activity.TripFollowerActivity;

public class TripExistsAPIVolley {

    private static final String dataUrl =
            "http://christopherhield-001-site4.htempurl.com/api/Datapoints/TripExists";

    private static final String TAG = "TripExistsAPIVolley";

    private final RequestQueue queue;
    private final TripFollowerActivity tripFollowerActivity;

    public TripExistsAPIVolley(TripFollowerActivity tripFollowerActivity) {
        this.tripFollowerActivity = tripFollowerActivity;
        this.queue = Volley.newRequestQueue(tripFollowerActivity);
    }

    public void tripExists(String tripId) {
        Uri.Builder buildURL = Uri.parse(dataUrl).buildUpon();
        buildURL.appendPath(tripId);
        String urlToUse = buildURL.build().toString();

        Response.Listener<String> listener = results -> {
            Log.d(TAG, "onResponse: " + results);
            boolean exists = Boolean.parseBoolean(results);
            if (exists) {
                tripFollowerActivity.tripExists();
            } else {
                tripFollowerActivity.tripNotExist();
            }
        };

        Response.ErrorListener error = volleyError -> {
            String s = volleyError.getMessage();
            if (volleyError.networkResponse != null) {
                s += new String(volleyError.networkResponse.data);
            }
            Log.d(TAG, "tripExists: " + s);
            tripFollowerActivity.tripExistsError(s, tripId);
        };

        // Request a string response from the provided URL.
        StringRequest stringRequest =
                new StringRequest(
                        Request.Method.GET,
                        urlToUse,
                        listener,
                        error);

        queue.add(stringRequest);
    }
}
