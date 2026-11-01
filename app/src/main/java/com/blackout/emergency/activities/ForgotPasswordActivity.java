package com.blackout.emergency.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.blackout.emergency.R;
import com.blackout.emergency.auth.SupabaseAuthClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Self-service password recovery activity.
 * Sends Supabase recovery email with password reset token.
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    private TextInputEditText etEmail;
    private MaterialButton btnSubmit;
    private ProgressBar pbLoading;
    private TextView tvError;
    private MaterialCardView cardSuccess;

    private SupabaseAuthClient authClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        authClient = new SupabaseAuthClient(this);

        ImageButton btnBack = findViewById(R.id.btn_forgot_back);
        etEmail = findViewById(R.id.et_forgot_email);
        btnSubmit = findViewById(R.id.btn_forgot_submit);
        pbLoading = findViewById(R.id.pb_forgot_loading);
        tvError = findViewById(R.id.tv_forgot_error);
        cardSuccess = findViewById(R.id.card_forgot_success);
        MaterialButton btnToLogin = findViewById(R.id.btn_forgot_to_login);

        btnBack.setOnClickListener(v -> finish());
        btnToLogin.setOnClickListener(v -> finish());

        btnSubmit.setOnClickListener(v -> handleResetPassword());
    }

    private void handleResetPassword() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email address");
            etEmail.requestFocus();
            return;
        }

        setLoading(true);

        authClient.resetPassword(email, new SupabaseAuthClient.SimpleCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    setLoading(false);
                    cardSuccess.setVisibility(View.VISIBLE);
                    etEmail.setText("");
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
            cardSuccess.setVisibility(View.GONE);
            pbLoading.setVisibility(View.VISIBLE);
            btnSubmit.setEnabled(false);
            btnSubmit.setText("");
        } else {
            pbLoading.setVisibility(View.GONE);
            btnSubmit.setEnabled(true);
            btnSubmit.setText("SEND RESET LINK");
        }
    }
}
