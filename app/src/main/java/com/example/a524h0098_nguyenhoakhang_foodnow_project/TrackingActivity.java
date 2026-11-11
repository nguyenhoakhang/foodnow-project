package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class TrackingActivity extends AppCompatActivity {
    public static final String EXTRA_ORDER_ID = "orderId";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView map;
    private TextView status;
    private ListenerRegistration orderListener, driverListener;
    private String orderId, driverId, orderStatus;
    private boolean pageReady, geocoding;
    private String geocodedAddress;
    private Double destinationLatitude, destinationLongitude, driverLatitude, driverLongitude;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (FirebaseAuth.getInstance().getUid() == null) {
            finish();
            return;
        }
        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        if (orderId == null || orderId.isEmpty()) {
            finish();
            return;
        }
        androidx.activity.EdgeToEdge.enable(this);
        setContentView(R.layout.activity_tracking);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.trackingRoot),
                (view, insets) -> {
                    androidx.core.graphics.Insets bars = insets.getInsets(
                            androidx.core.view.WindowInsetsCompat.Type.systemBars()
                                    | androidx.core.view.WindowInsetsCompat.Type.ime());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
        status = findViewById(R.id.trackingStatus);
        map = findViewById(R.id.trackingMap);
        findViewById(R.id.trackingBack).setOnClickListener(view -> finish());
        map.getSettings().setJavaScriptEnabled(true);
        map.getSettings().setDomStorageEnabled(true);
        map.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                pushDestination();
                pushDriver();
            }
        });
        map.loadUrl("file:///android_asset/tracking.html");
        orderListener = db.collection("orders").document(orderId).addSnapshotListener((order, error) -> {
            if (error != null || order == null || !order.exists()) {
                status.setText(R.string.live_tracking_unavailable);
                return;
            }
            orderStatus = order.getString("status");
            status.setText(getString(R.string.tracking_status_line, order.getString("restaurantName"),
                    statusLabel(orderStatus)));
            Double latitude = order.getDouble("deliveryLatitude");
            Double longitude = order.getDouble("deliveryLongitude");
            if (valid(latitude, longitude)) {
                destinationLatitude = latitude;
                destinationLongitude = longitude;
                pushDestination();
            } else {
                geocode(order.getString("address"));
            }
            String newDriverId = "COMPLETED".equals(orderStatus) || "CANCELLED".equals(orderStatus) ? null
                    : order.getString("driverId");
            if (newDriverId == null) {
                if (driverListener != null) {
                    driverListener.remove();
                    driverListener = null;
                }
                driverId = null;
                driverLatitude = null;
                driverLongitude = null;
                if (map != null && pageReady)
                    map.evaluateJavascript("clearDriver()", null);
            } else if (!newDriverId.equals(driverId)) {
                driverId = newDriverId;
                watchDriver();
            }
        });
    }

    private boolean valid(Double latitude, Double longitude) {
        return latitude != null && longitude != null && (latitude != 0 || longitude != 0);
    }

    private void watchDriver() {
        if (driverListener != null)
            driverListener.remove();
        driverListener = db.collection("driverLocations").document(driverId).addSnapshotListener((location, error) -> {
            if (error != null || location == null || !location.exists())
                return;
            Double latitude = location.getDouble("latitude");
            Double longitude = location.getDouble("longitude");
            if (valid(latitude, longitude)) {
                driverLatitude = latitude;
                driverLongitude = longitude;
                pushDriver();
            }
        });
    }

    private void pushDestination() {
        if (map != null && pageReady && valid(destinationLatitude, destinationLongitude)) {
            map.evaluateJavascript("setDestination(" + destinationLatitude + "," + destinationLongitude + ")", null);
        }
    }

    private void pushDriver() {
        if (map == null)
            return;
        if (pageReady && valid(driverLatitude, driverLongitude)) {
            map.evaluateJavascript("updateDriver(" + driverLatitude + "," + driverLongitude + ")", null);
        } else if (pageReady) {
            map.evaluateJavascript(
                    "setMessage('" + getString(R.string.live_tracking_waiting).replace("'", "\\'") + "')", null);
        }
    }

    private void geocode(String address) {
        if (geocoding || (address != null && address.equals(geocodedAddress))
                || address == null || address.trim().isEmpty())
            return;
        geocoding = true;
        geocodedAddress = address;
        new Thread(() -> {
            HttpURLConnection connection = null;
            boolean resolved = false;
            try {
                String query = URLEncoder.encode(address, StandardCharsets.UTF_8.name());
                URL url = new URL("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + query);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("User-Agent", "FoodNow/1.0");
                if (connection.getResponseCode() != 200)
                    return;
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null)
                        body.append(line);
                }
                JSONArray results = new JSONArray(body.toString());
                if (results.length() == 0)
                    return;
                JSONObject first = results.getJSONObject(0);
                double latitude = Double.parseDouble(first.getString("lat"));
                double longitude = Double.parseDouble(first.getString("lon"));
                resolved = true;
                main.post(() -> {
                    destinationLatitude = latitude;
                    destinationLongitude = longitude;
                    pushDestination();
                });
            } catch (Exception ignored) {
            } finally {
                geocoding = false;
                if (!resolved)
                    geocodedAddress = null;
                if (connection != null)
                    connection.disconnect();
            }
        }).start();
    }

    private String statusLabel(String value) {
        if ("SEARCHING".equals(value))
            return getString(R.string.order_status_searching);
        if ("ACCEPTED".equals(value))
            return getString(R.string.order_status_accepted);
        if ("PICKED_UP".equals(value))
            return getString(R.string.order_status_picked_up);
        if ("DELIVERING".equals(value))
            return getString(R.string.order_status_delivering);
        if ("COMPLETED".equals(value))
            return getString(R.string.order_status_completed);
        if ("CANCELLED".equals(value))
            return getString(R.string.order_status_cancelled);
        return "";
    }

    @Override
    protected void onDestroy() {
        if (orderListener != null)
            orderListener.remove();
        if (driverListener != null)
            driverListener.remove();
        if (map != null) {
            map.destroy();
            map = null;
        }
        super.onDestroy();
    }
}
