package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class ChatRepository {
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public Task<Void> send(String uid, String orderId, String messageId, String text) {
        String content = text.trim();
        if (content.isEmpty() || content.length() > 1000)
            throw new IllegalArgumentException("Invalid message");
        DocumentReference message = firestore.collection("orders").document(orderId)
                .collection("messages").document(messageId);
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot existing = transaction.get(message);
            if (existing.exists()) {
                if (!uid.equals(existing.getString("senderId")) || !content.equals(existing.getString("text"))) {
                    throw new IllegalStateException("Message ID already used");
                }
                return null;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("senderId", uid);
            data.put("text", content);
            data.put("createdAt", FieldValue.serverTimestamp());
            transaction.set(message, data);
            return null;
        });
    }
}
