package com.blackout.emergency.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.blackout.emergency.R;
import com.blackout.emergency.auth.SessionManager;

/**
 * Tactical Welcome and Educational Onboarding Activity.
 * Explains core capabilities (Offline SOS, P2P mesh relay, signed public warnings)
 * and provides entry points for Account Creation, Login, or immediate Guest access.
 */
public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        Button btnRegister = findViewById(R.id.btn_welcome_register);
        Button btnLogin = findViewById(R.id.btn_welcome_login);
        Button btnGuest = findViewById(R.id.btn_welcome_guest);

        btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        btnLogin.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, LoginActivity.class);
            startActivity(intent);
        });

        btnGuest.setOnClickListener(v -> {
            SessionManager sessionManager = SessionManager.getInstance(WelcomeActivity.this);
            sessionManager.setOnboardingCompleted(true);
            sessionManager.setGuestMode(true);

            Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}

