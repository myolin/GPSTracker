package com.myolin.followme.Volley;

import android.net.Uri;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.myolin.followme.Activity.TripFollowerActivity;
import com.myolin.followme.LocationServices.LatLngTime;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;

public class GetTripPointsAPIVolley {

    private static final String dataUrl =
            "http://christopherhield-001-site4.htempurl.com/api/Datapoints/GetTrip";
    private static final String TAG = "GetTripPointsAPIVolley";
    public static final SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);

    private final RequestQueue queue;
    private final TripFollowerActivity tripFollowerActivity;

    public GetTripPointsAPIVolley(TripFollowerActivity tripFollowerActivity) {
        this.tripFollowerActivity = tripFollowerActivity;
        this.queue = Volley.newRequestQueue(tripFollowerActivity);
    }

    public void getPoints(String tripId) {
        Uri.Builder buildURL = Uri.parse(dataUrl).buildUpon();
        buildURL.appendPath(tripId);
        String urlToUse = buildURL.build().toString();

        Response.Listener<JSONArray> listener = results -> {
            Log.d(TAG, "onResponse: " + results);
            ArrayList<LatLngTime> points = new ArrayList<>();
            for (int i = 0; i < results.length(); i++) {
                JSONObject jo = results.optJSONObject(i);
                try {
                    double lat = jo.optDouble("latitude");
                    double lon = jo.optDouble("longitude");
                    String dateTime = jo.optString("datetime");
                    dateTime = dateTime.replace("T", " ");
                    points.add(new LatLngTime(lat, lon, sdf.parse(dateTime)));
                } catch (Exception e) {
                    Log.d(TAG, "getPoints: " + e.getMessage());
                }
            }
            tripFollowerActivity.acceptInitialPathPoints(points);
        };

        Response.ErrorListener error = volleyError -> {
            if (volleyError.networkResponse != null) {
                if (volleyError.networkResponse.statusCode == 404) {
                    tripFollowerActivity.tripNotFound(tripId);
                    return;
                }
                String s = new String(volleyError.networkResponse.data);
                Log.d(TAG, "onErrorResponse: " + s);
                tripFollowerActivity.getTripPointsError(s, tripId);
            }
            Log.d(TAG, "getPoints: " + volleyError.getMessage());
        };

        // Request a string response from the provided URL.
        JsonArrayRequest jsonArrayRequest =
                new JsonArrayRequest(
                        Request.Method.GET,
                        urlToUse,
                        null,
                        listener,
                        error);

        queue.add(jsonArrayRequest);
    }

}
