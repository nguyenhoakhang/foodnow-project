package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import java.text.NumberFormat;
import java.util.Locale;

public class OrderFragment extends Fragment {
    private ListenerRegistration listener;
    private LinearLayout orderList;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setBackgroundColor(requireContext().getColor(R.color.ui_background));
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(18), dp(22), dp(24));
        scroll.addView(content);
        TextView eyebrow = text("FoodNow", 15, true);
        eyebrow.setLetterSpacing(0.12f);
        content.addView(eyebrow);
        TextView title = text(getString(R.string.ui_orders_heading), 30, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(5);
        content.addView(title, titleParams);
        TextView subtitle = text(getString(R.string.ui_orders_subtitle), 14, false);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(4);
        content.addView(subtitle, subParams);
        orderList = new LinearLayout(requireContext());
        orderList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, -2);
        listParams.topMargin = dp(22);
        content.addView(orderList, listParams);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            showMessage(getString(R.string.orders_login_required));
            return scroll;
        }
        showMessage(getString(R.string.orders_loading));
        listener = FirebaseFirestore.getInstance().collection("orders")
                .whereEqualTo("customerId", FirebaseAuth.getInstance().getUid())
                .addSnapshotListener((snap, error) -> {
                    if (!isAdded() || getView() == null)
                        return;
                    orderList.removeAllViews();
                    if (error != null) {
                        showMessage(getString(R.string.orders_load_failed));
                        return;
                    }
                    if (snap == null || snap.isEmpty()) {
                        showMessage(getString(R.string.orders_empty));
                        return;
                    }
                    java.util.List<DocumentSnapshot> documents = new java.util.ArrayList<>(snap.getDocuments());
                    documents.sort((left, right) -> {
                        com.google.firebase.Timestamp a = left.getTimestamp("createdAt");
                        com.google.firebase.Timestamp b = right.getTimestamp("createdAt");
                        return a == null || b == null ? 0 : b.compareTo(a);
                    });
                    for (DocumentSnapshot doc : documents)
                        addOrder(doc);
                });
        return scroll;
    }

    private void addOrder(DocumentSnapshot doc) {
        View card = getLayoutInflater().inflate(R.layout.item_order, orderList, false);
        String status = doc.getString("status");
        String shortId = doc.getId().substring(0, Math.min(8, doc.getId().length()));
        ((TextView) card.findViewById(R.id.orderId)).setText(getString(R.string.order_number, shortId));
        ((TextView) card.findViewById(R.id.orderStatus)).setText(statusLabel(status));
        TextView restaurant = card.findViewById(R.id.orderRestaurant);
        String restaurantName = doc.getString("restaurantName");
        if (restaurantName != null && !restaurantName.isEmpty()) {
            restaurant.setText(restaurantName);
            restaurant.setVisibility(View.VISIBLE);
        }
        Long total = doc.getLong("total");
        ((TextView) card.findViewById(R.id.orderAmount)).setText(
                NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(total == null ? 0 : total) + " ₫");
        String address = doc.getString("address");
        ((TextView) card.findViewById(R.id.orderAddress)).setText(getString(R.string.order_deliver_to,
                address == null ? getString(R.string.address_missing) : address));
        card.setContentDescription(getString(R.string.order_details_description, doc.getId()));
        card.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(requireContext(), OrdersActivity.class);
            intent.putExtra(OrdersActivity.EXTRA_HISTORY_ORDER, doc.getId());
            startActivity(intent);
        });
        orderList.addView(card);
    }

    private String statusLabel(String status) {
        if ("SEARCHING".equals(status))
            return getString(R.string.order_status_searching);
        if ("ACCEPTED".equals(status))
            return getString(R.string.order_status_accepted);
        if ("PICKED_UP".equals(status))
            return getString(R.string.order_status_picked_up);
        if ("DELIVERING".equals(status))
            return getString(R.string.order_status_delivering);
        if ("COMPLETED".equals(status))
            return getString(R.string.order_status_completed);
        if ("CANCELLED".equals(status))
            return getString(R.string.order_status_cancelled);
        return getString(R.string.status_updating);
    }

    private void showMessage(String message) {
        TextView empty = text(message, 14, false);
        empty.setBackgroundResource(R.drawable.bg_surface);
        empty.setPadding(dp(20), dp(22), dp(20), dp(22));
        orderList.addView(empty);
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(requireContext().getColor(bold ? R.color.ui_text : R.color.ui_muted));
        if (bold)
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onDestroyView() {
        if (listener != null)
            listener.remove();
        listener = null;
        orderList = null;
        super.onDestroyView();
    }
}
