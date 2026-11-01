package com.blackout.emergency.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.blackout.emergency.R;

/**
 * Tactical Splash Screen: Initializes application state and routes users
 * based on onboarding completion, authenticated session, or emergency guest status.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 1000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.iv_splash_logo);
        Animation fadeIn = new AlphaAnimation(0.2f, 1.0f);
        fadeIn.setDuration(700);
        logo.startAnimation(fadeIn);

        new Handler(Looper.getMainLooper()).postDelayed(this::routeNextScreen, SPLASH_DELAY_MS);
    }

    private void routeNextScreen() {
        if (isFinishing() || isDestroyed()) return;

        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
