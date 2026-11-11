package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.a524h0098_nguyenhoakhang_foodnow_project.databinding.ActivityRestaurantBinding;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.util.Locale;

public class RestaurantActivity extends AppCompatActivity {
    public static final String EXTRA_ID = "restaurant_id";
    private ActivityRestaurantBinding binding;
    private DocumentReference restaurant;
    private ListenerRegistration restaurantListener;
    private ListenerRegistration foodListener;
    private FoodAdapter foodAdapter;
    private boolean failed;
    private boolean restaurantFresh;
    private boolean menuFresh;
    private DocumentSnapshot restaurantSnapshot;
    private CartStore cart;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null || id.isEmpty() || id.contains("/")) {
            finish();
            return;
        }
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        binding = ActivityRestaurantBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        restaurant = FirebaseFirestore.getInstance().collection("restaurants").document(id);
        cart = new CartStore(this, uid);
        foodAdapter = new FoodAdapter(this::addFood);
        binding.menu.setLayoutManager(new LinearLayoutManager(this));
        binding.menu.setAdapter(foodAdapter);
        binding.back.setOnClickListener(view -> finish());
        binding.retry.setOnClickListener(view -> load());
        binding.viewCart.setOnClickListener(view -> showCart());
        updateCart();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (restaurant != null) {
            updateCart();
            load();
        }
    }

    @Override
    protected void onStop() {
        stopListeners();
        super.onStop();
    }

    private void stopListeners() {
        if (restaurantListener != null)
            restaurantListener.remove();
        if (foodListener != null)
            foodListener.remove();
        restaurantListener = null;
        foodListener = null;
    }

    private void load() {
        stopListeners();
        failed = false;
        restaurantFresh = false;
        menuFresh = false;
        restaurantSnapshot = null;
        foodAdapter.setOrderingAvailable(false);
        binding.progress.setVisibility(View.VISIBLE);
        binding.retry.setVisibility(View.GONE);
        binding.status.setText("");
        restaurantListener = restaurant.addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
            if (failed)
                return;
            if (error != null || snapshot == null) {
                showError();
                return;
            }
            if (!snapshot.exists()) {
                failed = true;
                binding.progress.setVisibility(View.GONE);
                binding.status.setText(R.string.restaurant_missing);
                return;
            }
            restaurantSnapshot = snapshot;
            restaurantFresh = !snapshot.getMetadata().isFromCache();
            updateOrdering();
            binding.name.setText(snapshot.getString("name"));
            binding.address.setText(snapshot.getString("address"));
            String open = snapshot.getString("openTime");
            String close = snapshot.getString("closeTime");
            binding.hours.setText(
                    getString(R.string.restaurant_hours, open == null ? "" : open, close == null ? "" : close));
            int image = CatalogImages.drawable(snapshot.getString("cover"));
            binding.cover.setVisibility(image == 0 ? View.GONE : View.VISIBLE);
            if (image != 0)
                binding.cover.setImageResource(image);
            RestaurantHours.Status status = RestaurantHours.getStatus(snapshot.getBoolean("active"), open, close,
                    snapshot.getString("timezone"), System.currentTimeMillis());
            int label = Boolean.FALSE.equals(snapshot.getBoolean("active")) ? R.string.restaurant_closed
                    : status == RestaurantHours.Status.OPEN ? R.string.restaurant_open_now
                            : status == RestaurantHours.Status.CLOSED ? R.string.restaurant_closed_now
                                    : R.string.restaurant_hours_unknown;
            binding.restaurantStatus.setText(label);
        });
        foodListener = restaurant.collection("foods").addSnapshotListener(MetadataChanges.INCLUDE,
                (snapshot, error) -> {
                    if (failed)
                        return;
                    if (error != null || snapshot == null) {
                        showError();
                        return;
                    }
                    menuFresh = !snapshot.getMetadata().isFromCache();
                    updateOrdering();
                    binding.progress.setVisibility(View.GONE);
                    foodAdapter.setFoods(snapshot.getDocuments());
                    if (snapshot.getMetadata().isFromCache()) {
                        binding.status
                                .setText(snapshot.isEmpty() ? R.string.catalog_cached_empty : R.string.catalog_cached);
                        binding.retry.setVisibility(View.VISIBLE);
                    } else {
                        binding.status.setText(snapshot.isEmpty() ? getString(R.string.menu_empty) : "");
                        binding.retry.setVisibility(View.GONE);
                    }
                });
    }

    private void showError() {
        failed = true;
        foodAdapter.setOrderingAvailable(false);
        binding.progress.setVisibility(View.GONE);
        binding.status.setText(R.string.catalog_error);
        binding.retry.setVisibility(View.VISIBLE);
    }

    private void updateOrdering() {
        boolean open = restaurantSnapshot != null && RestaurantHours.getStatus(restaurantSnapshot.getBoolean("active"),
                restaurantSnapshot.getString("openTime"), restaurantSnapshot.getString("closeTime"),
                restaurantSnapshot.getString("timezone"), System.currentTimeMillis()) == RestaurantHours.Status.OPEN;
        foodAdapter.setOrderingAvailable(!failed && restaurantFresh && menuFresh && open);
    }

    private void addFood(DocumentSnapshot food) {
        updateOrdering();
        if (!restaurantFresh || !menuFresh || restaurantSnapshot == null ||
                RestaurantHours.getStatus(restaurantSnapshot.getBoolean("active"),
                        restaurantSnapshot.getString("openTime"),
                        restaurantSnapshot.getString("closeTime"), restaurantSnapshot.getString("timezone"),
                        System.currentTimeMillis()) != RestaurantHours.Status.OPEN
                ||
                !Boolean.TRUE.equals(food.getBoolean("available")))
            return;
        String existing = cart.read().optString("restaurantId", "");
        if (!existing.isEmpty() && !existing.equals(restaurant.getId())) {
            new AlertDialog.Builder(this).setMessage(R.string.cart_replace).setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.confirm, (dialog, which) -> {
                        cart.clear();
                        saveFood(food);
                    }).show();
        } else
            saveFood(food);
    }

    private void saveFood(DocumentSnapshot food) {
        Long price = food.getLong("price");
        String name = food.getString("name");
        if (price == null || price <= 0 || name == null || name.isEmpty())
            return;
        try {
            cart.add(restaurant.getId(), food.getId(), name, price);
            updateCart();
        } catch (IllegalStateException error) {
            Toast.makeText(this, R.string.cart_limit, Toast.LENGTH_SHORT).show();
        } catch (JSONException error) {
            Toast.makeText(this, R.string.cart_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateCart() {
        JSONArray items = cart.read().optJSONArray("items");
        int count = 0;
        long total = 0;
        if (items != null)
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null)
                    continue;
                int quantity = item.optInt("quantity", 0);
                count += quantity;
                total += item.optLong("price", 0) * quantity;
            }
        binding.viewCart.setEnabled(count > 0);
        binding.viewCart.setText(count == 0 ? getString(R.string.cart_empty)
                : getString(R.string.cart_summary, count, formatMoney(total)));
    }

    private String formatMoney(long value) {
        return NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(value);
    }

    private void showCart() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(
                this);
        View sheet = getLayoutInflater().inflate(R.layout.sheet_cart, null);
        dialog.setContentView(sheet);
        ((android.widget.TextView) sheet.findViewById(R.id.cartRestaurant)).setText(binding.name.getText());
        sheet.findViewById(R.id.cartCheckout).setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(this, CheckoutActivity.class));
        });
        renderCart(sheet);
        dialog.setOnShowListener(ignored -> {
            android.widget.FrameLayout panel = dialog
                    .findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (panel != null) {
                panel.getLayoutParams().height = (int) (getResources().getDisplayMetrics().heightPixels * 0.85);
                panel.requestLayout();
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(panel)
                        .setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        dialog.show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void renderCart(View sheet) {
        android.widget.LinearLayout rows = sheet.findViewById(R.id.cartRows);
        rows.removeAllViews();
        JSONObject value = cart.read();
        JSONArray items = value.optJSONArray("items");
        long total = 0;
        if (items != null)
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null)
                    continue;
                int count = item.optInt("quantity");
                total += item.optLong("price") * count;
                android.widget.LinearLayout card = new android.widget.LinearLayout(this);
                card.setOrientation(android.widget.LinearLayout.VERTICAL);
                card.setPadding(dp(16), dp(16), dp(16), dp(12));
                card.setBackgroundResource(R.drawable.bg_surface);
                android.widget.LinearLayout.LayoutParams cardParams = new android.widget.LinearLayout.LayoutParams(-1,
                        -2);
                cardParams.bottomMargin = dp(12);
                rows.addView(card, cardParams);
                android.widget.TextView name = new android.widget.TextView(this);
                name.setText(getString(R.string.food_price_line, item.optString("name"),
                        formatMoney(item.optLong("price"))));
                name.setTextColor(getColor(R.color.ui_text));
                name.setTextSize(16);
                name.setLineSpacing(dp(4), 1);
                card.addView(name);
                android.widget.LinearLayout controls = new android.widget.LinearLayout(this);
                controls.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
                card.addView(controls);
                for (boolean increase : new boolean[] { false, true }) {
                    if (increase) {
                        android.widget.TextView quantity = new android.widget.TextView(this);
                        quantity.setText(String.valueOf(count));
                        quantity.setGravity(android.view.Gravity.CENTER);
                        quantity.setTextSize(17);
                        quantity.setTextColor(getColor(R.color.ui_text));
                        controls.addView(quantity, new android.widget.LinearLayout.LayoutParams(dp(44), dp(48)));
                    }
                    androidx.appcompat.widget.AppCompatImageButton button = new androidx.appcompat.widget.AppCompatImageButton(
                            this);
                    button.setImageResource(increase ? R.drawable.ic_ui_add : R.drawable.ic_ui_remove);
                    button.setBackgroundResource(R.drawable.bg_pill_green);
                    button.setPadding(dp(12), dp(12), dp(12), dp(12));
                    button.setContentDescription(
                            getString(increase ? R.string.ui_increase_quantity : R.string.ui_decrease_quantity,
                                    item.optString("name")));
                    button.setEnabled(!increase || count < 99);
                    controls.addView(button, new android.widget.LinearLayout.LayoutParams(dp(48), dp(48)));
                    button.setOnClickListener(v -> {
                        try {
                            if (increase)
                                cart.add(value.optString("restaurantId"), item.optString("id"), item.optString("name"),
                                        item.optLong("price"));
                            else
                                cart.removeOne(item.optString("id"));
                            updateCart();
                            renderCart(sheet);
                        } catch (JSONException | IllegalStateException error) {
                            Toast.makeText(this, R.string.cart_error, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        boolean empty = items == null || items.length() == 0;
        if (empty) {
            android.widget.TextView message = new android.widget.TextView(this);
            message.setText(R.string.cart_empty);
            message.setTextColor(getColor(R.color.ui_muted));
            rows.addView(message);
        }
        ((android.widget.TextView) sheet.findViewById(R.id.cartSubtotal))
                .setText(getString(R.string.ui_subtotal, formatMoney(total)));
        sheet.findViewById(R.id.cartCheckout).setEnabled(!empty);
    }
}
