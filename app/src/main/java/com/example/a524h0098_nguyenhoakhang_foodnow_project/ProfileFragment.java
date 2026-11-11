package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class ProfileFragment extends Fragment {
    private EditText name, phone, address, currentPassword, newPassword;
    private TextView message;
    private Button save, changePassword;
    private FirebaseUser user;
    private android.widget.ImageView avatar;
    private static final int PICK_AVATAR = 84;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        View screen = inflater.inflate(R.layout.profile_editor, container, false);
        user = FirebaseAuth.getInstance().getCurrentUser();
        avatar = screen.findViewById(R.id.profileAvatar);
        loadAvatar();
        screen.findViewById(R.id.profileChooseAvatar).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, PICK_AVATAR);
        });
        TextView email = screen.findViewById(R.id.profileEmail);
        email.setText(user == null ? "" : user.getEmail());
        name = screen.findViewById(R.id.profileEditName);
        phone = screen.findViewById(R.id.profileEditPhone);
        address = screen.findViewById(R.id.profileEditAddress);
        save = screen.findViewById(R.id.profileSaveChanges);
        message = screen.findViewById(R.id.profileFeedback);
        currentPassword = screen.findViewById(R.id.profileCurrentPassword);
        newPassword = screen.findViewById(R.id.profileNewPassword);
        changePassword = screen.findViewById(R.id.profileChangePassword);
        screen.findViewById(R.id.profileLogout).setOnClickListener(v -> {
            String uid = user == null ? null : user.getUid();
            NotificationHelper.clear(() -> {
                DriverLocationPublisher.clear(uid);
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(requireContext(), SignInActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            });
        });
        save.setEnabled(false);
        changePassword.setOnClickListener(v -> changePassword());
        save.setOnClickListener(v -> save());
        if (user != null)
            FirebaseFirestore.getInstance().collection("users").document(user.getUid()).get()
                    .addOnCompleteListener(task -> {
                        if (!isAdded() || getView() == null)
                            return;
                        if (!task.isSuccessful() || !task.getResult().exists()) {
                            message.setText(R.string.auth_error);
                            return;
                        }
                        name.setText(task.getResult().getString("name"));
                        phone.setText(task.getResult().getString("phone"));
                        address.setText(task.getResult().getString("address"));
                        save.setEnabled(true);
                    });
        return screen;
    }

    private void loadAvatar() {
        avatar.setImageResource(R.drawable.ic_ui_person);
        if (user == null)
            return;
        String value = requireContext().getSharedPreferences("foodnow_avatars", 0).getString(user.getUid(), null);
        if (value == null)
            return;
        try {
            android.net.Uri uri = android.net.Uri.parse(value);
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            java.io.InputStream bounds = requireContext().getContentResolver().openInputStream(uri);
            if (bounds == null)
                return;
            try (java.io.InputStream stream = bounds) {
                android.graphics.BitmapFactory.decodeStream(stream, null, options);
            }
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 512 || options.outHeight / options.inSampleSize > 512)
                options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            java.io.InputStream source = requireContext().getContentResolver().openInputStream(uri);
            if (source == null)
                return;
            try (java.io.InputStream stream = source) {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(stream, null, options);
                if (bitmap != null)
                    avatar.setImageBitmap(bitmap);
            }
        } catch (java.io.IOException | SecurityException error) {
            requireContext().getSharedPreferences("foodnow_avatars", 0).edit().remove(user.getUid()).apply();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_AVATAR || resultCode != android.app.Activity.RESULT_OK || data == null
                || data.getData() == null || user == null)
            return;
        android.net.Uri uri = data.getData();
        try {
            requireContext().getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            requireContext().getSharedPreferences("foodnow_avatars", 0).edit().putString(user.getUid(), uri.toString())
                    .apply();
            loadAvatar();
        } catch (SecurityException error) {
            message.setText(R.string.auth_error);
        }
    }

    private void save() {
        String fullName = name.getText().toString().trim();
        String number = phone.getText().toString().trim();
        String location = address.getText().toString().trim();
        if (user == null || fullName.isEmpty() || fullName.length() > 100 || number.isEmpty() || number.length() > 30
                || location.isEmpty() || location.length() > 300) {
            message.setText(R.string.registration_details_required);
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("name", fullName);
        data.put("phone", number);
        data.put("address", location);
        save.setEnabled(false);
        FirebaseFirestore.getInstance().collection("users").document(user.getUid()).update(data)
                .addOnCompleteListener(task -> {
                    if (!isAdded() || getView() == null)
                        return;
                    save.setEnabled(true);
                    message.setText(task.isSuccessful() ? R.string.profile_saved : R.string.auth_error);
                });
    }

    private void changePassword() {
        String oldValue = currentPassword.getText().toString();
        String newValue = newPassword.getText().toString();
        if (user == null || user.getEmail() == null || oldValue.isEmpty() || newValue.length() < 6) {
            message.setText(R.string.registration_details_required);
            return;
        }
        changePassword.setEnabled(false);
        user.reauthenticate(EmailAuthProvider.getCredential(user.getEmail(), oldValue))
                .continueWithTask(task -> {
                    if (!task.isSuccessful())
                        throw task.getException();
                    return user.updatePassword(newValue);
                }).addOnCompleteListener(task -> {
                    if (!isAdded() || getView() == null)
                        return;
                    changePassword.setEnabled(true);
                    message.setText(task.isSuccessful() ? R.string.password_changed : R.string.auth_error);
                    if (task.isSuccessful()) {
                        currentPassword.setText("");
                        newPassword.setText("");
                    }
                });
    }
}
