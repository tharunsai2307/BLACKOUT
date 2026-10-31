package com.blackout.emergency.gateway;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.utils.Constants;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * HTTP Client connecting the Android Internet Gateway to the Supabase central backend.
 * Uses native Java HttpURLConnection / HttpsURLConnection with zero third-party dependencies.
 * Sends validated JSON payloads conforming to the protocol specification to the Edge Function.
 */
public class BackendApiClient {

    private static final String TAG = "BackendApiClient";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 20000;

    private static final Set<String> VALID_CATEGORIES = new HashSet<String>() {{
        add(Constants.CATEGORY_MEDICAL);
        add(Constants.CATEGORY_ROAD_ACCIDENT);
        add(Constants.CATEGORY_TRAPPED);
        add(Constants.CATEGORY_FLOOD);
        add(Constants.CATEGORY_FIRE);
        add(Constants.CATEGORY_DISASTER);
        add(Constants.CATEGORY_PERSONAL_SAFETY);
        add(Constants.CATEGORY_OTHER);
    }};

    public interface HttpConnectionProvider {
        HttpURLConnection openConnection(URL url) throws IOException;
    }

    private String baseUrl;
    private String anonKey;
    private HttpConnectionProvider connectionProvider;

    public BackendApiClient(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        this.baseUrl = prefs.getString(Constants.PREF_SUPABASE_URL, Constants.DEFAULT_SUPABASE_URL);
        this.anonKey = prefs.getString(Constants.PREF_SUPABASE_ANON_KEY, Constants.DEFAULT_SUPABASE_ANON_KEY);
        this.connectionProvider = url -> (HttpURLConnection) url.openConnection();
    }

    public BackendApiClient(@NonNull String baseUrl, @NonNull String anonKey) {
        this.baseUrl = baseUrl;
        this.anonKey = anonKey;
        this.connectionProvider = url -> (HttpURLConnection) url.openConnection();
    }

    @VisibleForTesting
    public void setConnectionProvider(HttpConnectionProvider provider) {
        this.connectionProvider = provider;
    }

    public synchronized void configure(@NonNull String baseUrl, @NonNull String anonKey) {
        this.baseUrl = baseUrl;
        this.anonKey = anonKey;
    }

    public synchronized String getBaseUrl() {
        return baseUrl;
    }

    public synchronized String getAnonKey() {
        return anonKey;
    }

    /**
     * Serializes an EmergencyMessage into the canonical JSON structure expected by the backend.
     */
    @NonNull
    public JSONObject buildJsonPayload(@NonNull EmergencyMessage message, @Nullable String gatewayDeviceId) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("protocolVersion", Constants.PROTOCOL_VERSION);
        json.put("messageType", Constants.MESSAGE_TYPE_SOS);
        json.put("messageId", message.getMessageId());
        json.put("originalSenderDeviceId", message.getSenderDeviceId() != null ? message.getSenderDeviceId() : "UNKNOWN_DEV");
        json.put("gatewayDeviceId", gatewayDeviceId != null ? gatewayDeviceId : "GATEWAY-UNKNOWN");
        json.put("category", message.getEmergencyCategory());
        json.put("description", message.getDescription());
        json.put("priority", message.getPriority() != null ? message.getPriority() : Constants.PRIORITY_MODERATE);

        if (message.getLatitude() != null) {
            json.put("latitude", message.getLatitude());
        }
        if (message.getLongitude() != null) {
            json.put("longitude", message.getLongitude());
        }
        if (message.getLocationAccuracy() != null) {
            json.put("accuracyMeters", message.getLocationAccuracy());
        }
        if (message.getLocationTimestamp() != null) {
            json.put("locationTimestamp", message.getLocationTimestamp());
        }
        if (message.getLocationStatus() != null) {
            json.put("locationStatus", message.getLocationStatus());
        }

        json.put("createdAt", message.getCreatedAt());
        json.put("expiresAt", message.getExpiresAt());
        json.put("hopCount", message.getHopCount());

        return json;
    }

    /**
     * Validates client-side constraints before initiating a network request.
     */
    @Nullable
    public String validateMessage(@NonNull EmergencyMessage message) {
        if (message.getMessageId() == null || message.getMessageId().trim().isEmpty()) {
            return "Message ID is missing";
        }
        try {
            UUID.fromString(message.getMessageId());
        } catch (IllegalArgumentException e) {
            return "Message ID is not a valid UUID: " + message.getMessageId();
        }

        if (message.getEmergencyCategory() == null || !VALID_CATEGORIES.contains(message.getEmergencyCategory())) {
            return "Invalid emergency category: " + message.getEmergencyCategory();
        }

        if (message.getDescription() == null || message.getDescription().trim().isEmpty()) {
            return "Description cannot be empty";
        }
        if (message.getDescription().length() > Constants.MAX_DESCRIPTION_LENGTH) {
            return "Description exceeds limit of " + Constants.MAX_DESCRIPTION_LENGTH + " characters";
        }

        if (message.getLatitude() != null && (message.getLatitude() < -90.0 || message.getLatitude() > 90.0)) {
            return "Latitude out of range [-90, 90]";
        }
        if (message.getLongitude() != null && (message.getLongitude() < -180.0 || message.getLongitude() > 180.0)) {
            return "Longitude out of range [-180, 180]";
        }

        if (message.getHopCount() < 0 || message.getHopCount() > Constants.MAX_MESH_HOPS) {
            return "Hop count exceeds allowable limit of " + Constants.MAX_MESH_HOPS;
        }

        return null;
    }

    /**
     * Synchronously uploads an emergency alert to the Supabase Edge Function endpoint.
     */
    @NonNull
    public UploadResult uploadAlert(@NonNull EmergencyMessage message, @Nullable String gatewayDeviceId) {
        String validationError = validateMessage(message);
        if (validationError != null) {
            return UploadResult.rejected(400, "CLIENT_VALIDATION_ERROR", validationError);
        }

        if (message.getExpiresAt() != null && message.getExpiresAt() <= System.currentTimeMillis()) {
            return UploadResult.rejected(400, "ALERT_EXPIRED", "Emergency alert has expired");
        }

        HttpURLConnection connection = null;
        try {
            String endpoint = normalizeUrl(baseUrl) + "/functions/v1/emergency-alerts";
            URL url = new URL(endpoint);

            connection = connectionProvider.openConnection(url);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoInput(true);
            connection.setDoOutput(true);

            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("apikey", anonKey);
            connection.setRequestProperty("Authorization", "Bearer " + anonKey);
            if (gatewayDeviceId != null) {
                connection.setRequestProperty("x-gateway-device-id", gatewayDeviceId);
            }

            JSONObject payloadJson = buildJsonPayload(message, gatewayDeviceId);
            byte[] bodyBytes = payloadJson.toString().getBytes(StandardCharsets.UTF_8);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(bodyBytes);
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            String responseBody = readStream(responseCode >= 200 && responseCode < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream());

            if (responseCode == 201 || responseCode == 200) {
                String status = "SUCCESS";
                String backendRef = null;
                try {
                    JSONObject respJson = new JSONObject(responseBody);
                    status = respJson.optString("status", "SUCCESS");
                    backendRef = respJson.optString("backendReference", null);
                } catch (Exception ignored) {}

                if ("DUPLICATE_ACCEPTED".equalsIgnoreCase(status)) {
                    return UploadResult.duplicateAccepted(responseCode, backendRef, responseBody);
                }
                return UploadResult.success(responseCode, status, backendRef, responseBody);
            } else if (responseCode == 400) {
                String error = "VALIDATION_FAILED";
                String msg = responseBody;
                try {
                    JSONObject respJson = new JSONObject(responseBody);
                    error = respJson.optString("error", error);
                    msg = respJson.optString("message", msg);
                } catch (Exception ignored) {}
                return UploadResult.rejected(responseCode, error, msg);
            } else if (responseCode == 409) {
                return UploadResult.conflict(responseCode, "CONFLICTING_PAYLOAD", responseBody);
            } else {
                return UploadResult.serverError(responseCode, "HTTP " + responseCode + ": " + responseBody);
            }
        } catch (IOException e) {
            return UploadResult.networkError(e.getMessage() != null ? e.getMessage() : "Network error occurred");
        } catch (Exception e) {
            return UploadResult.serverError(500, e.getMessage() != null ? e.getMessage() : "Unexpected error");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Checks the health and availability of the Supabase backend.
     */
    @NonNull
    public HealthResult checkHealth() {
        HttpURLConnection connection = null;
        try {
            String endpoint = normalizeUrl(baseUrl) + "/functions/v1/emergency-alerts/health";
            URL url = new URL(endpoint);

            connection = connectionProvider.openConnection(url);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoInput(true);

            connection.setRequestProperty("apikey", anonKey);
            connection.setRequestProperty("Authorization", "Bearer " + anonKey);

            int responseCode = connection.getResponseCode();
            String body = readStream(responseCode == 200 ? connection.getInputStream() : connection.getErrorStream());

            if (responseCode == 200) {
                return new HealthResult(true, responseCode, body, null);
            } else {
                return new HealthResult(false, responseCode, body, "HTTP " + responseCode);
            }
        } catch (Exception e) {
            return new HealthResult(false, 0, null, e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String normalizeUrl(String url) {
        if (url == null) return "";
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }

    /**
     * Result representation for an SOS alert upload operation.
     */
    public static class UploadResult {
        private final boolean success;
        private final boolean duplicate;
        private final boolean retryable;
        private final int httpStatusCode;
        private final String status;
        private final String backendReference;
        private final String errorMessage;
        private final String rawResponse;

        private UploadResult(boolean success, boolean duplicate, boolean retryable,
                             int httpStatusCode, String status, String backendReference,
                             String errorMessage, String rawResponse) {
            this.success = success;
            this.duplicate = duplicate;
            this.retryable = retryable;
            this.httpStatusCode = httpStatusCode;
            this.status = status;
            this.backendReference = backendReference;
            this.errorMessage = errorMessage;
            this.rawResponse = rawResponse;
        }

        public static UploadResult success(int code, String status, String ref, String raw) {
            return new UploadResult(true, false, false, code, status, ref, null, raw);
        }

        public static UploadResult duplicateAccepted(int code, String ref, String raw) {
            return new UploadResult(true, true, false, code, Constants.UPLOAD_STATUS_DUPLICATE_ACCEPTED, ref, null, raw);
        }

        public static UploadResult rejected(int code, String error, String msg) {
            return new UploadResult(false, false, false, code, Constants.UPLOAD_STATUS_REJECTED, null, error + ": " + msg, msg);
        }

        public static UploadResult conflict(int code, String error, String msg) {
            return new UploadResult(false, false, false, code, "CONFLICT", null, error + ": " + msg, msg);
        }

        public static UploadResult serverError(int code, String msg) {
            return new UploadResult(false, false, true, code, "SERVER_ERROR", null, msg, msg);
        }

        public static UploadResult networkError(String error) {
            return new UploadResult(false, false, true, 0, "NETWORK_ERROR", null, error, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isDuplicate() {
            return duplicate;
        }

        public boolean isRetryable() {
            return retryable;
        }

        public int getHttpStatusCode() {
            return httpStatusCode;
        }

        public String getStatus() {
            return status;
        }

        public String getBackendReference() {
            return backendReference;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getRawResponse() {
            return rawResponse;
        }
    }

    /**
     * Result representation for backend health check.
     */
    public static class HealthResult {
        private final boolean healthy;
        private final int httpStatusCode;
        private final String responseBody;
        private final String errorMessage;

        public HealthResult(boolean healthy, int httpStatusCode, String responseBody, String errorMessage) {
            this.healthy = healthy;
            this.httpStatusCode = httpStatusCode;
            this.responseBody = responseBody;
            this.errorMessage = errorMessage;
        }

        public boolean isHealthy() {
            return healthy;
        }

        public int getHttpStatusCode() {
            return httpStatusCode;
        }

        public String getResponseBody() {
            return responseBody;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
