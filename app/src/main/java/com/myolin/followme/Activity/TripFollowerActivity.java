package com.myolin.followme.Activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.maps.android.SphericalUtil;
import com.myolin.followme.LocationServices.LatLngTime;
import com.myolin.followme.R;
import com.myolin.followme.Utils.NetworkChecker;
import com.myolin.followme.Volley.GetLastLocationAPIVolley;
import com.myolin.followme.Volley.GetTripPointsAPIVolley;
import com.myolin.followme.Volley.TripExistsAPIVolley;
import com.myolin.followme.databinding.ActivityTripFollowerBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class TripFollowerActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = "TripFollowerActivity";

    public static final SimpleDateFormat sdf =
            new SimpleDateFormat("E MMM d, K:mm a", java.util.Locale.US);

    private GoogleMap mMap;
    private ActivityTripFollowerBinding binding;
    private String tripId;
    private final float zoomDefault = 15.0f;
    private Polyline llHistoryPolyline;
    private final ArrayList<LatLng> latLonHistory = new ArrayList<>();
    private Marker carMarker;
    private GetLastLocationAPIVolley lastPointGetter;
    private boolean running = true;
    private ObjectAnimator objectAnimator1;
    private boolean isCentered = true;
    private Date startDateTime;
    private boolean isNetworkAvailable = true;
    private boolean isNetworkFirstCheck = true; // first network check flag on activity start
    private Thread getLastPointThread;
    private boolean showTripEndDialog = false;
    private float prevBearing = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTripFollowerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupNetworkCallback();
        binding.title.getBackground().setAlpha(150);

        binding.progressBar2.setVisibility(View.VISIBLE);

        Intent intent = getIntent();
        if (intent.hasExtra("TripId")) {
            tripId = intent.getStringExtra("TripId");
        }

        TripExistsAPIVolley tripExistsApi = new TripExistsAPIVolley(this);
        tripExistsApi.tripExists(tripId);

        binding.followTripId.setText(String.format(Locale.getDefault(), "Trip Id: %s", tripId));

        // setup object animator
        objectAnimator1 =
                ObjectAnimator.ofFloat(binding.followSignal, "alpha", 1.0f, 0.25f);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.animateCamera(CameraUpdateFactory.zoomTo(zoomDefault));
        mMap.setBuildingsEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);
    }

    public void tripExists() {
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFollower);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // let map load first and getPoints 2 seconds later
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            GetTripPointsAPIVolley getTripPointsApi =
                    new GetTripPointsAPIVolley(TripFollowerActivity.this);
            getTripPointsApi.getPoints(tripId);
        }, 2000);

    }

    public void tripNotExist() {
        binding.progressBar2.setVisibility(View.GONE);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_title));
        builder.setMessage("The Trip ID '" + tripId + "' was not found.");
        builder.setIcon(R.drawable.logo);
        builder.setPositiveButton("OK", ((dialog, which) -> finish()));
        builder.create().show();
    }

    public void tripExistsError(String s, String tripId) {
        binding.progressBar2.setVisibility(View.GONE);

        if (NetworkChecker.hasNetworkConnection(this)) {
            Toast.makeText(this, "Trip Exists Error", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "tripExistsError: Trip Id: " + tripId + ", Error: " + s);
            finish();
        } else {
            Log.d(TAG, "tripExistsError: no network");
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Follow Me - No Network");
            builder.setMessage("No Network Connection - cannot access trip data now\n\nCannot" +
                    "follow the trip now.");
            builder.setIcon(R.drawable.logo);
            builder.setPositiveButton("OK", ((dialog, which) -> finish()));
            builder.create().show();
        }

    }

    public void acceptInitialPathPoints(ArrayList<LatLngTime> points) {
        binding.progressBar2.setVisibility(View.GONE);
        if (!points.isEmpty()) {
            int last = points.size() - 1;
            LatLng lastPoint = points.get(last).getLatLng();
            if (lastPoint.latitude == 0 && lastPoint.longitude == 0) {
                points.remove(last);
                running = false;
                tripEnded();
            }
        }

        if (!points.isEmpty()) {
            startDateTime = points.get(0).getDateTime();
            binding.followTripStart.setText(String.format(Locale.getDefault(),
                    "Trip Start: %s", sdf.format(startDateTime)));

            // ADD the LL to our location history
            for (LatLngTime llt : points) {
                latLonHistory.add(llt.getLatLng());
            }

            calculateDistance();
            calculateTimeElapsed(points.get(points.size() - 1).getDateTime());

            PolylineOptions polylineOptions = new PolylineOptions();
            for (LatLng ll : latLonHistory) {
                polylineOptions.add(ll);
            }
            llHistoryPolyline = mMap.addPolyline(polylineOptions);
            llHistoryPolyline.setEndCap(new RoundCap());
            llHistoryPolyline.setWidth(12);
            llHistoryPolyline.setColor(Color.BLUE);

            LatLng lastLL = latLonHistory.get(latLonHistory.size() - 1);
            float r = getRadius();
            if (r > 0) {
                Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.car);
                Bitmap resized = Bitmap.createScaledBitmap(icon, (int) r, (int) r, false);
                BitmapDescriptor iconBitmap = BitmapDescriptorFactory.fromBitmap(resized);

                MarkerOptions options = new MarkerOptions();
                options.position(lastLL);
                options.icon(iconBitmap);

                carMarker = mMap.addMarker(options);
            }
            mMap.animateCamera(CameraUpdateFactory.newLatLng(lastLL));
        }

        getLastPointThread = new Thread(new JoinDataRunnable());
        getLastPointThread.start();
    }

    public void tripNotFound(String tripId) {
        Toast.makeText(this, "Trip Id " + tripId + " Not Found", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "tripNotFound: Trip Id " + tripId + " not found");
        finish();
    }

    public void getTripPointsError(String s, String tripId) {
        Toast.makeText(this, "Get Trip Points Error", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "getTripPointsError: Trip Id: " + tripId + ", Error: " + s);
        finish();
    }

    public void handleLastLocationSuccess(LatLngTime llt) {
        objectAnimator1.setDuration(1000);
        objectAnimator1.setRepeatCount(1);
        objectAnimator1.start();

        LatLng latLng = llt.getLatLng();
        Date dateTime = llt.getDateTime();

        if (latLng.latitude == 0 && latLng.longitude == 0) {
            running = false;
            if (!showTripEndDialog) {
                tripEnded();
            }
            return;
        }

        LatLng lastPoint = latLonHistory.get(latLonHistory.size() - 1);
        float bearing = getBearing(lastPoint.latitude, lastPoint.longitude, latLng.latitude, latLng.longitude);

        if (bearing != 0) {
            prevBearing = bearing;
        } else {
            bearing = prevBearing;
        }

        latLonHistory.add(latLng);

        if (llHistoryPolyline != null) {
            llHistoryPolyline.remove();
        }

        PolylineOptions polylineOptions = new PolylineOptions();
        for (LatLng ll : latLonHistory) {
            polylineOptions.add(ll);
        }
        llHistoryPolyline = mMap.addPolyline(polylineOptions);
        llHistoryPolyline.setEndCap(new RoundCap());
        llHistoryPolyline.setWidth(12);
        llHistoryPolyline.setColor(Color.BLUE);

        float r = getRadius();
        if (r > 0) {
            Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.car);
            Bitmap resized = Bitmap.createScaledBitmap(icon, (int) r, (int) r, false);
            BitmapDescriptor iconBitmap = BitmapDescriptorFactory.fromBitmap(resized);

            MarkerOptions options = new MarkerOptions();
            options.position(latLng);
            options.icon(iconBitmap);
            options.rotation(bearing);

            if (carMarker != null) {
                carMarker.remove();
            }

            carMarker = mMap.addMarker(options);
        }

        if (isCentered) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoomDefault));
        }

        calculateDistance();
        calculateTimeElapsed(dateTime);
    }

    private void calculateDistance() {
        double sum = 0;
        LatLng last = latLonHistory.get(0);
        for (int i = 1; i < latLonHistory.size(); i++) {
            LatLng current = latLonHistory.get(i);
            sum += SphericalUtil.computeDistanceBetween(current, last);
            last = current;
        }
        binding.followTripDistance.setText(String.format(Locale.getDefault(),
                "Distance: %.1f km", sum/1000.0));
    }

    private void calculateTimeElapsed(Date dateTime) {
        int ms = (int) Math.abs(dateTime.getTime() - startDateTime.getTime());
        int t = ms;
        int h = ms / 3600000;
        t -= (h * 3600000);
        int m = t / 60000;
        t -= (m * 60000);
        int s = t / 1000;
        binding.followTripElapsed.setText(String.format(Locale.getDefault(),
                "Elapsed: %02d:%02d:%02d", h, m, s));
    }

    public void handleLastLocationFail(String message, String tripId) {
        //Toast.makeText(this, "Get Last Location Fail", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "handleLastLocationFail: Trip Id: " + tripId + ", Error: " + message);
    }

    private float getRadius() {
        float z = mMap.getCameraPosition().zoom;
        return 15f * z - 130f;
    }

    private void tripEnded() {
        showTripEndDialog = true;
        binding.titleText.setText(getString(R.string.trip_ended));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Trip Ended");
        builder.setIcon(R.drawable.logo);
        builder.setMessage("The trip has ended.");
        builder.setPositiveButton("OK", null);
        builder.create().show();
    }

    public void centerLocation(View v) {
        if (isCentered) {
            isCentered = false;
            binding.target.setAlpha(0.35f);
        } else {
            isCentered = true;
            binding.target.setAlpha(1f);
        }
    }

    private float getBearing(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1);
        double lat2Rad = Math.toRadians(lat2);
        double lon2Rad = Math.toRadians(lon2);

        // calculate difference in longitude
        double deltaLon = lon2Rad - lon1Rad;

        // Calculate the bearing using the formula
        double y = Math.sin(deltaLon) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
                Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLon);
        double bearingRad = Math.atan2(y, x);

        // Convert the bearing from radians to degrees
        double bearingDeg = Math.toDegrees(bearingRad);

        double degrees = (bearingDeg + 360) % 360;

        return (float) degrees;
    }

    private void setupNetworkCallback() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        connectivityManager.registerNetworkCallback(
                builder.build(),
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        super.onAvailable(network);

                        if (isNetworkFirstCheck) {
                            isNetworkFirstCheck = false; // ignore activity start network check
                            return;
                        }

                        if (!isNetworkAvailable) { // only do this if network was previously available
                            isNetworkAvailable = true;
                            runOnUiThread(() ->
                                    binding.followerNetworkState.setVisibility(View.GONE));
                            getLastPointThread = new Thread(new JoinDataRunnable());
                            getLastPointThread.start();
                            Log.d(TAG, "onAvailable: network available");
                        }
                    }

                    @Override
                    public void onLost(@NonNull Network network) {
                        super.onLost(network);
                        if (isNetworkAvailable) {
                            isNetworkAvailable = false;
                            runOnUiThread(() ->
                                binding.followerNetworkState.setVisibility(View.VISIBLE));
                            Log.d(TAG, "onLost: network lost");

                            new Thread(() -> {
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    Log.d(TAG, "onLost: Thread Interrupted");
                                    return;
                                }
                                if (NetworkChecker.hasNetworkConnection(TripFollowerActivity.this)) {
                                    runOnUiThread(() ->
                                            binding.followerNetworkState.setVisibility(View.GONE));
                                    isNetworkAvailable = true;
                                    Log.d(TAG, "onLost: network is available. false positive.");
                                }
                            }).start();
                        }
                    }
                }
        );
    }

    ////////////////////////////////////////////////////////////////////////

    class JoinDataRunnable implements Runnable {

        private static final String TAG = "JoinDataThread";

        public JoinDataRunnable() {
            lastPointGetter = new GetLastLocationAPIVolley(TripFollowerActivity.this);
        }

        /** @noinspection BusyWait*/
        @Override
        public void run() {
            while (running) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.d(TAG, "run: Thread Interrupted");
                    return;
                }

                Log.d(TAG, "run: Getting last point");
                if (!NetworkChecker.hasNetworkConnection(TripFollowerActivity.this)) {
                    return;
                }

                lastPointGetter.getLastLocation(tripId);
            }
        }
    }
}

