package com.blackout.emergency.auth;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Manages user authentication state, active session tokens, and guest mode flags.
 * Persists session securely in SharedPreferences.
 */
public class SessionManager {

    private static final String PREF_NAME = "blackout_auth_session";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_FULL_NAME = "full_name";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_ROLE = "role";
    private static final String KEY_IS_GUEST = "is_guest_mode";
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";
    private static final String KEY_PROFILE_COMPLETED = "profile_completed";

    private static volatile SessionManager instance;
    private final SharedPreferences prefs;

    public static synchronized SessionManager getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new SessionManager(context.getApplicationContext());
        }
        return instance;
    }

    private SessionManager(@NonNull Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public synchronized void saveSession(@NonNull String accessToken, @Nullable String refreshToken,
                                         @NonNull String userId, @NonNull String email,
                                         @Nullable String fullName, @Nullable String role) {
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_EMAIL, email)
                .putString(KEY_FULL_NAME, fullName != null ? fullName : "BLACKOUT Citizen")
                .putString(KEY_ROLE, role != null ? role : "CITIZEN")
                .putBoolean(KEY_IS_GUEST, false)
                .putBoolean(KEY_ONBOARDING_COMPLETED, true)
                .apply();
    }

    public synchronized void updateProfileDetails(@Nullable String fullName, @Nullable String phone, boolean isProfileCompleted) {
        SharedPreferences.Editor editor = prefs.edit();
        if (fullName != null) editor.putString(KEY_FULL_NAME, fullName);
        if (phone != null) editor.putString(KEY_PHONE, phone);
        editor.putBoolean(KEY_PROFILE_COMPLETED, isProfileCompleted);
        editor.apply();
    }

    public synchronized void setGuestMode(boolean isGuest) {
        prefs.edit()
                .putBoolean(KEY_IS_GUEST, isGuest)
                .putBoolean(KEY_ONBOARDING_COMPLETED, true)
                .apply();
    }

    public synchronized boolean isGuestMode() {
        return prefs.getBoolean(KEY_IS_GUEST, false);
    }

    public synchronized boolean isLoggedIn() {
        String token = prefs.getString(KEY_ACCESS_TOKEN, null);
        return token != null && !token.trim().isEmpty() && !isGuestMode();
    }

    public synchronized boolean hasCompletedOnboarding() {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false);
    }

    public synchronized void setOnboardingCompleted(boolean completed) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply();
    }

    public synchronized boolean isProfileCompleted() {
        return prefs.getBoolean(KEY_PROFILE_COMPLETED, false);
    }

    @Nullable
    public synchronized String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    @Nullable
    public synchronized String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, null);
    }

    @Nullable
    public synchronized String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    @Nullable
    public synchronized String getEmail() {
        return prefs.getString(KEY_EMAIL, null);
    }

    @NonNull
    public synchronized String getFullName() {
        return prefs.getString(KEY_FULL_NAME, "BLACKOUT Citizen");
    }

    @Nullable
    public synchronized String getPhoneNumber() {
        return prefs.getString(KEY_PHONE, null);
    }

    @NonNull
    public synchronized String getRole() {
        return prefs.getString(KEY_ROLE, "CITIZEN");
    }

    public synchronized void clearSession() {
        prefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_EMAIL)
                .remove(KEY_FULL_NAME)
                .remove(KEY_PHONE)
                .remove(KEY_ROLE)
                .putBoolean(KEY_IS_GUEST, false)
                .apply();
    }

    public synchronized void logout() {
        clearSession();
    }

    public synchronized void setUserDetails(String userId, String email, String fullName, String role) {
        prefs.edit()
                .putString(KEY_USER_ID, userId)
                .putString(KEY_EMAIL, email)
                .putString(KEY_FULL_NAME, fullName)
                .putString(KEY_ROLE, role)
                .apply();
    }
}
