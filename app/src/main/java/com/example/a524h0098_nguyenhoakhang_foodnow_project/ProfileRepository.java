package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class ProfileRepository {
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public Task<Void> create(String uid, String role, String name, String phone, String address, String vehicle) {
        DocumentReference user = firestore.collection("users").document(uid);
        DocumentReference driver = firestore.collection("drivers").document(uid);
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot existing = transaction.get(user);
            if (existing.exists()) {
                if (!role.equals(existing.getString("role"))) {
                    throw new IllegalStateException("Role cannot be changed");
                }
                if ("driver".equals(role) && !transaction.get(driver).exists()) {
                    Map<String, Object> details = new HashMap<>();
                    details.put("vehicle", vehicle == null ? "" : vehicle.trim());
                    details.put("approved", true);
                    details.put("available", false);
                    details.put("createdAt", FieldValue.serverTimestamp());
                    transaction.set(driver, details);
                }
                return null;
            }
            Map<String, Object> profile = new HashMap<>();
            profile.put("role", role);
            profile.put("name", name.trim());
            profile.put("phone", phone.trim());
            profile.put("address", address.trim());
            profile.put("createdAt", FieldValue.serverTimestamp());
            transaction.set(user, profile);
            if ("driver".equals(role)) {
                Map<String, Object> details = new HashMap<>();
                details.put("vehicle", vehicle == null ? "" : vehicle.trim());
                details.put("approved", true);
                details.put("available", false);
                details.put("createdAt", FieldValue.serverTimestamp());
                transaction.set(driver, details);
            }
            return null;
        });
    }
}
