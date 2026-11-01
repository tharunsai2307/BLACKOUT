package com.blackout.emergency.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.blackout.emergency.utils.Constants;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Pure Java 11 HTTP Client connecting native Android to Supabase Authentication and REST API.
 * Uses native HttpURLConnection with zero third-party framework dependencies.
 */
public class SupabaseAuthClient {

    private static final int TIMEOUT_MS = 15000;
    private final String baseUrl;
    private final String anonKey;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private HttpConnectionProvider connectionProvider;

    public interface HttpConnectionProvider {
        HttpURLConnection openConnection(URL url) throws IOException;
    }

    public interface AuthCallback {
        void onSuccess(@NonNull String accessToken, @Nullable String refreshToken,
                       @NonNull String userId, @NonNull String email,
                       @NonNull String fullName, @NonNull String role);
        void onFailure(@NonNull String errorMessage);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onFailure(@NonNull String errorMessage);
    }

    public interface ProfileCallback {
        void onSuccess(@NonNull JSONObject profile);
        void onFailure(@NonNull String errorMessage);
    }

    public interface ContactsCallback {
        void onSuccess(@NonNull JSONArray contacts);
        void onFailure(@NonNull String errorMessage);
    }

    public SupabaseAuthClient(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        this.baseUrl = prefs.getString(Constants.PREF_SUPABASE_URL, Constants.DEFAULT_SUPABASE_URL);
        this.anonKey = prefs.getString(Constants.PREF_SUPABASE_ANON_KEY, Constants.DEFAULT_SUPABASE_ANON_KEY);
        this.executor = Executors.newCachedThreadPool();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.connectionProvider = url -> (HttpURLConnection) url.openConnection();
    }

    public SupabaseAuthClient(@NonNull String baseUrl, @NonNull String anonKey) {
        this.baseUrl = baseUrl;
        this.anonKey = anonKey;
        this.executor = Executors.newCachedThreadPool();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.connectionProvider = url -> (HttpURLConnection) url.openConnection();
    }

    @VisibleForTesting
    public void setConnectionProvider(HttpConnectionProvider provider) {
        this.connectionProvider = provider;
    }

    private void postToMain(Runnable r) {
        if (mainHandler != null) {
            mainHandler.post(r);
        } else {
            r.run();
        }
    }

    /**
     * Sign Up a new citizen account via Supabase Auth (/auth/v1/signup).
     */
    public void signUp(@NonNull String email, @NonNull String password,
                       @NonNull String fullName, @Nullable String phone,
                       @NonNull AuthCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(baseUrl + "/auth/v1/signup");
                HttpURLConnection conn = connectionProvider.openConnection(url);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setDoOutput(true);
                conn.setRequestProperty("apikey", anonKey);
                conn.setRequestProperty("Content-Type", "application/json");

                JSONObject body = new JSONObject();
                body.put("email", email.trim().toLowerCase());
                body.put("password", password);

                JSONObject dataObj = new JSONObject();
                dataObj.put("full_name", fullName.trim());
                if (phone != null && !phone.trim().isEmpty()) {
                    dataObj.put("phone", phone.trim());
                }
                body.put("data", dataObj);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int statusCode = conn.getResponseCode();
                String responseBody = readResponse(conn, statusCode);

                if (statusCode >= 200 && statusCode < 300) {
                    JSONObject json = new JSONObject(responseBody);
                    String accessToken = json.optString("access_token", "");
                    String refreshToken = json.optString("refresh_token", "");

                    JSONObject userObj = json.optJSONObject("user");
                    String userId = userObj != null ? userObj.optString("id", "") : json.optString("id", "");
                    String userEmail = userObj != null ? userObj.optString("email", email) : email;

                    postToMain(() -> callback.onSuccess(accessToken, refreshToken, userId, userEmail, fullName, "CITIZEN"));
                } else {
                    String errorMsg = parseErrorMessage(responseBody, "Registration rejected by authentication server (HTTP " + statusCode + ")");
                    postToMain(() -> callback.onFailure(errorMsg));
                }
            } catch (Exception e) {
                postToMain(() -> callback.onFailure(e.getMessage() != null ? e.getMessage() : "Network connection failure during registration"));
            }
        });
    }

    /**
     * Sign In an existing user via Supabase Auth (/auth/v1/token?grant_type=password).
     */
    public void signIn(@NonNull String email, @NonNull String password, @NonNull AuthCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(baseUrl + "/auth/v1/token?grant_type=password");
                HttpURLConnection conn = connectionProvider.openConnection(url);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setDoOutput(true);
                conn.setRequestProperty("apikey", anonKey);
                conn.setRequestProperty("Content-Type", "application/json");

                JSONObject body = new JSONObject();
                body.put("email", email.trim().toLowerCase());
                body.put("password", password);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int statusCode = conn.getResponseCode();
                String responseBody = readResponse(conn, statusCode);

                if (statusCode >= 200 && statusCode < 300) {
                    JSONObject json = new JSONObject(responseBody);
                    String accessToken = json.getString("access_token");
                    String refreshToken = json.optString("refresh_token", "");

                    JSONObject userObj = json.getJSONObject("user");
                    String userId = userObj.getString("id");
                    String userEmail = userObj.getString("email");

                    JSONObject userMetadata = userObj.optJSONObject("user_metadata");
                    String fullName = userMetadata != null ? userMetadata.optString("full_name", "BLACKOUT Citizen") : "BLACKOUT Citizen";

                    postToMain(() -> callback.onSuccess(accessToken, refreshToken, userId, userEmail, fullName, "CITIZEN"));
                } else {
                    String errorMsg = parseErrorMessage(responseBody, "Invalid credentials or account does not exist.");
                    postToMain(() -> callback.onFailure(errorMsg));
                }
            } catch (Exception e) {
                postToMain(() -> callback.onFailure(e.getMessage() != null ? e.getMessage() : "Network failure during login attempt"));
            }
        });
    }

    /**
     * Request Password Recovery email (/auth/v1/recover).
     */
    public void resetPassword(@NonNull String email, @NonNull SimpleCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(baseUrl + "/auth/v1/recover");
                HttpURLConnection conn = connectionProvider.openConnection(url);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setDoOutput(true);
                conn.setRequestProperty("apikey", anonKey);
                conn.setRequestProperty("Content-Type", "application/json");

                JSONObject body = new JSONObject();
                body.put("email", email.trim().toLowerCase());

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int statusCode = conn.getResponseCode();
                if (statusCode >= 200 && statusCode < 300) {
                    postToMain(callback::onSuccess);
                } else {
                    String responseBody = readResponse(conn, statusCode);
                    String errorMsg = parseErrorMessage(responseBody, "Password reset request failed (HTTP " + statusCode + ")");
                    postToMain(() -> callback.onFailure(errorMsg));
                }
            } catch (Exception e) {
                postToMain(() -> callback.onFailure(e.getMessage() != null ? e.getMessage() : "Network failure during password recovery"));
            }
        });
    }

    /**
     * Fetch user profile from Supabase (/rest/v1/user_profiles?id=eq.{userId}).
     */
    public void fetchUserProfile(@NonNull String userId, @NonNull String token, @NonNull ProfileCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(baseUrl + "/rest/v1/user_profiles?id=eq." + userId + "&select=*");
                HttpURLConnection conn = connectionProvider.openConnection(url);
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("apikey", anonKey);
                conn.setRequestProperty("Authorization", "Bearer " + token);

                int statusCode = conn.getResponseCode();
                String responseBody = readResponse(conn, statusCode);

                if (statusCode >= 200 && statusCode < 300) {
                    JSONArray arr = new JSONArray(responseBody);
                    if (arr.length() > 0) {
                        JSONObject profile = arr.getJSONObject(0);
                        postToMain(() -> callback.onSuccess(profile));
                    } else {
                        postToMain(() -> callback.onFailure("Profile record not found"));
                    }
                } else {
                    postToMain(() -> callback.onFailure("Failed to fetch profile (HTTP " + statusCode + ")"));
                }
            } catch (Exception e) {
                postToMain(() -> callback.onFailure(e.getMessage() != null ? e.getMessage() : "Network failure fetching profile"));
            }
        });
    }

    /**
     * Update user profile (/rest/v1/user_profiles?id=eq.{userId}).
     */
    public void updateUserProfile(@NonNull String userId, @NonNull JSONObject profileJson,
                                  @NonNull String token, @NonNull SimpleCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(baseUrl + "/rest/v1/user_profiles?id=eq." + userId);
                HttpURLConnection conn = connectionProvider.openConnection(url);
                conn.setRequestMethod("PATCH");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setDoOutput(true);
                conn.setRequestProperty("apikey", anonKey);
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(profileJson.toString().getBytes(StandardCharsets.UTF_8));
                }

                int statusCode = conn.getResponseCode();
                if (statusCode >= 200 && statusCode < 300) {
                    postToMain(callback::onSuccess);
                } else {
                    String responseBody = readResponse(conn, statusCode);
                    postToMain(() -> callback.onFailure(parseErrorMessage(responseBody, "Failed to update profile (HTTP " + statusCode + ")")));
                }
            } catch (Exception e) {
                postToMain(() -> callback.onFailure(e.getMessage() != null ? e.getMessage() : "Network error updating profile"));
            }
        });
    }

    /**
     * Fetch emergency contacts (/rest/v1/emergency_contacts?user_id=eq.{userId}).
     */
    public void fetchEmergencyContacts(@NonNull String userId, @NonNull String token, @NonNull ContactsCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(baseUrl + "/rest/v1/emergency_contacts?user_id=eq." + userId + "&select=*&order=is_primary.desc,contact_name.asc");
                HttpURLConnection conn = connectionProvider.openConnection(url);
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("apikey", anonKey);
                conn.setRequestProperty("Authorization", "Bearer " + token);

                int statusCode = conn.getResponseCode();
                String responseBody = readResponse(conn, statusCode);

                if (statusCode >= 200 && statusCode < 300) {
                    JSONArray arr = new JSONArray(responseBody);
                    postToMain(() -> callback.onSuccess(arr));
                } else {
                    postToMain(() -> callback.onFailure("Failed to retrieve emergency contacts (HTTP " + statusCode + ")"));
                }
            } catch (Exception e) {
                postToMain(() -> callback.onFailure(e.getMessage() != null ? e.getMessage() : "Network failure loading contacts"));
            }
        });
    }

    /**
     * Upsert an emergency contact (/rest/v1/emergency_contacts).
     */
    public void upsertEmergencyContact(@NonNull JSONObject contactJson, @NonNull String token, @NonNull SimpleCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(baseUrl + "/rest/v1/emergency_contacts");
                HttpURLConnection conn = connectionProvider.openConnection(url);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setDoOutput(true);
                conn.setRequestProperty("apikey", anonKey);
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Prefer", "resolution=merge-duplicates");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(contactJson.toString().getBytes(StandardCharsets.UTF_8));
                }

                int statusCode = conn.getResponseCode();
                if (statusCode >= 200 && statusCode < 300) {
                    postToMain(callback::onSuccess);
                } else {
                    String responseBody = readResponse(conn, statusCode);
                    postToMain(() -> callback.onFailure(parseErrorMessage(responseBody, "Failed to save contact (HTTP " + statusCode + ")")));
                }
            } catch (Exception e) {
                postToMain(() -> callback.onFailure(e.getMessage() != null ? e.getMessage() : "Network error saving contact"));
            }
        });
    }

    /**
     * Delete an emergency contact (/rest/v1/emergency_contacts?id=eq.{contactId}).
     */
    public void deleteEmergencyContact(@NonNull String contactId, @NonNull String token, @NonNull SimpleCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(baseUrl + "/rest/v1/emergency_contacts?id=eq." + contactId);
                HttpURLConnection conn = connectionProvider.openConnection(url);
                conn.setRequestMethod("DELETE");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("apikey", anonKey);
                conn.setRequestProperty("Authorization", "Bearer " + token);

                int statusCode = conn.getResponseCode();
                if (statusCode >= 200 && statusCode < 300) {
                    postToMain(callback::onSuccess);
                } else {
                    String responseBody = readResponse(conn, statusCode);
                    postToMain(() -> callback.onFailure(parseErrorMessage(responseBody, "Failed to delete contact (HTTP " + statusCode + ")")));
                }
            } catch (Exception e) {
                postToMain(() -> callback.onFailure(e.getMessage() != null ? e.getMessage() : "Network error deleting contact"));
            }
        });
    }

    private String readResponse(HttpURLConnection conn, int statusCode) throws IOException {
        InputStream stream = (statusCode >= 200 && statusCode < 400) ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private String parseErrorMessage(String responseBody, String defaultMsg) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return defaultMsg;
        }
        try {
            JSONObject json = new JSONObject(responseBody);
            if (json.has("msg")) return json.getString("msg");
            if (json.has("error_description")) return json.getString("error_description");
            if (json.has("message")) return json.getString("message");
            if (json.has("error")) return json.getString("error");
        } catch (JSONException ignored) {}
        return defaultMsg;
    }
}
