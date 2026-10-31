package com.blackout.emergency.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import com.blackout.emergency.warnings.models.TrustedSigningKey;

/**
 * Room Database entity storing trusted public verification keys for offline verification.
 */
@Entity(
        tableName = "trusted_signing_keys",
        indices = {
                @Index(value = {"keyStatus"}),
                @Index(value = {"authorityId"})
        }
)
public class TrustedSigningKeyEntity {

    @PrimaryKey
    @NonNull
    private String keyId;
    private String publicKeyBase64;
    private String algorithm;
    private String authorityId;
    private long validFrom;
    private long validUntil;
    private String keyStatus;
    private Long revokedAt;
    private String revocationReason;
    private long createdAt;

    public TrustedSigningKeyEntity() {
        this.keyId = "";
        this.algorithm = "Ed25519";
        this.keyStatus = TrustedSigningKey.STATUS_ACTIVE;
        this.createdAt = System.currentTimeMillis();
    }

    public static TrustedSigningKeyEntity fromModel(@NonNull TrustedSigningKey model) {
        TrustedSigningKeyEntity entity = new TrustedSigningKeyEntity();
        entity.setKeyId(model.getKeyId());
        entity.setPublicKeyBase64(model.getPublicKeyBase64());
        entity.setAlgorithm(model.getAlgorithm());
        entity.setAuthorityId(model.getAuthorityId());
        entity.setValidFrom(model.getValidFrom());
        entity.setValidUntil(model.getValidUntil());
        entity.setKeyStatus(model.getKeyStatus());
        entity.setRevokedAt(model.getRevokedAt());
        entity.setRevocationReason(model.getRevocationReason());
        entity.setCreatedAt(model.getCreatedAt());
        return entity;
    }

    @NonNull
    public TrustedSigningKey toModel() {
        TrustedSigningKey k = new TrustedSigningKey(
                this.keyId,
                this.publicKeyBase64 != null ? this.publicKeyBase64 : "",
                this.algorithm != null ? this.algorithm : "Ed25519",
                this.authorityId != null ? this.authorityId : "",
                this.validFrom,
                this.validUntil,
                this.keyStatus != null ? this.keyStatus : TrustedSigningKey.STATUS_ACTIVE
        );
        k.setRevokedAt(this.revokedAt);
        k.setRevocationReason(this.revocationReason);
        k.setCreatedAt(this.createdAt);
        return k;
    }

    @NonNull
    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(@NonNull String keyId) {
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
}
