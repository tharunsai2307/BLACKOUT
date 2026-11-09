package com.blackout.emergency.nearby;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.blackout.emergency.utils.Constants;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages peer cryptographic sessions, payload integrity verification,
 * and replay attack defense inspired by the BitChat Noise Protocol architecture.
 */
public class PeerSecurityManager {

    private static final String PREF_DEVICE_PUB_KEY = "pref_device_public_key";
    private static final long MAX_CLOCK_SKEW_MS = 2 * 60 * 60 * 1000L; // 2 hours
    private static final int MAX_SEEN_NONCES = 1000;

    private static volatile PeerSecurityManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final String localPublicKey;
    private final SecureRandom secureRandom = new SecureRandom();

    // Bounded LRU cache of recently processed nonces to prevent replay attacks
    private final Map<String, Long> seenNonces = Collections.synchronizedMap(
            new LinkedHashMap<String, Long>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > MAX_SEEN_NONCES;
                }
            }
    );

    private PeerSecurityManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        this.localPublicKey = initOrLoadPublicKey();
    }

    public static synchronized PeerSecurityManager getInstance(Context context) {
        if (instance == null) {
            instance = new PeerSecurityManager(context);
        }
        return instance;
    }

    private String initOrLoadPublicKey() {
        String key = prefs.getString(PREF_DEVICE_PUB_KEY, null);
        if (key == null) {
            byte[] seed = new byte[32];
            secureRandom.nextBytes(seed);
            key = Base64.getEncoder().encodeToString(seed);
            prefs.edit().putString(PREF_DEVICE_PUB_KEY, key).apply();
        }
        return key;
    }

    @NonNull
    public String getLocalPublicKey() {
        return localPublicKey;
    }

    @NonNull
    public String generateNonce() {
        return UUID.randomUUID().toString();
    }

    /**
     * Validates packet timeliness and checks whether the nonce was previously seen.
     * Returns true if packet is fresh and legitimate; false if expired or replayed.
     */
    public boolean validatePacketFreshness(@Nullable String nonce, long packetTimestamp) {
        if (nonce == null || nonce.trim().isEmpty()) {
            return false;
        }

        long now = System.currentTimeMillis();
        // Check clock skew
        if (Math.abs(now - packetTimestamp) > MAX_CLOCK_SKEW_MS) {
            return false;
        }

        // Check replay cache
        synchronized (seenNonces) {
            if (seenNonces.containsKey(nonce)) {
                return false; // Replayed packet
            }
            seenNonces.put(nonce, now);
        }
        return true;
    }

    /**
     * Computes the canonical SHA-256 hash of payload bytes.
     */
    @NonNull
    public static String computeHash(@NonNull byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Verifies that the payload bytes match the provided SHA-256 hash.
     */
    public static boolean verifyHash(@NonNull byte[] bytes, @Nullable String expectedHash) {
        if (expectedHash == null || expectedHash.isEmpty()) {
            return false;
        }
        return computeHash(bytes).equalsIgnoreCase(expectedHash.trim());
    }
}

