package com.blackout.emergency.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import com.blackout.emergency.warnings.models.PublicWarning;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * Room Database entity representing a digitally signed public emergency warning (Phase 7).
 */
@Entity(
        tableName = "public_warnings",
        indices = {
                @Index(value = {"status"}),
                @Index(value = {"verificationStatus"}),
                @Index(value = {"expiresAt"}),
                @Index(value = {"signingKeyId"})
        }
)
public class PublicWarningEntity {

    @PrimaryKey
    @NonNull
    private String warningId;
    private int protocolVersion;
    private int revision;
    private String title;
    private String description;
    private String category;
    private String severity;
    private String issuingAuthority;
    private long issuedAt;
    private long effectiveAt;
    private long expiresAt;
    private String affectedRegionsJson;
    private Double latitude;
    private Double longitude;
    private Double radiusMeters;
    private String language;
    private String status;
    private String signingKeyId;
    private String signatureAlgorithm;
    private String signature;
    private String canonicalPayload;
    private String verificationStatus;
    private long lastReceivedAt;
    private long lastRelayedAt;
    private String sourceDeviceId;
    private String payloadHash;

    public PublicWarningEntity() {
        this.warningId = "";
        this.protocolVersion = 1;
        this.revision = 1;
        this.language = "en";
        this.status = PublicWarning.STATUS_PUBLISHED;
        this.signatureAlgorithm = "Ed25519";
        this.verificationStatus = PublicWarning.VERIFY_INVALID_PAYLOAD;
        this.lastReceivedAt = System.currentTimeMillis();
    }

    public static PublicWarningEntity fromModel(@NonNull PublicWarning model) {
        PublicWarningEntity entity = new PublicWarningEntity();
        entity.setWarningId(model.getWarningId());
        entity.setProtocolVersion(model.getProtocolVersion());
        entity.setRevision(model.getRevision());
        entity.setTitle(model.getTitle());
        entity.setDescription(model.getDescription());
        entity.setCategory(model.getCategory());
        entity.setSeverity(model.getSeverity());
        entity.setIssuingAuthority(model.getIssuingAuthority());
        entity.setIssuedAt(model.getIssuedAt());
        entity.setEffectiveAt(model.getEffectiveAt());
        entity.setExpiresAt(model.getExpiresAt());

        JSONArray arr = new JSONArray();
        if (model.getAffectedRegions() != null) {
            for (String r : model.getAffectedRegions()) {
                arr.put(r);
            }
        }
        entity.setAffectedRegionsJson(arr.toString());

        entity.setLatitude(model.getLatitude());
        entity.setLongitude(model.getLongitude());
        entity.setRadiusMeters(model.getRadiusMeters());
        entity.setLanguage(model.getLanguage());
        entity.setStatus(model.getStatus());
        entity.setSigningKeyId(model.getSigningKeyId());
        entity.setSignatureAlgorithm(model.getSignatureAlgorithm());
        entity.setSignature(model.getSignature());
        entity.setCanonicalPayload(model.getCanonicalPayload());
        entity.setVerificationStatus(model.getVerificationStatus());
        entity.setLastReceivedAt(model.getLastReceivedAt());
        entity.setLastRelayedAt(model.getLastRelayedAt());
        entity.setSourceDeviceId(model.getSourceDeviceId());
        entity.setPayloadHash(model.getPayloadHash());
        return entity;
    }

    @NonNull
    public PublicWarning toModel() {
        List<String> regions = new ArrayList<>();
        if (affectedRegionsJson != null && !affectedRegionsJson.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(affectedRegionsJson);
                for (int i = 0; i < arr.length(); i++) {
                    regions.add(arr.getString(i));
                }
            } catch (JSONException ignored) {}
        }

        PublicWarning w = new PublicWarning(
                this.warningId,
                this.protocolVersion,
                this.revision,
                this.title != null ? this.title : "",
                this.description != null ? this.description : "",
                this.category != null ? this.category : "",
                this.severity != null ? this.severity : "",
                this.issuingAuthority != null ? this.issuingAuthority : "",
                this.issuedAt,
                this.effectiveAt,
                this.expiresAt,
                regions,
                this.latitude,
                this.longitude,
                this.radiusMeters,
                this.language,
                this.status != null ? this.status : PublicWarning.STATUS_PUBLISHED,
                this.signingKeyId != null ? this.signingKeyId : "",
                this.signatureAlgorithm != null ? this.signatureAlgorithm : "Ed25519",
                this.signature != null ? this.signature : "",
                this.canonicalPayload
        );
        w.setVerificationStatus(this.verificationStatus);
        w.setLastReceivedAt(this.lastReceivedAt);
        w.setLastRelayedAt(this.lastRelayedAt);
        w.setSourceDeviceId(this.sourceDeviceId);
        w.setPayloadHash(this.payloadHash);
        return w;
    }

    @NonNull
    public String getWarningId() {
        return warningId;
    }

    public void setWarningId(@NonNull String warningId) {
        this.warningId = warningId;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getIssuingAuthority() {
        return issuingAuthority;
    }

    public void setIssuingAuthority(String issuingAuthority) {
        this.issuingAuthority = issuingAuthority;
    }

    public long getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(long issuedAt) {
        this.issuedAt = issuedAt;
    }

    public long getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(long effectiveAt) {
        this.effectiveAt = effectiveAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getAffectedRegionsJson() {
        return affectedRegionsJson;
    }

    public void setAffectedRegionsJson(String affectedRegionsJson) {
        this.affectedRegionsJson = affectedRegionsJson;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getRadiusMeters() {
        return radiusMeters;
    }

    public void setRadiusMeters(Double radiusMeters) {
        this.radiusMeters = radiusMeters;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSigningKeyId() {
        return signingKeyId;
    }

    public void setSigningKeyId(String signingKeyId) {
        this.signingKeyId = signingKeyId;
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    public void setSignatureAlgorithm(String signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getCanonicalPayload() {
        return canonicalPayload;
    }

    public void setCanonicalPayload(String canonicalPayload) {
        this.canonicalPayload = canonicalPayload;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public long getLastReceivedAt() {
        return lastReceivedAt;
    }

    public void setLastReceivedAt(long lastReceivedAt) {
        this.lastReceivedAt = lastReceivedAt;
    }

    public long getLastRelayedAt() {
        return lastRelayedAt;
    }

    public void setLastRelayedAt(long lastRelayedAt) {
        this.lastRelayedAt = lastRelayedAt;
    }

    public String getSourceDeviceId() {
        return sourceDeviceId;
    }

    public void setSourceDeviceId(String sourceDeviceId) {
        this.sourceDeviceId = sourceDeviceId;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }
}
