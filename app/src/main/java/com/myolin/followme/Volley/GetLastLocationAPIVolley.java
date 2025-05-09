package com.myolin.followme.Volley;

import android.net.Uri;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.myolin.followme.Activity.TripFollowerActivity;
import com.myolin.followme.LocationServices.LatLngTime;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;

public class GetLastLocationAPIVolley {

    private static final String dataUrl =
            "http://christopherhield-001-site4.htempurl.com/api/Datapoints/GetLastLocation";
    private static final String TAG = "GetLastLocationAPIVolley";
    public static final SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);

    private final RequestQueue queue;
    private final TripFollowerActivity tripFollowerActivity;

    public GetLastLocationAPIVolley(TripFollowerActivity tripFollowerActivity) {
        this.tripFollowerActivity = tripFollowerActivity;
        this.queue = Volley.newRequestQueue(tripFollowerActivity);
    }

    public void getLastLocation(String tripId) {
        Uri.Builder buildURL = Uri.parse(dataUrl).buildUpon();
        buildURL.appendPath(tripId);
        String urlToUse = buildURL.build().toString();

        Response.Listener<JSONObject> listener = results -> {
            Log.d(TAG, "onResponse: " + results);
            try {
                double lat = results.optDouble("latitude");
                double lon = results.optDouble("longitude");
                Date d = sdf.parse(results.optString("datetime"));

                LatLngTime llt = new LatLngTime(lat, lon, d);

                tripFollowerActivity.handleLastLocationSuccess(llt);
            } catch (Exception e) {
                Log.d(TAG, "onResponse: " + e.getMessage());
                tripFollowerActivity.handleLastLocationFail(e.getMessage(), tripId);
            }
        };

        Response.ErrorListener error = volleyError -> {
            if (volleyError.networkResponse != null) {
                String s = new String(volleyError.networkResponse.data);
                Log.d(TAG, "onErrorResponse: " + s);
            }
            tripFollowerActivity.handleLastLocationFail(volleyError.getMessage(), tripId);
        };

        // Request a string response from the provided URL.
        JsonObjectRequest jsonObjectRequest =
                new JsonObjectRequest(
                        Request.Method.GET,
                        urlToUse,
                        null,
                        listener,
                        error);

        queue.add(jsonObjectRequest);
    }
}
