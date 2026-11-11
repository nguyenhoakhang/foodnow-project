package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

final class DriverLocationPublisher implements LocationListener {
    static final int PERMISSION_REQUEST = 73;
    private final Activity activity;
    private final String uid;
    private final LocationManager manager;
    private boolean running;
    private boolean permissionRequested;
    private boolean available = true;
    private Location lastLocation;

    DriverLocationPublisher(Activity activity, String uid) {
        this.activity = activity;
        this.uid = uid;
        manager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
    }

    void setAvailable(boolean value) {
        if (available == value)
            return;
        available = value;
        if (lastLocation != null)
            publish(lastLocation);
    }

    void start() {
        if (!hasPermission()) {
            if (!permissionRequested) {
                permissionRequested = true;
                ActivityCompat.requestPermissions(activity,
                        new String[] { Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION },
                        PERMISSION_REQUEST);
            }
            return;
        }
        if (running || manager == null)
            return;
        try {
            boolean started = false;
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 20000, 0, this, Looper.getMainLooper());
                publishLastKnown(LocationManager.GPS_PROVIDER);
                started = true;
            }
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 20000, 0, this,
                        Looper.getMainLooper());
                publishLastKnown(LocationManager.NETWORK_PROVIDER);
                started = true;
            }
            running = started;
        } catch (SecurityException | IllegalArgumentException error) {
            running = false;
        }
    }

    void stop() {
        if (!running)
            return;
        running = false;
        try {
            manager.removeUpdates(this);
        } catch (SecurityException ignored) {
        }
        if (lastLocation != null) {
            available = false;
            publish(lastLocation);
        }
        lastLocation = null;
    }

    static void clear(String uid) {
        if (uid != null)
            FirebaseFirestore.getInstance().collection("driverLocations").document(uid).delete();
    }

    private boolean hasPermission() {
        return ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(activity,
                        Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void publishLastKnown(String provider) {
        try {
            Location location = manager.getLastKnownLocation(provider);
            if (location != null)
                publish(location);
        } catch (SecurityException ignored) {
        }
    }

    private void publish(Location location) {
        if (Math.abs(System.currentTimeMillis() - location.getTime()) > 300000)
            return;
        lastLocation = location;
        Map<String, Object> data = new HashMap<>();
        data.put("latitude", location.getLatitude());
        data.put("longitude", location.getLongitude());
        data.put("available", available);
        data.put("updatedAt", FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance().collection("driverLocations").document(uid).set(data);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (running)
            publish(location);
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }
}
