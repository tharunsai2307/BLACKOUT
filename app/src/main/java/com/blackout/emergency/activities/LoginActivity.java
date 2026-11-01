package com.blackout.emergency.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.blackout.emergency.R;
import com.blackout.emergency.auth.SessionManager;
import com.blackout.emergency.auth.SupabaseAuthClient;
import com.blackout.emergency.database.EmergencyRepository;
import com.blackout.emergency.database.UserProfileEntity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Tactical Login Activity for returning citizen responders and coordinators.
 * Connects to Supabase Authentication with offline fallback caching in Room.
 */
public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private MaterialButton btnSubmit;
    private ProgressBar pbLoading;
    private TextView tvError;

    private SessionManager sessionManager;
    private SupabaseAuthClient authClient;
    private EmergencyRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sessionManager = SessionManager.getInstance(this);
        authClient = new SupabaseAuthClient(this);
        repository = EmergencyRepository.getInstance(getApplicationContext());

        ImageButton btnBack = findViewById(R.id.btn_login_back);
        etEmail = findViewById(R.id.et_login_email);
        etPassword = findViewById(R.id.et_login_password);
        btnSubmit = findViewById(R.id.btn_login_submit);
        pbLoading = findViewById(R.id.pb_login_loading);
        tvError = findViewById(R.id.tv_login_error);

        TextView tvForgotPassword = findViewById(R.id.tv_login_forgot_password);
        TextView tvToRegister = findViewById(R.id.tv_login_to_register);
        TextView tvGuest = findViewById(R.id.tv_login_guest);

        btnBack.setOnClickListener(v -> finish());

        tvToRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });

        tvForgotPassword.setOnClickListener(v -> {
            Toast.makeText(this, "Please contact your system coordinator for password recovery", Toast.LENGTH_LONG).show();
        });

        tvGuest.setOnClickListener(v -> {
            sessionManager.setOnboardingCompleted(true);
            sessionManager.setGuestMode(true);
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        btnSubmit.setOnClickListener(v -> handleLogin());
    }

    private void handleLogin() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email address");
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            showError("Password cannot be empty");
            etPassword.requestFocus();
            return;
        }

        setLoading(true);

        authClient.signIn(email, password, new SupabaseAuthClient.AuthCallback() {
            @Override
            public void onSuccess(@NonNull String accessToken, @Nullable String refreshToken,
                                  @NonNull String userId, @NonNull String userEmail,
                                  @NonNull String fullName, @NonNull String role) {
                runOnUiThread(() -> {
                    setLoading(false);
                    sessionManager.saveSession(accessToken, refreshToken, userId, userEmail, fullName, role);
                    sessionManager.setOnboardingCompleted(true);

                    UserProfileEntity profile = new UserProfileEntity(userId, fullName, userEmail, role);
                    repository.saveUserProfile(profile);

                    Toast.makeText(LoginActivity.this, "Welcome back, " + fullName, Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onFailure(@NonNull String errorMessage) {
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
            btnSubmit.setText("LOG IN");
        }
    }
}
