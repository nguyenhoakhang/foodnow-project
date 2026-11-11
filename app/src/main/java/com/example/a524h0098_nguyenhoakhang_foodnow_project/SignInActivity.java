package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class SignInActivity extends BaseActivity {
    private static final int RC_GOOGLE = 41;
    private EditText email, password;
    private FirebaseAuth auth;
    private GoogleSignInClient googleClient;
    private boolean busy;
    private String notificationOrder;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_signin;
    }

    @Override
    protected void initViews() {
        email = findViewById(R.id.txtEmail);
        password = findViewById(R.id.txtPassword);
        auth = FirebaseAuth.getInstance();
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(this, options);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        notificationOrder = getIntent().getStringExtra(OrdersActivity.EXTRA_HISTORY_ORDER);
        FirebaseUser current = auth.getCurrentUser();
        if (current != null)
            openForUser(current);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        notificationOrder = intent.getStringExtra(OrdersActivity.EXTRA_HISTORY_ORDER);
        FirebaseUser current = auth.getCurrentUser();
        if (current != null)
            openForUser(current);
    }

    @Override
    protected void initEvents() {
        findViewById(R.id.btnSignIn).setOnClickListener(v -> authenticate());
        findViewById(R.id.btnSignUp).setOnClickListener(v -> openActivity(RegisterActivity.class));
        findViewById(R.id.btnForgot).setOnClickListener(v -> {
            String value = email.getText().toString().trim();
            if (value.isEmpty()) {
                showToast(getString(R.string.password_reset_email_hint));
                return;
            }
            auth.sendPasswordResetEmail(value)
                    .addOnSuccessListener(unused -> showToast(getString(R.string.password_reset_email_sent)))
                    .addOnFailureListener(e -> showToast(getString(R.string.password_reset_email_failed)));
        });
        findViewById(R.id.btnSignInGoogle)
                .setOnClickListener(v -> startActivityForResult(googleClient.getSignInIntent(), RC_GOOGLE));
    }

    private void setBusy(boolean value) {
        busy = value;
        findViewById(R.id.btnSignIn).setEnabled(!value);
        findViewById(R.id.btnSignUp).setEnabled(!value);
        findViewById(R.id.btnSignInGoogle).setEnabled(!value);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != RC_GOOGLE)
            return;
        try {
            GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);
            if (account == null || account.getIdToken() == null) {
                showToast(getString(R.string.auth_error));
                return;
            }
            setBusy(true);
            auth.signInWithCredential(GoogleAuthProvider.getCredential(account.getIdToken(), null))
                    .addOnSuccessListener(result -> openForUser(result.getUser()))
                    .addOnFailureListener(error -> {
                        setBusy(false);
                        showToast(getString(R.string.auth_error));
                    });
        } catch (ApiException error) {
            setBusy(false);
            showToast(getString(R.string.auth_error));
        }
    }

    private void authenticate() {
        if (busy)
            return;
        String address = email.getText().toString().trim(), pass = password.getText().toString();
        if (address.isEmpty() || pass.isEmpty()) {
            showToast(getString(R.string.sign_in_missing_fields));
            return;
        }
        setBusy(true);
        auth.signInWithEmailAndPassword(address, pass)
                .addOnSuccessListener(result -> openForUser(result.getUser()))
                .addOnFailureListener(e -> {
                    setBusy(false);
                    showToast(getString(R.string.sign_in_failed, e.getLocalizedMessage()));
                });
    }

    private void openForUser(FirebaseUser user) {
        if (user == null) {
            setBusy(false);
            return;
        }
        setBusy(true);
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(user.getUid()).get()
                .addOnSuccessListener(profile -> {
                    if (isFinishing() || isDestroyed())
                        return;
                    boolean driver = "driver".equals(profile.getString("role"));
                    if (notificationOrder != null && profile.exists() && !driver) {
                        Intent intent = new Intent(this, OrdersActivity.class);
                        intent.putExtra(OrdersActivity.EXTRA_HISTORY_ORDER, notificationOrder);
                        startActivity(intent);
                    } else {
                        openActivity(!profile.exists() ? RegisterActivity.class
                                : driver ? DriverActivity.class : MainActivity.class);
                    }
                    finish();
                }).addOnFailureListener(e -> {
                    if (isFinishing() || isDestroyed())
                        return;
                    setBusy(false);
                    showToast(getString(R.string.profile_check_failed));
                });
    }
}
