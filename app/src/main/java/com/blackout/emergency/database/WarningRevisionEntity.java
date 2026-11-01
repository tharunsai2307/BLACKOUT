package com.blackout.emergency.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Database entity storing historical revisions of public emergency warnings.
 */
@Entity(
        tableName = "warning_revisions",
        indices = {
                @Index(value = {"warningId", "revision"}, unique = true),
                @Index(value = {"warningId"})
        }
)
public class WarningRevisionEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String warningId;
    private int revision;
    private String title;
    private String description;
    private String category;
    private String severity;
    private String status;
    private String issuingAuthority;
    private String signingKeyId;
    private String signatureAlgorithm;
    private String signature;
    private String canonicalPayload;
    private String affectedRegionsJson;
    private Double latitude;
    private Double longitude;
    private Double radiusMeters;
    private String language;
    private long issuedAt;
    private long effectiveAt;
    private long expiresAt;
    private long receivedAt;

    public WarningRevisionEntity() {
        this.warningId = "";
        this.revision = 1;
        this.receivedAt = System.currentTimeMillis();
    }

    public WarningRevisionEntity(@NonNull String warningId, int revision, String title,
                                 String description, String category, String severity,
                                 String status, String issuingAuthority, String signingKeyId,
                                 String signatureAlgorithm, String signature,
                                 String canonicalPayload, String affectedRegionsJson,
                                 Double latitude, Double longitude, Double radiusMeters,
                                 String language, long issuedAt, long effectiveAt,
                                 long expiresAt, long receivedAt) {
        this.warningId = warningId;
        this.revision = revision;
        this.title = title;
        this.description = description;
        this.category = category;
        this.severity = severity;
        this.status = status;
        this.issuingAuthority = issuingAuthority;
        this.signingKeyId = signingKeyId;
        this.signatureAlgorithm = signatureAlgorithm;
        this.signature = signature;
        this.canonicalPayload = canonicalPayload;
        this.affectedRegionsJson = affectedRegionsJson;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusMeters = radiusMeters;
        this.language = language;
        this.issuedAt = issuedAt;
        this.effectiveAt = effectiveAt;
        this.expiresAt = expiresAt;
        this.receivedAt = receivedAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getWarningId() {
        return warningId;
    }

    public void setWarningId(@NonNull String warningId) {
        this.warningId = warningId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIssuingAuthority() {
        return issuingAuthority;
    }

    public void setIssuingAuthority(String issuingAuthority) {
        this.issuingAuthority = issuingAuthority;
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

    public long getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(long receivedAt) {
        this.receivedAt = receivedAt;
    }
}

