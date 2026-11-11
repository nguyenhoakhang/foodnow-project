package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import java.util.HashMap;
import java.util.Map;

final class NotificationHelper {
    static final int PERMISSION_REQUEST = 81;

    private NotificationHelper() {
    }

    static void register(Activity activity) {
        android.content.SharedPreferences preferences = activity.getSharedPreferences("foodnow_notifications",
                Activity.MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(activity,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !preferences.getBoolean("asked", false)) {
            preferences.edit().putBoolean("asked", true).apply();
            ActivityCompat.requestPermissions(activity, new String[] { Manifest.permission.POST_NOTIFICATIONS },
                    PERMISSION_REQUEST);
        }
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> save(token));
    }

    static void save(String token) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || token == null || token.isEmpty())
            return;
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("updatedAt", FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance().collection("deviceTokens").document(uid).set(data);
    }

    static void clear(Runnable done) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            done.run();
            return;
        }
        FirebaseFirestore.getInstance().collection("deviceTokens").document(uid).delete()
                .addOnCompleteListener(task -> done.run());
    }
}
