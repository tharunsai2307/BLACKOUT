package com.blackout.emergency.warnings.crypto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.blackout.emergency.warnings.models.TrustedSigningKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory and persistent repository of trusted public verification keys (Phase 7).
 * Pre-seeds trusted root keys and accepts authenticated dynamic key updates.
 */
public class TrustedKeyRepository {

    public static final String ROOT_KEY_ID = "KEY-BLACKOUT-ROOT-2026";
    public static final String ROOT_PUBLIC_KEY_BASE64 = "k5EwtLq5Md0tNFqNxdqdm2vL6c/q1ZgHcb6Uku32/Xc=";
    public static final String ROOT_AUTHORITY_ID = "BLACKOUT Civil Defense & Emergency Response Administration";

    private static volatile TrustedKeyRepository instance;
    private final Map<String, TrustedSigningKey> keys = new ConcurrentHashMap<>();

    public TrustedKeyRepository() {
        seedRootKeys();
    }

    public static synchronized TrustedKeyRepository getInstance() {
        if (instance == null) {
            instance = new TrustedKeyRepository();
        }
        return instance;
    }

    private void seedRootKeys() {
        TrustedSigningKey rootKey = new TrustedSigningKey(
                ROOT_KEY_ID,
                ROOT_PUBLIC_KEY_BASE64,
                "Ed25519",
                ROOT_AUTHORITY_ID,
                1735689600000L, // 2025-01-01T00:00:00Z
                2082758400000L, // 2036-01-01T00:00:00Z
                TrustedSigningKey.STATUS_ACTIVE
        );
        keys.put(rootKey.getKeyId(), rootKey);
    }

    @Nullable
    public TrustedSigningKey getKey(@NonNull String keyId) {
        return keys.get(keyId);
    }

    public void registerKey(@NonNull TrustedSigningKey key) {
        keys.put(key.getKeyId(), key);
    }

    public void registerKeys(@NonNull List<TrustedSigningKey> keyList) {
        for (TrustedSigningKey k : keyList) {
            if (k != null && k.getKeyId() != null) {
                keys.put(k.getKeyId(), k);
            }
        }
    }

    public boolean isKeyTrusted(@NonNull String keyId) {
        TrustedSigningKey k = keys.get(keyId);
        return k != null && k.isCurrentlyValid();
    }

    public void revokeKey(@NonNull String keyId, @Nullable String reason) {
        TrustedSigningKey k = keys.get(keyId);
        if (k != null) {
            k.setKeyStatus(TrustedSigningKey.STATUS_REVOKED);
            k.setRevokedAt(System.currentTimeMillis());
            k.setRevocationReason(reason != null ? reason : "Administrative revocation");
        }
    }

    @NonNull
    public List<TrustedSigningKey> getAllKeys() {
        return new ArrayList<>(keys.values());
    }

    public void reset() {
        keys.clear();
        seedRootKeys();
    }
}

