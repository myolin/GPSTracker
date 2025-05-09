package com.myolin.followme.LocationServices;

import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;

import androidx.annotation.NonNull;

public class SvcLocListener implements LocationListener {

    private final LocationService locationService;

    public SvcLocListener(LocationService locationService) {
        this.locationService = locationService;
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        Intent intent = new Intent();
        intent.setAction("com.example.broadcast.MY_BROADCAST");
        intent.putExtra("LATITUDE", location.getLatitude());
        intent.putExtra("LONGITUDE", location.getLongitude());
        intent.putExtra("BEARING", location.getBearing());

        locationService.sendBroadcast(intent);
    }
}
