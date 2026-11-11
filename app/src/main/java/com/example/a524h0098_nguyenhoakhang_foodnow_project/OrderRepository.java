package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OrderRepository {
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public Task<Void> setAvailability(String uid, boolean available) {
        return firestore.runTransaction(transaction -> {
            DocumentReference driver = firestore.collection("drivers").document(uid);
            DocumentSnapshot data = transaction.get(driver);
            if (!Boolean.TRUE.equals(data.getBoolean("approved")) || data.getString("activeOrderId") != null) {
                throw new IllegalStateException("Driver cannot change availability");
            }
            transaction.update(driver, "available", available);
            return null;
        });
    }

    public Task<Void> accept(String uid, String orderId) {
        DocumentReference driver = firestore.collection("drivers").document(uid);
        DocumentReference offer = firestore.collection("orderOffers").document(orderId);
        DocumentReference order = firestore.collection("orders").document(orderId);
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot driverData = transaction.get(driver);
            DocumentSnapshot offerData = transaction.get(offer);
            if (!Boolean.TRUE.equals(driverData.getBoolean("approved"))
                    || !Boolean.TRUE.equals(driverData.getBoolean("available"))
                    || driverData.getString("activeOrderId") != null
                    || !"SEARCHING".equals(offerData.getString("status"))) {
                throw new IllegalStateException("Order is unavailable");
            }
            transaction.update(driver, "activeOrderId", orderId);
            transaction.update(order, "status", "ACCEPTED", "driverId", uid, "updatedAt", FieldValue.serverTimestamp());
            transaction.update(offer, "status", "ACCEPTED", "driverId", uid, "updatedAt", FieldValue.serverTimestamp());
            return null;
        });
    }

    public Task<Void> advance(String uid, String orderId) {
        DocumentReference order = firestore.collection("orders").document(orderId);
        DocumentReference driver = firestore.collection("drivers").document(uid);
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot current = transaction.get(order);
            DocumentSnapshot driverData = transaction.get(driver);
            if (!uid.equals(current.getString("driverId")) || !orderId.equals(driverData.getString("activeOrderId"))) {
                throw new IllegalStateException("Order is not assigned to this driver");
            }
            String status = current.getString("status");
            String next;
            if ("ACCEPTED".equals(status))
                next = "PICKED_UP";
            else if ("PICKED_UP".equals(status))
                next = "DELIVERING";
            else if ("DELIVERING".equals(status))
                next = "COMPLETED";
            else
                throw new IllegalStateException("Order cannot advance");
            transaction.update(order, "status", next, "updatedAt", FieldValue.serverTimestamp());
            if ("COMPLETED".equals(next)) {
                String customerId = current.getString("customerId");
                if (customerId == null)
                    throw new IllegalStateException("Missing customer");
                transaction.update(driver, "activeOrderId", FieldValue.delete());
                transaction.update(firestore.collection("users").document(customerId), "activeOrderId",
                        FieldValue.delete());
            }
            return null;
        });
    }

    public Task<Void> release(String uid, String orderId) {
        DocumentReference order = firestore.collection("orders").document(orderId);
        DocumentReference driver = firestore.collection("drivers").document(uid);
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot current = transaction.get(order);
            DocumentSnapshot driverData = transaction.get(driver);
            if (!uid.equals(current.getString("driverId")) || !"ACCEPTED".equals(current.getString("status"))
                    || !orderId.equals(driverData.getString("activeOrderId"))) {
                throw new IllegalStateException("Order cannot be released");
            }
            transaction.update(order, "status", "SEARCHING", "driverId", FieldValue.delete(), "updatedAt",
                    FieldValue.serverTimestamp());
            transaction.update(firestore.collection("orderOffers").document(orderId), "status", "SEARCHING", "driverId",
                    FieldValue.delete(), "updatedAt", FieldValue.serverTimestamp());
            transaction.update(driver, "activeOrderId", FieldValue.delete());
            return null;
        });
    }

    public Task<Void> cancelSearching(String uid, String orderId) {
        DocumentReference order = firestore.collection("orders").document(orderId);
        DocumentReference user = firestore.collection("users").document(uid);
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot current = transaction.get(order);
            DocumentSnapshot profile = transaction.get(user);
            String status = current.getString("status");
            if (!uid.equals(current.getString("customerId"))
                    || !("SEARCHING".equals(status) || "ACCEPTED".equals(status))
                    || !orderId.equals(profile.getString("activeOrderId"))) {
                throw new IllegalStateException("Order can no longer be cancelled");
            }
            if ("ACCEPTED".equals(status)) {
                String driverId = current.getString("driverId");
                if (driverId == null)
                    throw new IllegalStateException("Missing driver");
                transaction.update(firestore.collection("drivers").document(driverId), "activeOrderId",
                        FieldValue.delete());
            }
            transaction.update(order, "status", "CANCELLED", "updatedAt", FieldValue.serverTimestamp());
            transaction.update(user, "activeOrderId", FieldValue.delete());
            transaction.update(firestore.collection("orderOffers").document(orderId), "status", "CANCELLED",
                    "updatedAt", FieldValue.serverTimestamp());
            return null;
        });
    }

    public Task<Void> cancelByDriver(String uid, String orderId) {
        DocumentReference order = firestore.collection("orders").document(orderId);
        DocumentReference driver = firestore.collection("drivers").document(uid);
        DocumentReference offer = firestore.collection("orderOffers").document(orderId);
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot current = transaction.get(order);
            DocumentSnapshot driverData = transaction.get(driver);
            String status = current.getString("status");
            if (!uid.equals(current.getString("driverId")) || !orderId.equals(driverData.getString("activeOrderId"))
                    || !("ACCEPTED".equals(status) || "PICKED_UP".equals(status) || "DELIVERING".equals(status))) {
                throw new IllegalStateException("Order cannot be cancelled by this driver");
            }
            String customerId = current.getString("customerId");
            if (customerId == null)
                throw new IllegalStateException("Missing customer");
            transaction.update(order, "status", "CANCELLED", "updatedAt", FieldValue.serverTimestamp());
            transaction.update(firestore.collection("users").document(customerId), "activeOrderId",
                    FieldValue.delete());
            transaction.update(driver, "activeOrderId", FieldValue.delete());
            transaction.update(offer, "status", "CANCELLED", "updatedAt", FieldValue.serverTimestamp());
            return null;
        });
    }

    public static class Item {
        public final String id;
        public final int quantity;
        public final long price;

        public Item(String id, int quantity, long price) {
            this.id = id;
            this.quantity = quantity;
            this.price = price;
        }
    }

    public Task<String> create(String uid, String orderId, String restaurantId, List<Item> items,
            long expectedFee, String recipient, String phone, String address, String note,
            double deliveryLatitude, double deliveryLongitude, String targetDriverId) {
        DocumentReference order = firestore.collection("orders").document(orderId);
        DocumentReference user = firestore.collection("users").document(uid);
        DocumentReference restaurant = firestore.collection("restaurants").document(restaurantId);
        DocumentReference config = firestore.document("appConfig/public");
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot previous = transaction.get(order);
            if (previous.exists()) {
                if (!"SEARCHING".equals(previous.getString("status")))
                    throw new IllegalStateException("Previous order is no longer searching");
                if (!uid.equals(previous.getString("customerId")))
                    throw new IllegalStateException("Order ID belongs to another customer");
                if (!restaurantId.equals(previous.getString("restaurantId"))
                        || !recipient.trim().equals(previous.getString("recipient"))
                        || !phone.trim().equals(previous.getString("phone"))
                        || !address.trim().equals(previous.getString("address"))
                        || !note.trim().equals(previous.getString("note"))
                        || !Long.valueOf(expectedFee).equals(previous.getLong("deliveryFee"))) {
                    throw new IllegalStateException("Previous order details differ");
                }
                Object savedItems = previous.get("items");
                if (!(savedItems instanceof List) || ((List<?>) savedItems).size() != items.size())
                    throw new IllegalStateException("Previous order items differ");
                List<?> previousItems = (List<?>) savedItems;
                for (int i = 0; i < items.size(); i++) {
                    if (!(previousItems.get(i) instanceof Map))
                        throw new IllegalStateException("Invalid previous order");
                    Map<?, ?> oldItem = (Map<?, ?>) previousItems.get(i);
                    Item item = items.get(i);
                    if (!item.id.equals(oldItem.get("id")) || !Long.valueOf(item.price).equals(oldItem.get("price"))
                            || !Long.valueOf(item.quantity).equals(oldItem.get("quantity")))
                        throw new IllegalStateException("Previous order items differ");
                }
                return orderId;
            }
            DocumentSnapshot profile = transaction.get(user);
            DocumentSnapshot store = transaction.get(restaurant);
            DocumentSnapshot settings = transaction.get(config);
            if (!"customer".equals(profile.getString("role")) || profile.getString("activeOrderId") != null) {
                throw new IllegalStateException("Customer has an active order or no profile");
            }
            if (RestaurantHours.getStatus(store.getBoolean("active"), store.getString("openTime"),
                    store.getString("closeTime"), store.getString("timezone"),
                    System.currentTimeMillis()) != RestaurantHours.Status.OPEN) {
                throw new IllegalStateException("Restaurant unavailable");
            }
            Long fee = settings.getLong("deliveryFee");
            if (fee == null || fee < 0 || fee != expectedFee)
                throw new IllegalStateException("Delivery fee changed");
            if (items == null || items.isEmpty() || items.size() > 4)
                throw new IllegalArgumentException("Invalid items");
            List<Map<String, Object>> snapshots = new ArrayList<>();
            Set<String> foodIds = new HashSet<>();
            long subtotal = 0;
            for (Item item : items) {
                if (item.id == null || item.id.isEmpty() || item.id.contains("/") || !foodIds.add(item.id)
                        || item.quantity < 1 || item.quantity > 99) {
                    throw new IllegalArgumentException("Invalid item");
                }
                DocumentSnapshot food = transaction.get(restaurant.collection("foods").document(item.id));
                Long price = food.getLong("price");
                String name = food.getString("name");
                if (!Boolean.TRUE.equals(food.getBoolean("available")) || price == null || price <= 0
                        || price != item.price || name == null) {
                    throw new IllegalStateException("Menu changed");
                }
                subtotal = Math.addExact(subtotal, Math.multiplyExact(price, item.quantity));
                Map<String, Object> snapshot = new HashMap<>();
                snapshot.put("id", item.id);
                snapshot.put("name", name);
                snapshot.put("price", price);
                snapshot.put("quantity", item.quantity);
                snapshots.add(snapshot);
            }
            Map<String, Object> data = new HashMap<>();
            data.put("customerId", uid);
            data.put("restaurantId", restaurantId);
            data.put("restaurantName", store.getString("name"));
            data.put("restaurantAddress", store.getString("address"));
            data.put("items", snapshots);
            data.put("subtotal", subtotal);
            data.put("deliveryFee", fee);
            data.put("total", Math.addExact(subtotal, fee));
            data.put("recipient", recipient.trim());
            data.put("phone", phone.trim());
            data.put("address", address.trim());
            data.put("note", note.trim());
            data.put("deliveryLatitude", deliveryLatitude);
            data.put("deliveryLongitude", deliveryLongitude);
            data.put("paymentMethod", "COD");
            data.put("status", "SEARCHING");
            data.put("createdAt", FieldValue.serverTimestamp());
            transaction.set(order, data);
            Map<String, Object> offer = new HashMap<>();
            offer.put("restaurantId", restaurantId);
            offer.put("restaurantName", store.getString("name"));
            offer.put("total", Math.addExact(subtotal, fee));
            offer.put("status", "SEARCHING");
            offer.put("targetDriverId", targetDriverId == null ? "" : targetDriverId);
            offer.put("createdAt", FieldValue.serverTimestamp());
            transaction.set(firestore.collection("orderOffers").document(orderId), offer);
            transaction.update(user, "activeOrderId", orderId);
            return orderId;
        });
    }
}
