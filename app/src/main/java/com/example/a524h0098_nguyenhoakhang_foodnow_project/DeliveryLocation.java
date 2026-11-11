package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.Locale;

final class DeliveryLocation implements LocationListener {
    static final int PERMISSION_REQUEST = 71;

    interface Callback {
        void received(String location);
    }

    private final Activity activity;
    private final Callback callback;
    private final LocationManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean requestedPermission;
    private boolean waiting;
    private final Runnable timeout = () -> {
        stop();
        Toast.makeText(activityContext(), R.string.location_unavailable, Toast.LENGTH_LONG).show();
    };

    DeliveryLocation(Activity activity, Callback callback) {
        this.activity = activity;
        this.callback = callback;
        manager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
    }

    private Context activityContext() {
        return activity;
    }

    void request() {
        boolean fine = ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            if (!requestedPermission) {
                requestedPermission = true;
                ActivityCompat.requestPermissions(activity, new String[] { Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION }, PERMISSION_REQUEST);
            } else
                Toast.makeText(activity, R.string.location_permission, Toast.LENGTH_LONG).show();
            return;
        }
        stop();
        try {
            boolean started = false;
            if (fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this, Looper.getMainLooper());
                started = true;
            }
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, this, Looper.getMainLooper());
                started = true;
            }
            if (!started) {
                Toast.makeText(activity, R.string.location_unavailable, Toast.LENGTH_LONG).show();
                return;
            }
            waiting = true;
            handler.postDelayed(timeout, 20000);
            Toast.makeText(activity, R.string.location_loading, Toast.LENGTH_SHORT).show();
        } catch (SecurityException | IllegalArgumentException error) {
            stop();
            Toast.makeText(activity, R.string.location_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (!waiting || activity.isFinishing() || activity.isDestroyed())
            return;
        if (Math.abs(System.currentTimeMillis() - location.getTime()) > 120000)
            return;
        stop();
        callback.received(String.format(Locale.US, "%.6f, %.6f", location.getLatitude(), location.getLongitude()));
    }

    void stop() {
        waiting = false;
        handler.removeCallbacks(timeout);
        try {
            manager.removeUpdates(this);
        } catch (SecurityException ignored) {
        }
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
