package com.blackout.emergency.warnings.sync;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.blackout.emergency.utils.Constants;
import com.blackout.emergency.warnings.models.PublicWarning;
import com.blackout.emergency.warnings.models.TrustedSigningKey;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Native HTTP client for fetching active digitally signed emergency warnings
 * and trusted key registry updates from the central cloud backend (Phase 7).
 * Uses standard HttpURLConnection with zero third-party networking libraries.
 */
public class WarningApiClient {

    public interface HttpConnectionProvider {
        HttpURLConnection openConnection(URL url) throws IOException;
    }

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 20000;

    private String baseUrl;
    private String anonKey;
    private HttpConnectionProvider connectionProvider;

    public WarningApiClient(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        this.baseUrl = prefs.getString(Constants.PREF_SUPABASE_URL, Constants.DEFAULT_SUPABASE_URL);
        this.anonKey = prefs.getString(Constants.PREF_SUPABASE_ANON_KEY, Constants.DEFAULT_SUPABASE_ANON_KEY);
        this.connectionProvider = url -> (HttpURLConnection) url.openConnection();
    }

    public WarningApiClient(@NonNull String baseUrl, @NonNull String anonKey) {
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

    // =========================================================================
    // 1. Fetch Active Published Warnings
    // =========================================================================

    @NonNull
    public List<PublicWarning> fetchActiveWarnings() throws IOException, JSONException {
        // Try Edge Function endpoint first: /functions/v1/warnings/active
        String endpoint = baseUrl + "/functions/v1/warnings/active";
        HttpURLConnection conn = null;
        try {
            conn = connectionProvider.openConnection(new URL(endpoint));
            setupHeaders(conn, "GET");

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                String responseBody = readStream(conn.getInputStream());
                return parseWarningsResponse(responseBody);
            } else if (responseCode == 404) {
                // Fallback to REST table query on Supabase: /rest/v1/public_warnings
                return fetchActiveWarningsViaRest();
            } else {
                String err = readStream(conn.getErrorStream());
                throw new IOException("Failed to fetch warnings (HTTP " + responseCode + "): " + err);
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @NonNull
    private List<PublicWarning> fetchActiveWarningsViaRest() throws IOException, JSONException {
        String endpoint = baseUrl + "/rest/v1/public_warnings?status=eq.PUBLISHED&select=*";
        HttpURLConnection conn = null;
        try {
            conn = connectionProvider.openConnection(new URL(endpoint));
            setupHeaders(conn, "GET");

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                String responseBody = readStream(conn.getInputStream());
                return parseWarningsJsonArray(new JSONArray(responseBody));
            } else {
                String err = readStream(conn.getErrorStream());
                throw new IOException("REST fallback failed (HTTP " + responseCode + "): " + err);
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // =========================================================================
    // 2. Fetch Key Registry Updates
    // =========================================================================

    @NonNull
    public List<TrustedSigningKey> fetchTrustedKeys() throws IOException, JSONException {
        String endpoint = baseUrl + "/functions/v1/warnings/keys";
        HttpURLConnection conn = null;
        try {
            conn = connectionProvider.openConnection(new URL(endpoint));
            setupHeaders(conn, "GET");

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                String responseBody = readStream(conn.getInputStream());
                return parseKeysResponse(responseBody);
            } else if (responseCode == 404) {
                return fetchTrustedKeysViaRest();
            } else {
                String err = readStream(conn.getErrorStream());
                throw new IOException("Failed to fetch signing keys (HTTP " + responseCode + "): " + err);
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @NonNull
    private List<TrustedSigningKey> fetchTrustedKeysViaRest() throws IOException, JSONException {
        String endpoint = baseUrl + "/rest/v1/warning_signing_keys?select=*";
        HttpURLConnection conn = null;
        try {
            conn = connectionProvider.openConnection(new URL(endpoint));
            setupHeaders(conn, "GET");

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                String responseBody = readStream(conn.getInputStream());
                return parseKeysJsonArray(new JSONArray(responseBody));
            } else {
                String err = readStream(conn.getErrorStream());
                throw new IOException("REST key query failed (HTTP " + responseCode + "): " + err);
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // =========================================================================
    // Response Parsers
    // =========================================================================

    @NonNull
    public List<PublicWarning> parseWarningsResponse(@NonNull String jsonStr) throws JSONException {
        JSONObject obj = new JSONObject(jsonStr);
        JSONArray arr = obj.optJSONArray("warnings");
        if (arr == null) {
            arr = new JSONArray();
        }
        return parseWarningsJsonArray(arr);
    }

    @NonNull
    public List<PublicWarning> parseWarningsJsonArray(@NonNull JSONArray arr) throws JSONException {
        List<PublicWarning> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.getJSONObject(i);
            PublicWarning w = new PublicWarning();

            // Field mapping supporting both camelCase and snake_case backend column names
            w.setWarningId(item.optString("warningId", item.optString("id", "")));
            w.setProtocolVersion(item.optInt("protocolVersion", item.optInt("protocol_version", 1)));
            w.setRevision(item.optInt("revision", 1));
            w.setTitle(item.optString("title", ""));
            w.setDescription(item.optString("description", ""));
            w.setCategory(item.optString("category", "GENERAL_PUBLIC_SAFETY"));
            w.setSeverity(item.optString("severity", "CRITICAL"));
            w.setIssuingAuthority(item.optString("issuingAuthority", item.optString("issuing_authority", "")));

            long issued = parseTimestamp(item, "issuedAt", "published_at");
            long effective = parseTimestamp(item, "effectiveAt", "effective_at");
            long expires = parseTimestamp(item, "expiresAt", "expires_at");

            w.setIssuedAt(issued > 0 ? issued : System.currentTimeMillis());
            w.setEffectiveAt(effective > 0 ? effective : w.getIssuedAt());
            w.setExpiresAt(expires > 0 ? expires : w.getIssuedAt() + (24 * 3600 * 1000L));

            List<String> regions = new ArrayList<>();
            JSONArray regArr = item.optJSONArray("affectedRegions");
            if (regArr == null) regArr = item.optJSONArray("affected_regions");
            if (regArr != null) {
                for (int j = 0; j < regArr.length(); j++) {
                    regions.add(regArr.optString(j));
                }
            } else if (item.has("affected_area")) {
                regions.add(item.optString("affected_area"));
            }
            w.setAffectedRegions(regions);

            if (item.has("latitude") && !item.isNull("latitude")) {
                w.setLatitude(item.optDouble("latitude"));
            }
            if (item.has("longitude") && !item.isNull("longitude")) {
                w.setLongitude(item.optDouble("longitude"));
            }
            if (item.has("radiusMeters") && !item.isNull("radiusMeters")) {
                w.setRadiusMeters(item.optDouble("radiusMeters"));
            } else if (item.has("radius_km") && !item.isNull("radius_km")) {
                w.setRadiusMeters(item.optDouble("radius_km") * 1000.0);
            }

            w.setLanguage(item.optString("language", "en"));
            w.setStatus(item.optString("status", PublicWarning.STATUS_PUBLISHED));
            w.setSigningKeyId(item.optString("signingKeyId", item.optString("signing_key_id", "")));
            w.setSignatureAlgorithm(item.optString("signatureAlgorithm", item.optString("signature_algorithm", "Ed25519")));
            w.setSignature(item.optString("signature", ""));
            w.setCanonicalPayload(item.optString("canonicalPayload", item.optString("canonical_payload", null)));

            list.add(w);
        }
        return list;
    }

    @NonNull
    public List<TrustedSigningKey> parseKeysResponse(@NonNull String jsonStr) throws JSONException {
        JSONObject obj = new JSONObject(jsonStr);
        JSONArray arr = obj.optJSONArray("keys");
        if (arr == null) {
            arr = new JSONArray();
        }
        return parseKeysJsonArray(arr);
    }

    @NonNull
    public List<TrustedSigningKey> parseKeysJsonArray(@NonNull JSONArray arr) throws JSONException {
        List<TrustedSigningKey> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.getJSONObject(i);
            TrustedSigningKey k = new TrustedSigningKey();
            k.setKeyId(item.optString("keyId", item.optString("key_id", "")));
            k.setPublicKeyBase64(item.optString("publicKey", item.optString("public_key", "")));
            k.setAlgorithm(item.optString("algorithm", "Ed25519"));
            k.setAuthorityId(item.optString("authorityId", item.optString("authority_id", "")));

            k.setValidFrom(parseTimestamp(item, "validFrom", "valid_from"));
            k.setValidUntil(parseTimestamp(item, "validUntil", "valid_until"));
            k.setKeyStatus(item.optString("keyStatus", item.optString("key_status", TrustedSigningKey.STATUS_ACTIVE)));

            if (item.has("revokedAt") && !item.isNull("revokedAt")) {
                k.setRevokedAt(item.optLong("revokedAt"));
            } else if (item.has("revoked_at") && !item.isNull("revoked_at")) {
                k.setRevokedAt(parseTimestamp(item, "revoked_at", "revoked_at"));
            }

            k.setRevocationReason(item.optString("revocationReason", item.optString("revocation_reason", null)));
            list.add(k);
        }
        return list;
    }

    private long parseTimestamp(JSONObject item, String key1, String key2) {
        if (item.has(key1)) {
            Object val = item.opt(key1);
            if (val instanceof Number) return ((Number) val).longValue();
            if (val instanceof String) return parseIso((String) val);
        }
        if (item.has(key2)) {
            Object val = item.opt(key2);
            if (val instanceof Number) return ((Number) val).longValue();
            if (val instanceof String) return parseIso((String) val);
        }
        return 0L;
    }

    private long parseIso(String iso) {
        try {
            return java.time.Instant.parse(iso).toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }

    private void setupHeaders(HttpURLConnection conn, String method) throws IOException {
        conn.setRequestMethod(method);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("apikey", anonKey);
        conn.setRequestProperty("Authorization", "Bearer " + anonKey);
        conn.setRequestProperty("Accept", "application/json");
    }

    private String readStream(@Nullable InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString().trim();
        }
    }
}
