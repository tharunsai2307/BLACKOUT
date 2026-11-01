package com.blackout.emergency.warnings.crypto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.blackout.emergency.warnings.models.PublicWarning;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic canonical serialization for BLACKOUT public emergency warnings (Phase 7).
 * Ensures unambiguous, cross-platform string representation for cryptographic signing
 * and signature verification across Supabase Edge Functions, Node.js, and Android.
 */
public final class WarningCanonicalSerializer {

    public static final String HEADER_V1 = "BLACKOUT_WARNING_V1";

    private WarningCanonicalSerializer() {
        // Prevent instantiation
    }

    /**
     * Reconstructs the canonical string representation for a given PublicWarning object.
     * The signature field itself is strictly excluded.
     */
    @NonNull
    public static String buildCanonicalString(@NonNull PublicWarning warning) {
        return buildCanonicalString(
                warning.getWarningId(),
                warning.getProtocolVersion(),
                warning.getRevision(),
                warning.getStatus(),
                warning.getCategory(),
                warning.getSeverity(),
                warning.getIssuingAuthority(),
                warning.getTitle(),
                warning.getDescription(),
                warning.getIssuedAt(),
                warning.getEffectiveAt(),
                warning.getExpiresAt(),
                warning.getLanguage(),
                warning.getSigningKeyId(),
                warning.getSignatureAlgorithm(),
                warning.getLatitude(),
                warning.getLongitude(),
                warning.getRadiusMeters(),
                warning.getAffectedRegions()
        );
    }

    /**
     * Builds canonical representation from individual fields.
     */
    @NonNull
    public static String buildCanonicalString(
            @Nullable String warningId,
            int protocolVersion,
            int revision,
            @Nullable String status,
            @Nullable String category,
            @Nullable String severity,
            @Nullable String issuingAuthority,
            @Nullable String title,
            @Nullable String description,
            long issuedAt,
            long effectiveAt,
            long expiresAt,
            @Nullable String language,
            @Nullable String signingKeyId,
            @Nullable String signatureAlgorithm,
            @Nullable Double latitude,
            @Nullable Double longitude,
            @Nullable Double radiusMeters,
            @Nullable List<String> affectedRegions) {

        List<String> sortedRegions = new ArrayList<>();
        if (affectedRegions != null) {
            for (String r : affectedRegions) {
                if (r != null && !r.trim().isEmpty()) {
                    sortedRegions.add(r.trim());
                }
            }
            Collections.sort(sortedRegions);
        }
        String regionsStr = String.join(",", sortedRegions);

        String latStr = latitude != null ? String.format(Locale.US, "%.6f", latitude) : "null";
        String lonStr = longitude != null ? String.format(Locale.US, "%.6f", longitude) : "null";
        String radStr = radiusMeters != null ? String.format(Locale.US, "%.2f", radiusMeters) : "null";

        StringBuilder sb = new StringBuilder();
        sb.append(HEADER_V1).append('\n');
        sb.append("warningId=").append(warningId != null ? warningId.trim() : "").append('\n');
        sb.append("protocolVersion=").append(protocolVersion).append('\n');
        sb.append("revision=").append(revision).append('\n');
        sb.append("status=").append(status != null ? status.trim().toUpperCase(Locale.ROOT) : "").append('\n');
        sb.append("category=").append(category != null ? category.trim().toUpperCase(Locale.ROOT) : "").append('\n');
        sb.append("severity=").append(severity != null ? severity.trim().toUpperCase(Locale.ROOT) : "").append('\n');
        sb.append("issuingAuthority=").append(issuingAuthority != null ? issuingAuthority.trim() : "").append('\n');
        sb.append("title=").append(title != null ? title.trim() : "").append('\n');
        sb.append("description=").append(description != null ? description.trim() : "").append('\n');
        sb.append("issuedAt=").append(issuedAt).append('\n');
        sb.append("effectiveAt=").append(effectiveAt).append('\n');
        sb.append("expiresAt=").append(expiresAt).append('\n');
        sb.append("language=").append(language != null ? language.trim().toLowerCase(Locale.ROOT) : "en").append('\n');
        sb.append("signingKeyId=").append(signingKeyId != null ? signingKeyId.trim() : "").append('\n');
        sb.append("signatureAlgorithm=").append(signatureAlgorithm != null ? signatureAlgorithm.trim() : "Ed25519").append('\n');
        sb.append("latitude=").append(latStr).append('\n');
        sb.append("longitude=").append(lonStr).append('\n');
        sb.append("radiusMeters=").append(radStr).append('\n');
        sb.append("affectedRegions=").append(regionsStr);

        return sb.toString();
    }

    /**
     * Returns canonical UTF-8 bytes to be signed or verified.
     */
    @NonNull
    public static byte[] getCanonicalBytes(@NonNull PublicWarning warning) {
        return buildCanonicalString(warning).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Computes SHA-256 hex hash of the canonical representation for tamper checking and inventory matching.
     */
    @NonNull
    public static String computePayloadHash(@NonNull String canonicalPayload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }
}

