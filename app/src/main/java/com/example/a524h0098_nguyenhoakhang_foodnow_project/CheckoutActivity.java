package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.a524h0098_nguyenhoakhang_foodnow_project.databinding.ActivityCheckoutBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CheckoutActivity extends AppCompatActivity {
    private ActivityCheckoutBinding binding;
    private CartStore cart;
    private String uid;
    private Long fee;
    private SharedPreferences preferences;
    private DeliveryLocation deliveryLocation;
    private Double latitude, longitude;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        binding = ActivityCheckoutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.checkoutRoot, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        cart = new CartStore(this, uid);
        preferences = getSharedPreferences("foodnow_checkout", MODE_PRIVATE);
        deliveryLocation = new DeliveryLocation(this, location -> {
            binding.address.setText(location);
            binding.checkoutMessage.setText(R.string.location_review);
            String[] parts = location.split(",");
            if (parts.length == 2) {
                try {
                    latitude = Double.parseDouble(parts[0].trim());
                    longitude = Double.parseDouble(parts[1].trim());
                } catch (NumberFormatException error) {
                    latitude = null;
                    longitude = null;
                }
            }
        });
        binding.useLocation.setOnClickListener(view -> deliveryLocation.request());
        binding.address.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable text) {
                latitude = null;
                longitude = null;
            }
        });
        JSONArray items = cart.read().optJSONArray("items");
        if (items == null || items.length() == 0 || items.length() > 4) {
            binding.checkoutMessage.setText(R.string.checkout_invalid);
            return;
        }
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null)
                lines.append(item.optString("name")).append(" × ").append(item.optInt("quantity"))
                        .append(" — ").append(money(item.optLong("price") * item.optInt("quantity"))).append('\n');
        }
        binding.checkoutItems.setText(lines.toString());
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.document("appConfig/public").get(Source.SERVER).addOnCompleteListener(this, task -> {
            if (task.isSuccessful() && task.getResult().getLong("deliveryFee") != null) {
                fee = task.getResult().getLong("deliveryFee");
                binding.checkoutFee.setText(getString(R.string.delivery_fee, money(fee)));
                long subtotal = 0;
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item != null)
                        subtotal += item.optLong("price") * item.optInt("quantity");
                }
                binding.checkoutTotal.setText(getString(R.string.checkout_total, money(subtotal + fee)));
                binding.placeOrder.setEnabled(true);
            } else
                binding.checkoutMessage.setText(R.string.checkout_error);
        });
        db.collection("users").document(uid).get(Source.SERVER).addOnSuccessListener(this, profile -> {
            if (profile.exists()) {
                if (binding.recipient.getText().length() == 0)
                    binding.recipient.setText(profile.getString("name"));
                if (binding.phone.getText().length() == 0)
                    binding.phone.setText(profile.getString("phone"));
                if (binding.address.getText().length() == 0)
                    binding.address.setText(profile.getString("address"));
            }
        });
        binding.placeOrder.setOnClickListener(view -> submit());
    }

    private String money(long value) {
        return NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(value);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == DeliveryLocation.PERMISSION_REQUEST)
            deliveryLocation.request();
    }

    @Override
    protected void onDestroy() {
        if (deliveryLocation != null)
            deliveryLocation.stop();
        super.onDestroy();
    }

    private void submit() {
        String recipient = binding.recipient.getText().toString().trim();
        String phone = binding.phone.getText().toString().trim();
        String address = binding.address.getText().toString().trim();
        JSONObject saved = cart.read();
        JSONArray raw = saved.optJSONArray("items");
        if (fee == null || recipient.isEmpty() || phone.isEmpty() || address.isEmpty() || raw == null
                || raw.length() == 0 || raw.length() > 4) {
            binding.checkoutMessage.setText(R.string.checkout_invalid);
            return;
        }
        List<OrderRepository.Item> items = new ArrayList<>();
        for (int i = 0; i < raw.length(); i++) {
            JSONObject item = raw.optJSONObject(i);
            if (item == null) {
                binding.checkoutMessage.setText(R.string.checkout_invalid);
                return;
            }
            items.add(new OrderRepository.Item(item.optString("id"), item.optInt("quantity"), item.optLong("price")));
        }
        final String restaurantId = saved.optString("restaurantId");
        final String note = binding.note.getText().toString();
        binding.placeOrder.setEnabled(false);
        binding.checkoutProgress.setVisibility(View.VISIBLE);
        findNearestDriver(
                targetDriverId -> createOrder(targetDriverId, restaurantId, items, recipient, phone, address, note));
    }

    private void findNearestDriver(java.util.function.Consumer<String> callback) {
        if (latitude == null || longitude == null) {
            geocode(binding.address.getText().toString().trim(), () -> queryNearestDriver(callback));
            return;
        }
        queryNearestDriver(callback);
    }

    private void geocode(String address, Runnable done) {
        if (address == null || address.isEmpty()) {
            done.run();
            return;
        }
        new Thread(() -> {
            java.net.HttpURLConnection connection = null;
            try {
                String query = java.net.URLEncoder.encode(address, java.nio.charset.StandardCharsets.UTF_8.name());
                java.net.URL url = new java.net.URL(
                        "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + query);
                connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("User-Agent", "FoodNow/1.0");
                if (connection.getResponseCode() != 200)
                    throw new java.io.IOException("geocode failed");
                StringBuilder body = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null)
                        body.append(line);
                }
                org.json.JSONArray results = new org.json.JSONArray(body.toString());
                if (results.length() > 0) {
                    org.json.JSONObject first = results.getJSONObject(0);
                    double foundLatitude = Double.parseDouble(first.getString("lat"));
                    double foundLongitude = Double.parseDouble(first.getString("lon"));
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed())
                            return;
                        latitude = foundLatitude;
                        longitude = foundLongitude;
                        done.run();
                    });
                    return;
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null)
                    connection.disconnect();
            }
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed())
                    done.run();
            });
        }).start();
    }

    private void queryNearestDriver(java.util.function.Consumer<String> callback) {
        if (latitude == null || longitude == null) {
            callback.accept("");
            return;
        }
        FirebaseFirestore.getInstance().collection("driverLocations").get(Source.SERVER).addOnCompleteListener(this,
                task -> {
                    String nearest = "";
                    double best = Double.MAX_VALUE;
                    if (task.isSuccessful()) {
                        long now = System.currentTimeMillis();
                        for (com.google.firebase.firestore.DocumentSnapshot driver : task.getResult().getDocuments()) {
                            if (!Boolean.TRUE.equals(driver.getBoolean("available")))
                                continue;
                            Timestamp updated = driver.getTimestamp("updatedAt");
                            if (updated == null || now - updated.toDate().getTime() > 120000L)
                                continue;
                            Double driverLatitude = driver.getDouble("latitude");
                            Double driverLongitude = driver.getDouble("longitude");
                            if (driverLatitude == null || driverLongitude == null)
                                continue;
                            double distance = distance(latitude, longitude, driverLatitude, driverLongitude);
                            if (distance < best) {
                                best = distance;
                                nearest = driver.getId();
                            }
                        }
                    }
                    callback.accept(nearest);
                });
    }

    private double distance(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
        double latitudeDelta = Math.toRadians(toLatitude - fromLatitude);
        double longitudeDelta = Math.toRadians(toLongitude - fromLongitude);
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(fromLatitude)) * Math.cos(Math.toRadians(toLatitude))
                        * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void createOrder(String targetDriverId, String restaurantId, List<OrderRepository.Item> items,
            String recipient, String phone, String address, String note) {
        String key = "pending_" + uid;
        String orderId = preferences.getString(key, null);
        if (orderId == null) {
            orderId = UUID.randomUUID().toString();
            if (!preferences.edit().putString(key, orderId).commit()) {
                binding.checkoutProgress.setVisibility(View.GONE);
                binding.placeOrder.setEnabled(true);
                binding.checkoutMessage.setText(R.string.checkout_error);
                return;
            }
        }
        new OrderRepository().create(uid, orderId, restaurantId, items, fee, recipient, phone, address, note,
                latitude == null ? 0 : latitude, longitude == null ? 0 : longitude, targetDriverId)
                .addOnCompleteListener(this, task -> {
                    binding.checkoutProgress.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        cart.clear();
                        preferences.edit().remove(key).apply();
                        showSuccess();
                    } else {
                        String message = task.getException() == null ? ""
                                : String.valueOf(task.getException().getMessage());
                        if (message.contains("Previous order"))
                            preferences.edit().remove(key).apply();
                        binding.placeOrder.setEnabled(true);
                        binding.checkoutMessage.setText(R.string.checkout_error);
                    }
                });
    }

    private void showSuccess() {
        View content = getLayoutInflater().inflate(R.layout.dialog_order_success, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(content)
                .setCancelable(false)
                .create();
        content.findViewById(R.id.closeSuccess).setOnClickListener(view -> {
            dialog.dismiss();
            startActivity(new android.content.Intent(this, OrdersActivity.class));
            finish();
        });
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
        });
        dialog.show();
    }
}
