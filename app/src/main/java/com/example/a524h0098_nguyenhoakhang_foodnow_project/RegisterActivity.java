package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import android.os.Bundle;
import android.content.Intent;
import android.util.Patterns;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.a524h0098_nguyenhoakhang_foodnow_project.databinding.ActivityRegisterBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class RegisterActivity extends AppCompatActivity {
    private ActivityRegisterBinding binding;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.registerRoot, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        auth = FirebaseAuth.getInstance();
        FirebaseUser current = auth.getCurrentUser();
        if (current != null) {
            binding.registerEmail.setText(current.getEmail());
            binding.registerEmail.setEnabled(false);
            binding.registerPasswordInput.setVisibility(View.GONE);
        }
        binding.registerRole.setOnCheckedChangeListener((group, checkedId) -> binding.registerVehicleInput
                .setVisibility(checkedId == R.id.roleDriver ? View.VISIBLE : View.GONE));
        binding.createAccount.setOnClickListener(view -> register());
    }

    private void register() {
        String email = binding.registerEmail.getText().toString().trim();
        String password = binding.registerPassword.getText().toString();
        String name = binding.registerName.getText().toString().trim();
        String phone = binding.registerPhone.getText().toString().trim();
        String address = binding.registerAddress.getText().toString().trim();
        String vehicle = binding.registerVehicle.getText().toString().trim();
        String role = binding.registerRole.getCheckedRadioButtonId() == R.id.roleDriver ? "driver" : "customer";
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches() || (auth.getCurrentUser() == null && password.length() < 6)
                || name.isEmpty()
                || phone.isEmpty() || address.isEmpty() || ("driver".equals(role) && vehicle.isEmpty())) {
            binding.registerMessage.setText(R.string.registration_details_required);
            return;
        }
        setBusy(true);
        FirebaseUser current = auth.getCurrentUser();
        if (current != null && email.equalsIgnoreCase(current.getEmail())) {
            saveProfile(current, role, name, phone, address, vehicle);
        } else if (current != null) {
            setBusy(false);
            binding.registerMessage.setText(R.string.registration_auth_failed);
        } else {
            auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
                if (!task.isSuccessful() || auth.getCurrentUser() == null) {
                    setBusy(false);
                    binding.registerMessage.setText(R.string.registration_auth_failed);
                    return;
                }
                saveProfile(auth.getCurrentUser(), role, name, phone, address, vehicle);
            });
        }
    }

    private void saveProfile(FirebaseUser user, String role, String name, String phone, String address,
            String vehicle) {
        new ProfileRepository().create(user.getUid(), role, name, phone, address, vehicle)
                .addOnCompleteListener(this, task -> {
                    setBusy(false);
                    if (task.isSuccessful()) {
                        startActivity(
                                new Intent(this, "driver".equals(role) ? DriverActivity.class : MainActivity.class));
                        finish();
                    } else
                        binding.registerMessage.setText(R.string.registration_profile_failed);
                });
    }

    private void setBusy(boolean busy) {
        binding.createAccount.setEnabled(!busy);
        binding.registerProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.registerMessage.setText("");
    }
}
