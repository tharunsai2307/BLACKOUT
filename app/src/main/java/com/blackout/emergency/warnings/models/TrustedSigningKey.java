package com.blackout.emergency.warnings.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.Serializable;
import java.util.Objects;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Model representing a trusted public verification key in the BLACKOUT key registry (Phase 7).
 */
public class TrustedSigningKey implements Serializable {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_RETIRED = "RETIRED";
    public static final String STATUS_REVOKED = "REVOKED";

    private String keyId;
    private String publicKeyBase64; // Raw 32 bytes Base64
    private String algorithm = "Ed25519";
    private String authorityId;
    private long validFrom;
    private long validUntil;
    private String keyStatus = STATUS_ACTIVE;
    private Long revokedAt;
    private String revocationReason;
    private long createdAt;

    public TrustedSigningKey() {
        this.createdAt = System.currentTimeMillis();
    }

    public TrustedSigningKey(@NonNull String keyId, @NonNull String publicKeyBase64,
                             @NonNull String algorithm, @NonNull String authorityId,
                             long validFrom, long validUntil, @NonNull String keyStatus) {
        this.keyId = keyId;
        this.publicKeyBase64 = publicKeyBase64;
        this.algorithm = algorithm;
        this.authorityId = authorityId;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.keyStatus = keyStatus;
        this.createdAt = System.currentTimeMillis();
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getPublicKeyBase64() {
        return publicKeyBase64;
    }

    public void setPublicKeyBase64(String publicKeyBase64) {
        this.publicKeyBase64 = publicKeyBase64;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getAuthorityId() {
        return authorityId;
    }

    public void setAuthorityId(String authorityId) {
        this.authorityId = authorityId;
    }

    public long getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(long validFrom) {
        this.validFrom = validFrom;
    }

    public long getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(long validUntil) {
        this.validUntil = validUntil;
    }

    public String getKeyStatus() {
        return keyStatus;
    }

    public void setKeyStatus(String keyStatus) {
        this.keyStatus = keyStatus;
    }

    public Long getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Long revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public void setRevocationReason(String revocationReason) {
        this.revocationReason = revocationReason;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isCurrentlyValid() {
        if (!STATUS_ACTIVE.equals(keyStatus)) {
            return false;
        }
        long now = System.currentTimeMillis();
        return now >= validFrom && now <= validUntil;
    }

    public boolean isRevoked() {
        return STATUS_REVOKED.equals(keyStatus);
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("keyId", keyId);
        json.put("publicKey", publicKeyBase64);
        json.put("algorithm", algorithm);
        json.put("authorityId", authorityId);
        json.put("validFrom", validFrom);
        json.put("validUntil", validUntil);
        json.put("keyStatus", keyStatus);
        if (revokedAt != null) json.put("revokedAt", revokedAt);
        if (revocationReason != null) json.put("revocationReason", revocationReason);
        json.put("createdAt", createdAt);
        return json;
    }

    @NonNull
    public static TrustedSigningKey fromJson(@NonNull JSONObject json) throws JSONException {
        TrustedSigningKey k = new TrustedSigningKey();
        k.setKeyId(json.getString("keyId"));
        k.setPublicKeyBase64(json.optString("publicKey", json.optString("public_key", "")));
        k.setAlgorithm(json.optString("algorithm", "Ed25519"));
        k.setAuthorityId(json.optString("authorityId", json.optString("authority_id", "")));
        k.setValidFrom(json.optLong("validFrom", json.optLong("valid_from", 0)));
        k.setValidUntil(json.optLong("validUntil", json.optLong("valid_until", 0)));
        k.setKeyStatus(json.optString("keyStatus", json.optString("key_status", STATUS_ACTIVE)));
        if (json.has("revokedAt") && !json.isNull("revokedAt")) {
            k.setRevokedAt(json.getLong("revokedAt"));
        } else if (json.has("revoked_at") && !json.isNull("revoked_at")) {
            k.setRevokedAt(json.getLong("revoked_at"));
        }
        if (json.has("revocationReason")) {
            k.setRevocationReason(json.getString("revocationReason"));
        } else if (json.has("revocation_reason")) {
            k.setRevocationReason(json.getString("revocation_reason"));
        }
        k.setCreatedAt(json.optLong("createdAt", json.optLong("created_at", System.currentTimeMillis())));
        return k;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrustedSigningKey that = (TrustedSigningKey) o;
        return Objects.equals(keyId, that.keyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyId);
    }
}

