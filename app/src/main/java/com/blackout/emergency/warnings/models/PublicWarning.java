package com.blackout.emergency.warnings.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Core model representing a digitally signed public emergency warning (Phase 7).
 * Encapsulates versioned warning payload, cryptographic signature metadata,
 * geographic relevance parameters, and verification lifecycle status.
 */
public class PublicWarning implements Serializable {

    // Status Enums (Phase 7 Protocol)
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SUBMITTED = "SUBMITTED_FOR_APPROVAL";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_SIGNED = "SIGNED";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_REVOKED = "REVOKED";

    // Verification Statuses (Section 9 Specification)
    public static final String VERIFY_VERIFIED = "VERIFIED";
    public static final String VERIFY_INVALID_SIGNATURE = "INVALID_SIGNATURE";
    public static final String VERIFY_UNKNOWN_SIGNING_KEY = "UNKNOWN_SIGNING_KEY";
    public static final String VERIFY_REVOKED_SIGNING_KEY = "REVOKED_SIGNING_KEY";
    public static final String VERIFY_EXPIRED = "EXPIRED";
    public static final String VERIFY_NOT_YET_EFFECTIVE = "NOT_YET_EFFECTIVE";
    public static final String VERIFY_INVALID_PAYLOAD = "INVALID_PAYLOAD";
    public static final String VERIFY_REVOKED_WARNING = "REVOKED_WARNING";
    public static final String VERIFY_PENDING_KEY_UPDATE = "PENDING_KEY_UPDATE";

    // Supported Categories (Section 3 Specification)
    public static final String CATEGORY_FLOOD = "FLOOD";
    public static final String CATEGORY_CYCLONE = "CYCLONE";
    public static final String CATEGORY_HEAVY_RAINFALL = "HEAVY_RAINFALL";
    public static final String CATEGORY_LANDSLIDE = "LANDSLIDE";
    public static final String CATEGORY_EARTHQUAKE = "EARTHQUAKE";
    public static final String CATEGORY_FIRE = "FIRE";
    public static final String CATEGORY_EVACUATION = "EVACUATION";
    public static final String CATEGORY_INFRASTRUCTURE = "INFRASTRUCTURE_EMERGENCY";
    public static final String CATEGORY_SAFETY = "GENERAL_PUBLIC_SAFETY";

    // Supported Severities (Section 3 Specification)
    public static final String SEVERITY_INFORMATIONAL = "INFORMATIONAL";
    public static final String SEVERITY_ADVISORY = "ADVISORY";
    public static final String SEVERITY_WATCH = "WATCH";
    public static final String SEVERITY_WARNING = "WARNING";
    public static final String SEVERITY_CRITICAL = "CRITICAL";

    private String warningId;
    private int protocolVersion = 1;
    private int revision = 1;
    private String title;
    private String description;
    private String category;
    private String severity;
    private String issuingAuthority;
    private long issuedAt;
    private long effectiveAt;
    private long expiresAt;
    private List<String> affectedRegions = new ArrayList<>();
    private Double latitude;
    private Double longitude;
    private Double radiusMeters;
    private String language = "en";
    private String status = STATUS_PUBLISHED;

    // Cryptographic Fields
    private String signingKeyId;
    private String signatureAlgorithm = "Ed25519";
    private String signature; // Base64
    private String canonicalPayload;

    // Local Tracking Metadata
    private String verificationStatus = VERIFY_INVALID_PAYLOAD;
    private long lastReceivedAt;
    private long lastRelayedAt;
    private String sourceDeviceId;
    private String payloadHash;

    public PublicWarning() {
        this.lastReceivedAt = System.currentTimeMillis();
    }

    public PublicWarning(@NonNull String warningId, int protocolVersion, int revision,
                         @NonNull String title, @NonNull String description,
                         @NonNull String category, @NonNull String severity,
                         @NonNull String issuingAuthority, long issuedAt, long effectiveAt,
                         long expiresAt, @Nullable List<String> affectedRegions,
                         @Nullable Double latitude, @Nullable Double longitude,
                         @Nullable Double radiusMeters, @Nullable String language,
                         @NonNull String status, @NonNull String signingKeyId,
                         @NonNull String signatureAlgorithm, @NonNull String signature,
                         @Nullable String canonicalPayload) {
        this.warningId = warningId;
        this.protocolVersion = protocolVersion;
        this.revision = revision;
        this.title = title;
        this.description = description;
        this.category = category;
        this.severity = severity;
        this.issuingAuthority = issuingAuthority;
        this.issuedAt = issuedAt;
        this.effectiveAt = effectiveAt;
        this.expiresAt = expiresAt;
        if (affectedRegions != null) {
            this.affectedRegions.addAll(affectedRegions);
        }
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusMeters = radiusMeters;
        this.language = language != null ? language : "en";
        this.status = status;
        this.signingKeyId = signingKeyId;
        this.signatureAlgorithm = signatureAlgorithm != null ? signatureAlgorithm : "Ed25519";
        this.signature = signature;
        this.canonicalPayload = canonicalPayload;
        this.lastReceivedAt = System.currentTimeMillis();
    }

    // Getters and Setters

    public String getWarningId() {
        return warningId;
    }

    public void setWarningId(String warningId) {
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

    public List<String> getAffectedRegions() {
        return affectedRegions;
    }

    public void setAffectedRegions(List<String> affectedRegions) {
        this.affectedRegions = affectedRegions != null ? affectedRegions : new ArrayList<>();
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

    public boolean isAuthenticAndVerified() {
        return VERIFY_VERIFIED.equals(verificationStatus);
    }

    public boolean isExpiredNow() {
        return System.currentTimeMillis() > expiresAt;
    }

    public boolean isEffectiveNow() {
        return System.currentTimeMillis() >= effectiveAt;
    }

    // JSON Serialization for Network Transfer and Relay Packets
    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("warningId", warningId);
        json.put("protocolVersion", protocolVersion);
        json.put("revision", revision);
        json.put("title", title);
        json.put("description", description);
        json.put("category", category);
        json.put("severity", severity);
        json.put("issuingAuthority", issuingAuthority);
        json.put("issuedAt", issuedAt);
        json.put("effectiveAt", effectiveAt);
        json.put("expiresAt", expiresAt);

        JSONArray regions = new JSONArray();
        for (String r : affectedRegions) {
            regions.put(r);
        }
        json.put("affectedRegions", regions);

        if (latitude != null) json.put("latitude", latitude);
        if (longitude != null) json.put("longitude", longitude);
        if (radiusMeters != null) json.put("radiusMeters", radiusMeters);

        json.put("language", language);
        json.put("status", status);
        json.put("signingKeyId", signingKeyId);
        json.put("signatureAlgorithm", signatureAlgorithm);
        json.put("signature", signature);
        if (canonicalPayload != null) {
            json.put("canonicalPayload", canonicalPayload);
        }
        return json;
    }

    @NonNull
    public static PublicWarning fromJson(@NonNull JSONObject json) throws JSONException {
        PublicWarning w = new PublicWarning();
        w.setWarningId(json.getString("warningId"));
        w.setProtocolVersion(json.optInt("protocolVersion", 1));
        w.setRevision(json.optInt("revision", 1));
        w.setTitle(json.getString("title"));
        w.setDescription(json.getString("description"));
        w.setCategory(json.getString("category"));
        w.setSeverity(json.getString("severity"));
        w.setIssuingAuthority(json.getString("issuingAuthority"));
        w.setIssuedAt(json.getLong("issuedAt"));
        w.setEffectiveAt(json.getLong("effectiveAt"));
        w.setExpiresAt(json.getLong("expiresAt"));

        JSONArray regions = json.optJSONArray("affectedRegions");
        List<String> regList = new ArrayList<>();
        if (regions != null) {
            for (int i = 0; i < regions.length(); i++) {
                regList.add(regions.getString(i));
            }
        }
        w.setAffectedRegions(regList);

        if (json.has("latitude") && !json.isNull("latitude")) {
            w.setLatitude(json.getDouble("latitude"));
        }
        if (json.has("longitude") && !json.isNull("longitude")) {
            w.setLongitude(json.getDouble("longitude"));
        }
        if (json.has("radiusMeters") && !json.isNull("radiusMeters")) {
            w.setRadiusMeters(json.getDouble("radiusMeters"));
        }

        w.setLanguage(json.optString("language", "en"));
        w.setStatus(json.optString("status", STATUS_PUBLISHED));
        w.setSigningKeyId(json.getString("signingKeyId"));
        w.setSignatureAlgorithm(json.optString("signatureAlgorithm", "Ed25519"));
        w.setSignature(json.getString("signature"));
        w.setCanonicalPayload(json.optString("canonicalPayload", null));

        return w;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PublicWarning that = (PublicWarning) o;
        return revision == that.revision && Objects.equals(warningId, that.warningId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(warningId, revision);
    }
}
