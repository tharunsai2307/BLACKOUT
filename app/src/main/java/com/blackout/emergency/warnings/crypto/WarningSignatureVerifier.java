package com.blackout.emergency.warnings.crypto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.blackout.emergency.warnings.models.PublicWarning;
import com.blackout.emergency.warnings.models.TrustedSigningKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

/**
 * Offline cryptographic signature verifier for public emergency warnings (Phase 7).
 * Implements strict RFC 8032 Ed25519 signature verification against local trusted public keys,
 * deterministic canonical content reconstruction, schema validation, and lifecycle checks.
 */
public class WarningSignatureVerifier {

    public static class VerificationResult {
        private final boolean isAuthentic;
        private final String status;
        private final String message;
        private final String canonicalPayload;

        public VerificationResult(boolean isAuthentic, @NonNull String status,
                                  @NonNull String message, @Nullable String canonicalPayload) {
            this.isAuthentic = isAuthentic;
            this.status = status;
            this.message = message;
            this.canonicalPayload = canonicalPayload;
        }

        public boolean isAuthentic() {
            return isAuthentic;
        }

        @NonNull
        public String getStatus() {
            return status;
        }

        @NonNull
        public String getMessage() {
            return message;
        }

        @Nullable
        public String getCanonicalPayload() {
            return canonicalPayload;
        }
    }

    private final TrustedKeyRepository keyRepository;

    public WarningSignatureVerifier(@NonNull TrustedKeyRepository keyRepository) {
        this.keyRepository = keyRepository;
    }

    /**
     * Executes complete 10-step verification workflow as defined in Section 9.
     */
    @NonNull
    public VerificationResult verify(@NonNull PublicWarning warning) {
        // Step 1 & 2: Schema and protocol validation
        if (warning.getWarningId() == null || warning.getWarningId().trim().isEmpty()) {
            return new VerificationResult(false, PublicWarning.VERIFY_INVALID_PAYLOAD, "Missing warningId", null);
        }
        if (warning.getProtocolVersion() != 1) {
            return new VerificationResult(false, PublicWarning.VERIFY_INVALID_PAYLOAD,
                    "Unsupported protocolVersion: " + warning.getProtocolVersion(), null);
        }
        if (warning.getRevision() < 1) {
            return new VerificationResult(false, PublicWarning.VERIFY_INVALID_PAYLOAD,
                    "Invalid revision number: " + warning.getRevision(), null);
        }
        if (warning.getTitle() == null || warning.getTitle().trim().isEmpty()) {
            return new VerificationResult(false, PublicWarning.VERIFY_INVALID_PAYLOAD, "Missing warning title", null);
        }
        if (warning.getDescription() == null || warning.getDescription().trim().isEmpty()) {
            return new VerificationResult(false, PublicWarning.VERIFY_INVALID_PAYLOAD, "Missing warning description", null);
        }
        if (warning.getCategory() == null || warning.getCategory().trim().isEmpty()) {
            return new VerificationResult(false, PublicWarning.VERIFY_INVALID_PAYLOAD, "Missing warning category", null);
        }
        if (warning.getSeverity() == null || warning.getSeverity().trim().isEmpty()) {
            return new VerificationResult(false, PublicWarning.VERIFY_INVALID_PAYLOAD, "Missing warning severity", null);
        }
        if (warning.getSigningKeyId() == null || warning.getSigningKeyId().trim().isEmpty()) {
            return new VerificationResult(false, PublicWarning.VERIFY_INVALID_PAYLOAD, "Missing signingKeyId", null);
        }
        if (warning.getSignature() == null || warning.getSignature().trim().isEmpty()) {
            return new VerificationResult(false, PublicWarning.VERIFY_INVALID_PAYLOAD, "Missing digital signature", null);
        }

        // Step 3: Retrieve trusted public key
        TrustedSigningKey key = keyRepository.getKey(warning.getSigningKeyId());
        if (key == null) {
            return new VerificationResult(false, PublicWarning.VERIFY_UNKNOWN_SIGNING_KEY,
                    "Signing key [" + warning.getSigningKeyId() + "] is unknown to local trust registry", null);
        }

        // Step 4: Check key status
        if (key.isRevoked()) {
            return new VerificationResult(false, PublicWarning.VERIFY_REVOKED_SIGNING_KEY,
                    "Signing key [" + warning.getSigningKeyId() + "] has been REVOKED (" + key.getRevocationReason() + ")", null);
        }

        // Step 5: Reconstruct canonical signed content
        String reconstructedCanonical = WarningCanonicalSerializer.buildCanonicalString(warning);

        // Step 6 & 7: Cryptographic Ed25519 signature verification
        boolean signatureValid = verifyEd25519Signature(
                key.getPublicKeyBase64(),
                reconstructedCanonical.getBytes(StandardCharsets.UTF_8),
                warning.getSignature()
        );

        if (!signatureValid) {
            return new VerificationResult(false, PublicWarning.VERIFY_INVALID_SIGNATURE,
                    "Cryptographic signature verification failed: content has been altered or signature is invalid",
                    reconstructedCanonical);
        }

        // Step 8: Validate timestamps
        long now = System.currentTimeMillis();
        if (warning.getExpiresAt() <= warning.getIssuedAt()) {
            return new VerificationResult(false, PublicWarning.VERIFY_INVALID_PAYLOAD,
                    "expiresAt (" + warning.getExpiresAt() + ") cannot be earlier than or equal to issuedAt (" + warning.getIssuedAt() + ")",
                    reconstructedCanonical);
        }

        // Revocation check
        if (PublicWarning.STATUS_REVOKED.equalsIgnoreCase(warning.getStatus())) {
            return new VerificationResult(true, PublicWarning.VERIFY_REVOKED_WARNING,
                    "Warning signature verified, but warning has been formally REVOKED",
                    reconstructedCanonical);
        }

        if (now > warning.getExpiresAt()) {
            return new VerificationResult(false, PublicWarning.VERIFY_EXPIRED,
                    "Warning has EXPIRED at " + warning.getExpiresAt(),
                    reconstructedCanonical);
        }

        if (now < warning.getEffectiveAt() - 300000L) { // 5 min clock skew tolerance
            return new VerificationResult(false, PublicWarning.VERIFY_NOT_YET_EFFECTIVE,
                    "Warning is NOT YET EFFECTIVE until " + warning.getEffectiveAt(),
                    reconstructedCanonical);
        }

        // Step 9 & 10: All checks passed
        return new VerificationResult(true, PublicWarning.VERIFY_VERIFIED,
                "Cryptographically verified official warning from " + warning.getIssuingAuthority(),
                reconstructedCanonical);
    }

    /**
     * Verifies raw Ed25519 signature using Bouncy Castle's pure-Java RFC 8032 implementation.
     */
    public static boolean verifyEd25519Signature(@NonNull String publicKeyBase64,
                                                 @NonNull byte[] contentBytes,
                                                 @NonNull String signatureBase64) {
        try {
            byte[] rawPubKey = Base64.getDecoder().decode(publicKeyBase64.trim());
            byte[] rawSig = Base64.getDecoder().decode(signatureBase64.trim());

            if (rawPubKey.length != 32) {
                // If the key is SPKI encoded (44 bytes), extract the trailing 32 bytes
                if (rawPubKey.length == 44) {
                    byte[] extracted = new byte[32];
                    System.arraycopy(rawPubKey, 12, extracted, 0, 32);
                    rawPubKey = extracted;
                } else {
                    return false;
                }
            }

            if (rawSig.length != 64) {
                return false;
            }

            Ed25519PublicKeyParameters pubParams = new Ed25519PublicKeyParameters(rawPubKey, 0);
            Ed25519Signer signer = new Ed25519Signer();
            signer.init(false, pubParams);
            signer.update(contentBytes, 0, contentBytes.length);

            return signer.verifySignature(rawSig);

        } catch (Exception e) {
            return false;
        }
    }
}
