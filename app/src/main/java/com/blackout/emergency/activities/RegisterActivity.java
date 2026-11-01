package com.blackout.emergency.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.blackout.emergency.R;
import com.blackout.emergency.auth.SessionManager;
import com.blackout.emergency.auth.SupabaseAuthClient;
import com.blackout.emergency.database.EmergencyRepository;
import com.blackout.emergency.database.UserProfileEntity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Registration activity for citizen responders.
 * Captures user identity, validates password strength, registers with Supabase Auth,
 * caches user profile in Room, and completes onboarding.
 */
public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etName;
    private TextInputEditText etEmail;
    private TextInputEditText etPhone;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirmPassword;
    private CheckBox cbTerms;
    private MaterialButton btnSubmit;
    private ProgressBar pbLoading;
    private TextView tvError;

    private SessionManager sessionManager;
    private SupabaseAuthClient authClient;
    private EmergencyRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        sessionManager = SessionManager.getInstance(this);
        authClient = new SupabaseAuthClient(this);
        repository = EmergencyRepository.getInstance(getApplicationContext());

        ImageButton btnBack = findViewById(R.id.btn_register_back);
        etName = findViewById(R.id.et_register_name);
        etEmail = findViewById(R.id.et_register_email);
        etPhone = findViewById(R.id.et_register_phone);
        etPassword = findViewById(R.id.et_register_password);
        etConfirmPassword = findViewById(R.id.et_register_confirm_password);
        cbTerms = findViewById(R.id.cb_register_terms);
        btnSubmit = findViewById(R.id.btn_register_submit);
        pbLoading = findViewById(R.id.pb_register_loading);
        tvError = findViewById(R.id.tv_register_error);

        TextView tvToLogin = findViewById(R.id.tv_register_to_login);
        TextView tvGuest = findViewById(R.id.tv_register_guest);

        btnBack.setOnClickListener(v -> finish());

        tvToLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });

        tvGuest.setOnClickListener(v -> {
            sessionManager.setOnboardingCompleted(true);
            sessionManager.setGuestMode(true);
            Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        btnSubmit.setOnClickListener(v -> handleRegistration());
    }

    private void handleRegistration() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
        String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString() : "";

        // Client-side validations
        if (TextUtils.isEmpty(name)) {
            showError("Full name is required");
            etName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email address");
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password) || password.length() < 8) {
            showError("Password must be at least 8 characters long");
            etPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }

        if (!cbTerms.isChecked()) {
            showError("You must accept the emergency protocol terms to register");
            return;
        }

        setLoading(true);

        authClient.signUp(email, password, name, phone, new SupabaseAuthClient.AuthCallback() {
            @Override
            public void onSuccess(@androidx.annotation.NonNull String accessToken,
                                  @androidx.annotation.Nullable String refreshToken,
                                  @androidx.annotation.NonNull String userId,
                                  @androidx.annotation.NonNull String userEmail,
                                  @androidx.annotation.NonNull String fullName,
                                  @androidx.annotation.NonNull String role) {
                runOnUiThread(() -> {
                    setLoading(false);

                    sessionManager.saveSession(
                            accessToken,
                            refreshToken,
                            userId,
                            userEmail,
                            fullName,
                            role
                    );
                    sessionManager.setOnboardingCompleted(true);

                    // Persist to local Room database for offline resilience
                    UserProfileEntity profile = new UserProfileEntity(userId, fullName, userEmail, role);
                    profile.setPhoneNumber(phone);
                    repository.saveUserProfile(profile);

                    Toast.makeText(RegisterActivity.this, "Account created successfully", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onFailure(@androidx.annotation.NonNull String errorMessage) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showError(errorMessage);
                });
            }
        });
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        if (loading) {
            tvError.setVisibility(View.GONE);
            pbLoading.setVisibility(View.VISIBLE);
            btnSubmit.setEnabled(false);
            btnSubmit.setText("");
        } else {
            pbLoading.setVisibility(View.GONE);
            btnSubmit.setEnabled(true);
            btnSubmit.setText("REGISTER");
        }
    }
}
