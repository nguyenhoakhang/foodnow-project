package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.a524h0098_nguyenhoakhang_foodnow_project.databinding.ActivityOrdersBinding;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Source;
import java.util.List;
import java.util.UUID;

public class OrdersActivity extends AppCompatActivity {
    public static final String EXTRA_HISTORY_ORDER = "historyOrder";
    private ActivityOrdersBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final OrderRepository orders = new OrderRepository();
    private ListenerRegistration profileListener, driverListener, orderListener, chatListener;
    private ListenerRegistration offersListener;
    private String uid, role, activeOrder, historyOrder;
    private String chatOrder, pendingMessageId, pendingMessageText;
    private String recipientPhone;
    private String deliveryAddress;
    private boolean approved, available;
    private long chatLimit = 50;
    private DriverLocationPublisher locationPublisher;
    private List<DocumentSnapshot> lastOffers = new java.util.ArrayList<>();
    private boolean lastOffersCached;
    private final android.os.Handler offersHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable offersRefresh = () -> renderOffers();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        uid = FirebaseAuth.getInstance().getUid();
        historyOrder = getIntent().getStringExtra(EXTRA_HISTORY_ORDER);
        if (uid == null) {
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        binding = ActivityOrdersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        NotificationHelper.register(this);
        ViewCompat.setOnApplyWindowInsetsListener(binding.ordersRoot, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        binding.orderAvailability.setOnClickListener(view -> run(orders.setAvailability(uid, !available)));
        binding.driverNavOrders.setOnClickListener(view -> {
            if (activeOrder == null)
                showOffers();
        });
        binding.driverNavHistory.setOnClickListener(view -> showHistory());
        binding.driverNavProfile.setOnClickListener(view -> startActivity(new Intent(this, ProfileActivity.class)));
        binding.orderOffers.setOnClickListener(view -> showOffers());
        binding.chatSend.setOnClickListener(view -> sendMessage());
        binding.chatOlder.setOnClickListener(view -> {
            if (chatOrder == null)
                return;
            String id = chatOrder;
            chatLimit += 50;
            if (chatListener != null)
                chatListener.remove();
            chatListener = null;
            watchChat(id);
        });
        binding.orderCall.setOnClickListener(view -> {
            if (recipientPhone != null && !recipientPhone.isEmpty()) {
                startActivity(new Intent(Intent.ACTION_DIAL, android.net.Uri.fromParts("tel", recipientPhone, null)));
            }
        });
        binding.orderMap.setOnClickListener(view -> {
            if (deliveryAddress == null || deliveryAddress.isEmpty())
                return;
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(deliveryAddress)));
            try {
                startActivity(intent);
            } catch (android.content.ActivityNotFoundException error) {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                        "https://www.google.com/maps/search/?api=1&query=" + android.net.Uri.encode(deliveryAddress))));
            }
        });
        binding.orderTrack.setOnClickListener(view -> {
            if (activeOrder == null)
                return;
            Intent intent = new Intent(this, TrackingActivity.class);
            intent.putExtra(TrackingActivity.EXTRA_ORDER_ID, activeOrder);
            startActivity(intent);
        });
        if (state != null) {
            pendingMessageId = state.getString("pendingMessageId");
            pendingMessageText = state.getString("pendingMessageText");
        }
        binding.orderAdvance.setOnClickListener(view -> {
            if (activeOrder != null)
                new AlertDialog.Builder(this).setMessage(binding.orderAdvance.getText())
                        .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.confirm,
                                (dialog, which) -> run(orders.advance(uid, activeOrder)))
                        .show();
        });
        binding.orderRelease.setOnClickListener(view -> {
            if (activeOrder != null)
                new AlertDialog.Builder(this).setMessage(R.string.release_order)
                        .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.confirm,
                                (dialog, which) -> run(orders.release(uid, activeOrder)))
                        .show();
        });
        binding.orderCancel.setOnClickListener(view -> {
            if (activeOrder == null)
                return;
            String message = "driver".equals(role) ? getString(R.string.driver_cancel_confirm)
                    : getString(R.string.cancel_order);
            new AlertDialog.Builder(this).setMessage(message)
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.confirm,
                            (dialog, which) -> run("driver".equals(role)
                                    ? orders.cancelByDriver(uid, activeOrder)
                                    : orders.cancelSearching(uid, activeOrder)))
                    .show();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (uid == null || binding == null)
            return;
        profileListener = db.collection("users").document(uid).addSnapshotListener((profile, error) -> {
            if (error != null || profile == null || !profile.exists()) {
                showError();
                return;
            }
            role = profile.getString("role");
            boolean driverRole = "driver".equals(role);
            binding.driverNav.setVisibility(driverRole ? View.VISIBLE : View.GONE);
            binding.orderEyebrow.setVisibility(driverRole ? View.VISIBLE : View.GONE);
            binding.orderSubtitle.setVisibility(driverRole ? View.VISIBLE : View.GONE);
            if (driverRole) {
                binding.orderTitle.setText(R.string.driver_workspace);
                if (driverListener == null)
                    driverListener = db.collection("drivers").document(uid)
                            .addSnapshotListener((driver, driverError) -> {
                                if (driverError != null || driver == null || !driver.exists()) {
                                    showError();
                                    return;
                                }
                                approved = Boolean.TRUE.equals(driver.getBoolean("approved"));
                                available = Boolean.TRUE.equals(driver.getBoolean("available"));
                                binding.orderAvailability.setVisibility(approved ? View.VISIBLE : View.GONE);
                                binding.orderAvailability.setText(available ? R.string.turn_off : R.string.turn_on);
                                binding.orderAvailability.setEnabled(driver.getString("activeOrderId") == null);
                                boolean online = approved && available && driver.getString("activeOrderId") == null;
                                boolean tracking = approved && (available || driver.getString("activeOrderId") != null);
                                binding.orderOffers.setVisibility(View.GONE);
                                watchOffers(online && historyOrder == null);
                                watchOrder(historyOrder != null ? historyOrder : driver.getString("activeOrderId"));
                                if (tracking) {
                                    if (locationPublisher == null)
                                        locationPublisher = new DriverLocationPublisher(this, uid);
                                    locationPublisher.setAvailable(online);
                                    locationPublisher.start();
                                } else if (locationPublisher != null) {
                                    locationPublisher.stop();
                                }
                                if (!approved)
                                    binding.orderInfo.setText(R.string.driver_not_approved);
                            });
            } else if ("customer".equals(role))
                watchOrder(historyOrder != null ? historyOrder : profile.getString("activeOrderId"));
            else
                showError();
        });
    }

    @Override
    protected void onStop() {
        if (binding == null) {
            super.onStop();
            return;
        }
        if (profileListener != null)
            profileListener.remove();
        if (driverListener != null)
            driverListener.remove();
        if (orderListener != null)
            orderListener.remove();
        offersHandler.removeCallbacks(offersRefresh);
        if (locationPublisher != null)
            locationPublisher.stop();
        watchOffers(false);
        stopChat();
        profileListener = driverListener = orderListener = null;
        activeOrder = null;
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == DriverLocationPublisher.PERMISSION_REQUEST && locationPublisher != null) {
            locationPublisher.start();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putString("pendingMessageId", pendingMessageId);
        out.putString("pendingMessageText", pendingMessageText);
        super.onSaveInstanceState(out);
    }

    private void watchOrder(String id) {
        if (id == null) {
            if (orderListener != null)
                orderListener.remove();
            orderListener = null;
            activeOrder = null;
            stopChat();
            binding.orderInfo.setText(R.string.no_active_order);
            clearActions();
            binding.orderTimeline.setVisibility(View.GONE);
            return;
        }
        if (id.equals(activeOrder) && orderListener != null)
            return;
        if (orderListener != null)
            orderListener.remove();
        activeOrder = id;
        orderListener = db.collection("orders").document(id).addSnapshotListener((order, error) -> {
            if (error != null || order == null || !order.exists()) {
                showError();
                return;
            }
            String status = order.getString("status");
            boolean chatAvailable = "ACCEPTED".equals(status) || "PICKED_UP".equals(status)
                    || "DELIVERING".equals(status);
            boolean historyChat = historyOrder != null && order.getString("driverId") != null
                    && ("COMPLETED".equals(status) || "CANCELLED".equals(status));
            if (chatAvailable || historyChat) {
                watchChat(id);
                binding.chatInput.setVisibility(historyChat ? View.GONE : View.VISIBLE);
                binding.chatSend.setVisibility(historyChat ? View.GONE : View.VISIBLE);
            } else
                stopChat();
            String details = order.getString("restaurantName") + "\n" + statusLabel(status);
            renderTimeline(status);
            details += "\n" + order.getString("restaurantAddress");
            Object items = order.get("items");
            if (items instanceof java.util.List) {
                for (Object item : (java.util.List<?>) items) {
                    if (item instanceof java.util.Map) {
                        java.util.Map<?, ?> values = (java.util.Map<?, ?>) item;
                        details += "\n" + getString(R.string.order_item_summary,
                                values.get("quantity") instanceof Number ? ((Number) values.get("quantity")).intValue()
                                        : 0,
                                String.valueOf(values.get("name")));
                    }
                }
            }
            Long total = order.getLong("total");
            if (total != null)
                details += "\n" + getString(R.string.order_cod,
                        java.text.NumberFormat.getNumberInstance(new java.util.Locale("vi", "VN")).format(total));
            String note = order.getString("note");
            if (note != null && !note.isEmpty())
                details += "\n" + note;
            if ("driver".equals(role) && !"SEARCHING".equals(status)) {
                details += "\n" + order.getString("recipient") + "\n" + order.getString("phone") + "\n"
                        + order.getString("address");
            }
            binding.orderInfo.setText(details);
            clearActions();
            boolean driver = "driver".equals(role);
            recipientPhone = order.getString("phone");
            deliveryAddress = order.getString("address");
            binding.orderMap.setVisibility(deliveryAddress != null ? View.VISIBLE : View.GONE);
            Double deliveryLatitude = order.getDouble("deliveryLatitude");
            Double deliveryLongitude = order.getDouble("deliveryLongitude");
            boolean trackable = deliveryLatitude != null && deliveryLongitude != null
                    && (deliveryLatitude != 0 || deliveryLongitude != 0)
                    && !"COMPLETED".equals(status) && !"CANCELLED".equals(status);
            binding.orderTrack.setVisibility(trackable ? View.VISIBLE : View.GONE);
            binding.orderCall
                    .setVisibility(driver && chatAvailable && recipientPhone != null ? View.VISIBLE : View.GONE);
            boolean canCancel = driver
                    ? ("ACCEPTED".equals(status) || "PICKED_UP".equals(status) || "DELIVERING".equals(status))
                    : ("SEARCHING".equals(status) || "ACCEPTED".equals(status));
            binding.orderCancel.setVisibility(canCancel ? View.VISIBLE : View.GONE);
            binding.orderRelease.setVisibility(driver && "ACCEPTED".equals(status) ? View.VISIBLE : View.GONE);
            if (driver && ("ACCEPTED".equals(status) || "PICKED_UP".equals(status) || "DELIVERING".equals(status))) {
                binding.orderAdvance.setVisibility(View.VISIBLE);
                binding.orderAdvance.setText("ACCEPTED".equals(status) ? R.string.picked_up
                        : "PICKED_UP".equals(status) ? R.string.start_delivery : R.string.complete_order);
            }
        });
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
        return "";
    }

    private void showOffers() {
        db.collection("orderOffers").whereEqualTo("status", "SEARCHING").get(Source.SERVER).addOnCompleteListener(this,
                task -> {
                    if (!task.isSuccessful()) {
                        showError();
                        return;
                    }
                    List<DocumentSnapshot> docs = visibleOffers(task.getResult().getDocuments());
                    if (docs.isEmpty()) {
                        binding.ordersMessage.setText(R.string.no_offers);
                        return;
                    }
                    String[] labels = new String[docs.size()];
                    for (int i = 0; i < docs.size(); i++)
                        labels[i] = offerLabel(docs.get(i));
                    new AlertDialog.Builder(this).setTitle(R.string.offers).setItems(labels,
                            (dialog, which) -> run(orders.accept(uid, docs.get(which).getId())))
                            .setNegativeButton(R.string.cancel, null).show();
                });
    }

    private void watchOffers(boolean enabled) {
        offersHandler.removeCallbacks(offersRefresh);
        if (!enabled) {
            if (offersListener != null)
                offersListener.remove();
            offersListener = null;
            lastOffers.clear();
            lastOffersCached = false;
            binding.driverJobs.removeAllViews();
            binding.driverJobs.setVisibility(View.GONE);
            return;
        }
        if (offersListener != null)
            return;
        binding.driverJobs.setVisibility(View.VISIBLE);
        offersListener = db.collection("orderOffers").whereEqualTo("status", "SEARCHING")
                .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE, (snapshot, error) -> {
                    if (error != null || snapshot == null) {
                        showError();
                        return;
                    }
                    lastOffers = new java.util.ArrayList<>(snapshot.getDocuments());
                    lastOffersCached = snapshot.getMetadata().isFromCache();
                    renderOffers();
                    offersHandler.removeCallbacks(offersRefresh);
                    offersHandler.postDelayed(offersRefresh, 15000);
                });
    }

    private void renderOffers() {
        if (binding == null)
            return;
        List<DocumentSnapshot> offers = visibleOffers(lastOffers);
        binding.driverJobs.removeAllViews();
        android.widget.TextView heading = new android.widget.TextView(this);
        heading.setText(offers.isEmpty() ? R.string.no_offers : R.string.offers);
        heading.setTextSize(20);
        binding.driverJobs.addView(heading);
        for (DocumentSnapshot offer : offers) {
            View card = getLayoutInflater().inflate(R.layout.item_offer, binding.driverJobs, false);
            ((android.widget.TextView) card.findViewById(R.id.offerInfo)).setText(offerLabel(offer));
            com.google.android.material.button.MaterialButton accept = card.findViewById(R.id.offerAccept);
            accept.setEnabled(!lastOffersCached);
            accept.setOnClickListener(view -> {
                accept.setEnabled(false);
                orders.accept(uid, offer.getId()).addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        accept.setEnabled(true);
                        showError();
                    }
                });
            });
            binding.driverJobs.addView(card);
        }
        if (lastOffersCached)
            binding.ordersMessage.setText(R.string.catalog_cached);
        else
            binding.ordersMessage.setText("");
    }

    private List<DocumentSnapshot> visibleOffers(List<DocumentSnapshot> offers) {
        List<DocumentSnapshot> visible = new java.util.ArrayList<>();
        for (DocumentSnapshot offer : offers) {
            if (visibleToDriver(offer))
                visible.add(offer);
        }
        visible.sort((left, right) -> Boolean.compare(uid.equals(right.getString("targetDriverId")),
                uid.equals(left.getString("targetDriverId"))));
        return visible;
    }

    private boolean visibleToDriver(DocumentSnapshot offer) {
        String target = offer.getString("targetDriverId");
        if (target == null || target.isEmpty() || target.equals(uid))
            return true;
        Timestamp created = offer.getTimestamp("createdAt");
        return created == null || System.currentTimeMillis() - created.toDate().getTime() > 45000L;
    }

    private String offerLabel(DocumentSnapshot offer) {
        Long total = offer.getLong("total");
        String prefix = uid.equals(offer.getString("targetDriverId")) ? getString(R.string.priority_offer) + " · " : "";
        return prefix + offer.getString("restaurantName") + "\nCOD: "
                + java.text.NumberFormat.getNumberInstance(new java.util.Locale("vi", "VN"))
                        .format(total == null ? 0 : total)
                + " VND";
    }

    private void showHistory() {
        db.collection("orders").whereEqualTo("driverId", uid).get(Source.SERVER).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) {
                showError();
                return;
            }
            List<DocumentSnapshot> docs = new java.util.ArrayList<>(task.getResult().getDocuments());
            docs.sort((left, right) -> {
                com.google.firebase.Timestamp a = left.getTimestamp("createdAt");
                com.google.firebase.Timestamp b = right.getTimestamp("createdAt");
                return a == null || b == null ? 0 : b.compareTo(a);
            });
            java.util.Collections.reverse(docs);
            if (docs.isEmpty()) {
                binding.ordersMessage.setText(R.string.no_active_order);
                return;
            }
            String[] labels = new String[docs.size()];
            for (int i = 0; i < docs.size(); i++)
                labels[i] = docs.get(i).getString("restaurantName") + " - "
                        + statusLabel(docs.get(i).getString("status"));
            new AlertDialog.Builder(this).setTitle(R.string.order_history).setItems(labels, (dialog, which) -> {
                Intent intent = new Intent(this, OrdersActivity.class);
                intent.putExtra(EXTRA_HISTORY_ORDER, docs.get(which).getId());
                startActivity(intent);
            }).setNegativeButton(R.string.cancel, null).show();
        });
    }

    private void clearActions() {
        binding.orderCancel.setVisibility(View.GONE);
        binding.orderRelease.setVisibility(View.GONE);
        binding.orderAdvance.setVisibility(View.GONE);
        binding.orderCall.setVisibility(View.GONE);
        binding.orderMap.setVisibility(View.GONE);
        binding.orderTrack.setVisibility(View.GONE);
    }

    private void watchChat(String orderId) {
        if (orderId.equals(chatOrder) && chatListener != null)
            return;
        stopChat();
        binding.chatPanel.setVisibility(View.VISIBLE);
        chatOrder = orderId;
        chatListener = db.collection("orders").document(orderId).collection("messages")
                .orderBy("createdAt").limitToLast(chatLimit)
                .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE, (messages, error) -> {
                    if (error != null || messages == null) {
                        binding.ordersMessage.setText(R.string.chat_failed);
                        return;
                    }
                    binding.chatBubbles.removeAllViews();
                    binding.chatMessages.setText(messages.isEmpty() ? getString(R.string.chat_empty) : "");
                    binding.chatMessages.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
                    for (DocumentSnapshot message : messages.getDocuments()) {
                        boolean mine = uid.equals(message.getString("senderId"));
                        android.widget.TextView bubble = new android.widget.TextView(this);
                        String sender = getString(mine ? R.string.chat_me : R.string.chat_other);
                        String label = sender + "\n" + message.getString("text");
                        com.google.firebase.Timestamp sent = message.getTimestamp("createdAt");
                        if (message.getMetadata().hasPendingWrites())
                            label += "\n" + getString(R.string.chat_pending);
                        else if (sent != null)
                            label += "\n" + new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                    .format(sent.toDate());
                        bubble.setText(label);
                        bubble.setTextSize(15);
                        bubble.setTextColor(getColor(R.color.ui_text));
                        bubble.setLineSpacing(dp(3), 1);
                        bubble.setPadding(dp(14), dp(12), dp(14), dp(12));
                        bubble.setBackgroundResource(mine ? R.drawable.bg_pill_green : R.drawable.bg_search_foodnow);
                        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                                -2, -2);
                        params.gravity = mine ? android.view.Gravity.END : android.view.Gravity.START;
                        params.topMargin = dp(8);
                        params.leftMargin = mine ? dp(24) : 0;
                        params.rightMargin = mine ? 0 : dp(24);
                        binding.chatBubbles.addView(bubble, params);
                    }
                    binding.chatOlder.setVisibility(messages.size() >= chatLimit ? View.VISIBLE : View.GONE);
                });
    }

    private void stopChat() {
        if (chatListener != null)
            chatListener.remove();
        chatListener = null;
        chatOrder = null;
        if (binding != null)
            binding.chatPanel.setVisibility(View.GONE);
    }

    private void sendMessage() {
        if (chatOrder == null)
            return;
        String text = binding.chatInput.getText().toString().trim();
        if (text.isEmpty())
            return;
        if (!text.equals(pendingMessageText) || pendingMessageId == null) {
            pendingMessageText = text;
            pendingMessageId = UUID.randomUUID().toString();
        }
        binding.chatSend.setEnabled(false);
        new ChatRepository().send(uid, chatOrder, pendingMessageId, text).addOnCompleteListener(this, task -> {
            binding.chatSend.setEnabled(true);
            if (task.isSuccessful()) {
                binding.chatInput.setText("");
                pendingMessageId = pendingMessageText = null;
                binding.ordersMessage.setText("");
            } else
                binding.ordersMessage.setText(R.string.chat_failed);
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void renderTimeline(String status) {
        String[] stages = { "SEARCHING", "ACCEPTED", "PICKED_UP", "DELIVERING", "COMPLETED" };
        int current = java.util.Arrays.asList(stages).indexOf(status);
        binding.orderTimeline.removeAllViews();
        binding.orderTimeline.setVisibility(current < 0 ? View.GONE : View.VISIBLE);
        for (int i = 0; i < stages.length && current >= 0; i++) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));
            android.widget.ImageView icon = new android.widget.ImageView(this);
            icon.setImageResource(R.drawable.ic_ui_check);
            icon.setImageTintList(android.content.res.ColorStateList
                    .valueOf(getColor(i <= current ? R.color.ui_accent : R.color.ui_border)));
            row.addView(icon, new android.widget.LinearLayout.LayoutParams(dp(24), dp(24)));
            android.widget.TextView label = new android.widget.TextView(this);
            label.setText(statusLabel(stages[i]));
            label.setTextColor(getColor(i == current ? R.color.ui_text : R.color.ui_muted));
            label.setTextSize(14);
            label.setPadding(dp(12), 0, 0, 0);
            if (i == current)
                label.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(label);
            binding.orderTimeline.addView(row);
        }
    }

    private void showError() {
        if (binding != null)
            binding.ordersMessage.setText(R.string.order_operation_error);
    }

    private void run(Task<Void> task) {
        binding.orderAdvance.setEnabled(false);
        binding.orderRelease.setEnabled(false);
        binding.orderCancel.setEnabled(false);
        binding.orderOffers.setEnabled(false);
        task.addOnCompleteListener(this, result -> {
            binding.orderAdvance.setEnabled(true);
            binding.orderRelease.setEnabled(true);
            binding.orderCancel.setEnabled(true);
            binding.orderOffers.setEnabled(true);
            if (!result.isSuccessful())
                showError();
            else
                binding.ordersMessage.setText("");
        });
    }
}
