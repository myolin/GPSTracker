package com.myolin.followme.Activity;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
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
import com.myolin.followme.LocationServices.LocationService;
import com.myolin.followme.LocationServices.PointReceiver;
import com.myolin.followme.R;
import com.myolin.followme.Utils.NetworkChecker;
import com.myolin.followme.Volley.AddTripPointAPIVolley;
import com.myolin.followme.Volley.TripExistsForLeadAPIVolley;
import com.myolin.followme.databinding.ActivityTripLeadBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class TripLeadActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = "TripLeadActivity";

    public static final SimpleDateFormat sdf =
            new SimpleDateFormat("E MMM d, K:mm a", java.util.Locale.US);

    private GoogleMap mMap;
    private ActivityTripLeadBinding binding;
    private final float zoomDefault = 15.0f;
    private PointReceiver pointReceiver;
    private Intent locationServiceIntent;
    private Polyline llHistoryPolyline;
    private final ArrayList<LatLng> latLonHistory = new ArrayList<>();
    private Marker carMarker;
    private AddTripPointAPIVolley api;
    private String username = "";
    private String firstName = "";
    private String lastName = "";
    private String tripId = "";
    private ObjectAnimator objectAnimator1;
    private ObjectAnimator objectAnimator2;
    private ObjectAnimator objectAnimator3;
    private AnimatorSet animatorSet;
    private boolean isTracking = true;
    private Date startDateTime;
    private boolean showNetworkState = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTripLeadBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.title.getBackground().setAlpha(150);

        // Get data from intent extras
        Intent intent = getIntent();
        if (intent.hasExtra("TripId")) {
            tripId = intent.getStringExtra("TripId");
        }
        if (intent.hasExtra("UserName")) {
            username = intent.getStringExtra("UserName");
        }
        if (intent.hasExtra("FirstName")) {
            firstName = intent.getStringExtra("FirstName");
        }
        if (intent.hasExtra("LastName")) {
            lastName = intent.getStringExtra("LastName");
        }

        TripExistsForLeadAPIVolley tripExistsApi = new TripExistsForLeadAPIVolley(this);
        tripExistsApi.tripExists(tripId);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.animateCamera(CameraUpdateFactory.zoomTo(zoomDefault));
        mMap.setBuildingsEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);
    }

    public void initTrip() {
        // Start Location Service
        Toast.makeText(this, "SERVICE STARTED", Toast.LENGTH_SHORT).show();
        startLocationService();

        // Location Point Sender
        api = new AddTripPointAPIVolley(this);

        // Initialize SupportMapFragment
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapLead);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                backInvoked();
            }
        });

        // setup and start object animator
        binding.gps.setVisibility(View.VISIBLE);
        binding.gpsText.setVisibility(View.VISIBLE);
        objectAnimator1 =
                ObjectAnimator.ofFloat(binding.gps, "alpha", 1.0f, 0.25f);
        objectAnimator2 =
                ObjectAnimator.ofFloat(binding.gpsText, "alpha", 1.0f, 0.25f);
        objectAnimator3 =
                ObjectAnimator.ofFloat(binding.signal, "alpha", 1.0f, 0.25f);
        startObjectAnimators();

        // trip info window
        binding.tripId.setText(String.format(Locale.getDefault(), "Trip Id: %s", tripId));
        startDateTime = new Date();
        binding.tripStart.setText(String.format(Locale.getDefault(), "Trip Start: %s",
                sdf.format(startDateTime)));
    }

    public void tripExists() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Follow Me - Trip Exists");
        builder.setMessage("The Trip ID '" + tripId + "' already existed.\nPlease choose a different" +
                " trip Id");
        builder.setIcon(R.drawable.logo);
        builder.setPositiveButton("OK", ((dialog, which) -> finish()));
        builder.create().show();
    }

    public void tripNotExist() {
        initTrip();
    }

    public void tripExistsError(String s, String tripId) {
        if (!NetworkChecker.hasNetworkConnection(this)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Follow Me - No Network");
            builder.setMessage("No network connection - cannot verify Trip Id now\n\nStopping the " +
                    "trip now.");
            builder.setIcon(R.drawable.logo);
            builder.setPositiveButton("OK", ((dialog, which) -> finish()));
            builder.create().show();
        }
        Log.d(TAG, "tripExistsError: Trip Id: " + tripId + ", Error: " + s);
    }

    private void startLocationService() {
        // Create a receiver to get the location updates
        pointReceiver = new PointReceiver(this);

        // Register the receiver
        ContextCompat.registerReceiver(this, pointReceiver,
                new IntentFilter("com.example.broadcast.MY_BROADCAST"),
                ContextCompat.RECEIVER_EXPORTED);

        // Starting service
        locationServiceIntent = new Intent(this, LocationService.class);
        ContextCompat.startForegroundService(this, locationServiceIntent);
    }

    public void updateLocation(LatLng latLng, float bearing) {
        binding.gps.setVisibility(View.GONE);
        binding.gpsText.setVisibility(View.GONE);
        animatorSet.cancel();

        // AddTripPoint API
        Date date = new Date();
        if (isTracking) {
            api.sendPoint(tripId, latLng.latitude, latLng.longitude, date, username);
        }

        objectAnimator3.setDuration(1000);
        objectAnimator3.setRepeatCount(1);
        objectAnimator3.start();

        latLonHistory.add(latLng); // Add the LL to our location history

        if (llHistoryPolyline != null) {
            llHistoryPolyline.remove(); // Remove old polyline
        }

        if (latLonHistory.size() == 1) { // First update
            mMap.addMarker(new MarkerOptions().alpha(0.5f).position(latLng).title("My Origin"));
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoomDefault));
            return;
        }

        if (latLonHistory.size() > 1) { // Second (or more) update
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
        }
        mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
        calculateDistance();
        calculateTimeElapsed(date);
    }

    public void toggleTracking(View v) {
        if (isTracking) {
            isTracking = false;
            binding.pause.setVisibility(View.VISIBLE);
            binding.pauseBtn.setImageResource(R.drawable.play);
        } else {
            isTracking = true;
            binding.pause.setVisibility(View.GONE);
            binding.pauseBtn.setImageResource(R.drawable.pause);
        }
    }

    public void stopTrip(View v) {
        Toast.makeText(this, "SERVICE DESTROYED", Toast.LENGTH_SHORT).show();
        if (pointReceiver != null) {
            unregisterReceiver(pointReceiver);
        }
        stopService(locationServiceIntent);
        api.sendPoint(tripId, 0, 0, new Date(), username);
        finish();
    }

    private float getRadius() {
        float z = mMap.getCameraPosition().zoom;
        return 15f * z - 130f;
    }

    private void calculateDistance() {
        double sum = 0;
        LatLng last = latLonHistory.get(0);
        for (int i = 1; i < latLonHistory.size(); i++) {
            LatLng current = latLonHistory.get(i);
            sum += SphericalUtil.computeDistanceBetween(current, last);
            last = current;
        }
        binding.tripDistance.setText(String.format(Locale.getDefault(),
                "Distance: %.1f km", sum/1000.0));
    }

    private void calculateTimeElapsed(Date date) {
        int ms = (int) Math.abs(date.getTime() - startDateTime.getTime());
        int t = ms;
        int h = ms / 3600000;
        t -= (h * 3600000);
        int m = t / 60000;
        t -= (m * 60000);
        int s = t / 1000;
        binding.tripElapsed.setText(String.format(Locale.getDefault(),
                "Elapsed: %02d:%02d:%02d", h, m, s));
    }
    
    public void handleAddTripPointSuccess(String tripId, String latitude, String longitude,
                                          String datetime, String username) {
        String log = "Trip Id: " + tripId + ", Latitude: " + latitude + ", Longitude: " + longitude +
                ", DateTime: " + datetime + ", username: " + username;
        Log.d(TAG, "handleAddTripPointSuccess: " + log);
        if (showNetworkState) {
            showNetworkState = false;
            binding.leadNetworkState.setVisibility(View.GONE);
        }
    }
    
    public void handleAddTripPointFail(String s) {
        Log.d(TAG, "handleAddTripPointFail: " + s);
        showNetworkState = true;
        binding.leadNetworkState.setVisibility(View.VISIBLE);
    }
    
    public void backInvoked() {
        Log.d(TAG, "backInvoked: ");
        moveTaskToBack(true);
    }

    public void startObjectAnimators() {
        objectAnimator1.setDuration(750);
        objectAnimator1.setRepeatCount(ObjectAnimator.INFINITE);
        objectAnimator1.setRepeatMode(ObjectAnimator.REVERSE);

        objectAnimator2.setDuration(750);
        objectAnimator2.setRepeatCount(ObjectAnimator.INFINITE);
        objectAnimator2.setRepeatMode(ObjectAnimator.REVERSE);

        animatorSet = new AnimatorSet();
        animatorSet.playTogether(objectAnimator1, objectAnimator2);
        animatorSet.start();
    }

    public void doShare(View v) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, String.format(Locale.getDefault(),
                "Follow Me Trip Id: %s", tripId));
        sendIntent.putExtra(Intent.EXTRA_TEXT, String.format(Locale.getDefault(),
                "%s %s has shared a \"Follow Me\" Trip ID with you.\n\n" +
                        "Use Follow Me Trip ID : %s", firstName, lastName, tripId));
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, "Share Follow Me Trip ID to...");
        startActivity(shareIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: ");
        api.sendPoint(tripId, 0, 0, new Date(), username);
    }
}