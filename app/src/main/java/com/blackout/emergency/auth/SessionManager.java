package com.blackout.emergency.auth;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages user authentication state, encrypted session tokens, and guest mode flags.
 *
 * Security architecture (Phase 10):
 * - Sensitive tokens (access_token, refresh_token) are encrypted at rest using AES-256-GCM
 *   via KeyStoreCryptoManager backed by Android Keystore.
 * - Non-sensitive profile metadata is stored in separate unencrypted preferences.
 * - Includes automatic non-destructive migration from legacy plaintext storage.
 * - Thread-safe atomic token refresh serialization to prevent refresh token race conditions.
 * - Logout and session invalidation never delete offline Room database emergency data.
 */
public class SessionManager {

    private static final String PREF_NAME = "blackout_auth_session";

    // Encrypted credential keys
    private static final String KEY_ENC_ACCESS_TOKEN = "enc_access_token";
    private static final String KEY_ENC_REFRESH_TOKEN = "enc_refresh_token";
    private static final String KEY_TOKEN_EXPIRES_AT_SEC = "token_expires_at_sec";
    private static final String KEY_AUTH_MIGRATED = "auth_migrated_v10";

    // Legacy plaintext keys for migration
    private static final String KEY_LEGACY_ACCESS_TOKEN = "access_token";
    private static final String KEY_LEGACY_REFRESH_TOKEN = "refresh_token";

    // Non-sensitive profile keys
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
    private final KeyStoreCryptoManager cryptoManager;

    // Refresh synchronization lock & queue
    private final Object refreshLock = new Object();
    private boolean isRefreshing = false;
    private final List<TokenRefreshListener> pendingRefreshListeners = new ArrayList<>();

    public interface TokenRefreshListener {
        void onTokenRefreshed(@NonNull String newAccessToken);
        void onRefreshFailed(@NonNull String errorMessage);
    }

    public static synchronized SessionManager getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new SessionManager(context.getApplicationContext());
        }
        return instance;
    }

    private SessionManager(@NonNull Context context) {
        this(context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE), KeyStoreCryptoManager.getInstance());
    }

    @VisibleForTesting
    public SessionManager(@NonNull SharedPreferences prefs, @NonNull KeyStoreCryptoManager cryptoManager) {
        this.prefs = prefs;
        this.cryptoManager = cryptoManager;
        migrateLegacyPlaintextSessionIfNecessary();
    }

    /**
     * Non-destructively migrates legacy plaintext tokens to AES-256-GCM encrypted storage.
     * Verifies round-trip decryption before clearing plaintext credentials.
     */
    private void migrateLegacyPlaintextSessionIfNecessary() {
        try {
            boolean alreadyMigrated = prefs.getBoolean(KEY_AUTH_MIGRATED, false);
            if (alreadyMigrated) {
                return;
            }

            String legacyAccess = prefs.getString(KEY_LEGACY_ACCESS_TOKEN, null);
            String legacyRefresh = prefs.getString(KEY_LEGACY_REFRESH_TOKEN, null);

            if (legacyAccess != null && !legacyAccess.trim().isEmpty()) {
                String encAccess = cryptoManager.encrypt(legacyAccess.trim());
                String encRefresh = (legacyRefresh != null && !legacyRefresh.trim().isEmpty())
                        ? cryptoManager.encrypt(legacyRefresh.trim())
                        : null;

                // Verify decryption before clearing plaintext
                String decryptedAccess = cryptoManager.decrypt(encAccess);
                if (legacyAccess.trim().equals(decryptedAccess)) {
                    long expSec = extractJwtExpiration(legacyAccess.trim());
                    SharedPreferences.Editor editor = prefs.edit()
                            .putString(KEY_ENC_ACCESS_TOKEN, encAccess)
                            .remove(KEY_LEGACY_ACCESS_TOKEN)
                            .remove(KEY_LEGACY_REFRESH_TOKEN)
                            .putBoolean(KEY_AUTH_MIGRATED, true);

                    if (encRefresh != null) {
                        editor.putString(KEY_ENC_REFRESH_TOKEN, encRefresh);
                    }
                    if (expSec > 0) {
                        editor.putLong(KEY_TOKEN_EXPIRES_AT_SEC, expSec);
                    }
                    editor.apply();
                } else {
                    // Decryption verification failed; clear invalid tokens safely
                    clearSensitiveTokens();
                    prefs.edit().putBoolean(KEY_AUTH_MIGRATED, true).apply();
                }
            } else {
                prefs.edit().putBoolean(KEY_AUTH_MIGRATED, true).apply();
            }
        } catch (Exception e) {
            // In case of Keystore or migration error, safely clear sensitive credentials
            clearSensitiveTokens();
            prefs.edit().putBoolean(KEY_AUTH_MIGRATED, true).apply();
        }
    }

    /**
     * Stores user authentication session with AES-256-GCM encrypted tokens.
     */
    public synchronized void saveSession(@NonNull String accessToken, @Nullable String refreshToken,
                                         @NonNull String userId, @NonNull String email,
                                         @Nullable String fullName, @Nullable String role) {
        try {
            String encAccessToken = cryptoManager.encrypt(accessToken.trim());
            String encRefreshToken = (refreshToken != null && !refreshToken.trim().isEmpty())
                    ? cryptoManager.encrypt(refreshToken.trim())
                    : null;

            long expSec = extractJwtExpiration(accessToken.trim());

            SharedPreferences.Editor editor = prefs.edit()
                    .putString(KEY_ENC_ACCESS_TOKEN, encAccessToken)
                    .putString(KEY_USER_ID, userId)
                    .putString(KEY_EMAIL, email)
                    .putString(KEY_FULL_NAME, fullName != null ? fullName : "BLACKOUT Citizen")
                    .putString(KEY_ROLE, role != null ? role : "CITIZEN")
                    .putBoolean(KEY_IS_GUEST, false)
                    .putBoolean(KEY_ONBOARDING_COMPLETED, true)
                    .remove(KEY_LEGACY_ACCESS_TOKEN)
                    .remove(KEY_LEGACY_REFRESH_TOKEN);

            if (encRefreshToken != null) {
                editor.putString(KEY_ENC_REFRESH_TOKEN, encRefreshToken);
            } else {
                editor.remove(KEY_ENC_REFRESH_TOKEN);
            }

            if (expSec > 0) {
                editor.putLong(KEY_TOKEN_EXPIRES_AT_SEC, expSec);
            } else {
                editor.remove(KEY_TOKEN_EXPIRES_AT_SEC);
            }

            editor.apply();
        } catch (Exception e) {
            // Failed to encrypt credentials; do not store in plaintext
            clearSensitiveTokens();
        }
    }

    /**
     * Extracts token expiration timestamp (in seconds) from JWT claims.
     */
    private long extractJwtExpiration(@NonNull String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length >= 2) {
                byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
                JSONObject json = new JSONObject(new String(payloadBytes, StandardCharsets.UTF_8));
                return json.optLong("exp", 0L);
            }
        } catch (Exception ignored) {}
        return 0L;
    }

    /**
     * Checks whether the current access token is expired or about to expire within 30 seconds.
     */
    public synchronized boolean isTokenExpired() {
        String token = getAccessToken();
        if (token == null || token.trim().isEmpty()) {
            return true;
        }

        long expSec = prefs.getLong(KEY_TOKEN_EXPIRES_AT_SEC, 0L);
        if (expSec <= 0) {
            expSec = extractJwtExpiration(token);
        }

        if (expSec > 0) {
            long nowSec = System.currentTimeMillis() / 1000L;
            return nowSec >= (expSec - 30L); // 30-second clock skew buffer
        }

        return false;
    }

    /**
     * Thread-safe atomic token refresh.
     * If a refresh is already in progress, subsequent callers join the wait queue.
     */
    public void refreshTokenIfNeeded(@NonNull SupabaseAuthClient authClient,
                                      @NonNull TokenRefreshListener listener) {
        String currentToken;
        String refreshToken;

        synchronized (refreshLock) {
            currentToken = getAccessToken();
            refreshToken = getRefreshToken();

            if (currentToken != null && !isTokenExpired()) {
                listener.onTokenRefreshed(currentToken);
                return;
            }

            if (refreshToken == null || refreshToken.trim().isEmpty()) {
                listener.onRefreshFailed("No refresh token available. Re-authentication required.");
                return;
            }

            pendingRefreshListeners.add(listener);

            if (isRefreshing) {
                // Another thread is actively executing the refresh HTTP request
                return;
            }

            isRefreshing = true;
        }

        // Execute refresh request asynchronously outside lock
        final String tokenToRefresh = refreshToken;
        authClient.refreshAccessToken(tokenToRefresh, new SupabaseAuthClient.AuthCallback() {
            @Override
            public void onSuccess(@NonNull String newAccessToken, @Nullable String newRefreshToken,
                                  @NonNull String userId, @NonNull String email,
                                  @NonNull String fullName, @NonNull String role) {
                List<TokenRefreshListener> callers;
                synchronized (refreshLock) {
                    isRefreshing = false;
                    saveSession(newAccessToken, newRefreshToken != null ? newRefreshToken : tokenToRefresh,
                            userId.isEmpty() ? getUserId() : userId,
                            email.isEmpty() ? getEmail() : email,
                            fullName, role);

                    callers = new ArrayList<>(pendingRefreshListeners);
                    pendingRefreshListeners.clear();
                }

                for (TokenRefreshListener caller : callers) {
                    caller.onTokenRefreshed(newAccessToken);
                }
            }

            @Override
            public void onFailure(@NonNull String errorMessage) {
                List<TokenRefreshListener> callers;
                synchronized (refreshLock) {
                    isRefreshing = false;
                    if (errorMessage.contains("400") || errorMessage.toLowerCase().contains("invalid") || errorMessage.toLowerCase().contains("revoked")) {
                        // Refresh token is permanently invalid or revoked
                        clearSensitiveTokens();
                    }
                    callers = new ArrayList<>(pendingRefreshListeners);
                    pendingRefreshListeners.clear();
                }

                for (TokenRefreshListener caller : callers) {
                    caller.onRefreshFailed(errorMessage);
                }
            }
        });
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
        String token = getAccessToken();
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
        String encToken = prefs.getString(KEY_ENC_ACCESS_TOKEN, null);
        if (encToken != null && !encToken.trim().isEmpty()) {
            try {
                return cryptoManager.decrypt(encToken);
            } catch (Exception e) {
                return null;
            }
        }
        // Fallback to legacy key if migration has not run
        return prefs.getString(KEY_LEGACY_ACCESS_TOKEN, null);
    }

    @Nullable
    public synchronized String getRefreshToken() {
        String encToken = prefs.getString(KEY_ENC_REFRESH_TOKEN, null);
        if (encToken != null && !encToken.trim().isEmpty()) {
            try {
                return cryptoManager.decrypt(encToken);
            } catch (Exception e) {
                return null;
            }
        }
        return prefs.getString(KEY_LEGACY_REFRESH_TOKEN, null);
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

    /**
     * Clears only encrypted tokens from preferences.
     */
    private synchronized void clearSensitiveTokens() {
        prefs.edit()
                .remove(KEY_ENC_ACCESS_TOKEN)
                .remove(KEY_ENC_REFRESH_TOKEN)
                .remove(KEY_LEGACY_ACCESS_TOKEN)
                .remove(KEY_LEGACY_REFRESH_TOKEN)
                .remove(KEY_TOKEN_EXPIRES_AT_SEC)
                .apply();
    }

    /**
     * Clears user authentication session.
     * NOTE: Strictly preserves Room database tables (SOS alerts, relay queue, emergency contacts, public warnings).
     */
    public synchronized void clearSession() {
        prefs.edit()
                .remove(KEY_ENC_ACCESS_TOKEN)
                .remove(KEY_ENC_REFRESH_TOKEN)
                .remove(KEY_LEGACY_ACCESS_TOKEN)
                .remove(KEY_LEGACY_REFRESH_TOKEN)
                .remove(KEY_TOKEN_EXPIRES_AT_SEC)
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
