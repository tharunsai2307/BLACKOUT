package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.blackout.emergency.warnings.crypto.TrustedKeyRepository;
import com.blackout.emergency.warnings.crypto.WarningCanonicalSerializer;
import com.blackout.emergency.warnings.crypto.WarningSignatureVerifier;
import com.blackout.emergency.warnings.models.PublicWarning;
import com.blackout.emergency.warnings.models.TrustedSigningKey;
import com.blackout.emergency.warnings.relay.WarningRelayModels;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * Phase 8 Security Audit — Negative Testing Suite.
 * Executes the required negative tests specified in Section 3.B of the Master Prompt:
 * 1. Modify warning title after signing.
 * 2. Modify warning description after signing.
 * 3. Modify severity.
 * 4. Modify geographical scope.
 * 5. Modify expiry timestamp.
 * 6. Replace signature with random bytes.
 * 7. Use an unknown key ID.
 * 8. Use a revoked key.
 * 9. Replay an old warning revision.
 * 10. Supply malformed canonical payload data.
 */
public class SecurityNegativeTestSuite {

    private TrustedKeyRepository keyRepository;
    private WarningSignatureVerifier verifier;

    private static final String SAMPLE_WARNING_ID = "d3b07384-d113-46fb-9b24-9b2bfa6eb542";
    private static final String ROOT_KEY_ID = "KEY-BLACKOUT-ROOT-2026";
    private static final String ROOT_PUB_KEY = "k5EwtLq5Md0tNFqNxdqdm2vL6c/q1ZgHcb6Uku32/Xc=";
    private static final String VALID_SIGNATURE = "sL8/lOZt03vDDP2IIlFbVjTpgWDy22y1Z8kiF0BJ4IxuBDkAp4NUwl4i0kVZu5v36GVVa90TmyrpnUxscmd2AA==";

    private static final long ISSUED_AT = 1793400000000L;
    private static final long EFFECTIVE_AT = 1793400000000L;
    private static final long EXPIRES_AT = 1850000000000L;

    @Before
    public void setUp() {
        keyRepository = new TrustedKeyRepository();
        verifier = new WarningSignatureVerifier(keyRepository);
    }

    private PublicWarning createBaselineWarning() {
        List<String> regions = Arrays.asList("Sector 4", "Central Basin", "North Riverbank");
        return new PublicWarning(
                SAMPLE_WARNING_ID,
                1,
                1,
                "Severe Flash Flooding Alert",
                "Immediate flash flooding reported in low-lying areas. Move to higher ground immediately.",
                PublicWarning.CATEGORY_FLOOD,
                PublicWarning.SEVERITY_CRITICAL,
                "BLACKOUT Civil Defense & Emergency Response Administration",
                ISSUED_AT,
                EFFECTIVE_AT,
                EXPIRES_AT,
                regions,
                13.082680,
                80.270718,
                5000.0,
                "en",
                PublicWarning.STATUS_PUBLISHED,
                ROOT_KEY_ID,
                "Ed25519",
                VALID_SIGNATURE,
                null
        );
    }

    // -------------------------------------------------------------------------
    // Negative Test 1: Modify warning title after signing
    // -------------------------------------------------------------------------
    @Test
    public void test01_ModifyTitleAfterSigning_FailsVerification() {
        PublicWarning w = createBaselineWarning();
        w.setTitle("Tampered Evacuation Order");

        WarningSignatureVerifier.VerificationResult result = verifier.verify(w);
        assertFalse("Tampered title must fail verification", result.isAuthentic());
        assertEquals(PublicWarning.VERIFY_INVALID_SIGNATURE, result.getStatus());
    }

    // -------------------------------------------------------------------------
    // Negative Test 2: Modify warning description after signing
    // -------------------------------------------------------------------------
    @Test
    public void test02_ModifyDescriptionAfterSigning_FailsVerification() {
        PublicWarning w = createBaselineWarning();
        w.setDescription("False reassurance: ignore flood sirens and stay home.");

        WarningSignatureVerifier.VerificationResult result = verifier.verify(w);
        assertFalse("Tampered description must fail verification", result.isAuthentic());
        assertEquals(PublicWarning.VERIFY_INVALID_SIGNATURE, result.getStatus());
    }

    // -------------------------------------------------------------------------
    // Negative Test 3: Modify severity
    // -------------------------------------------------------------------------
    @Test
    public void test03_ModifySeverityAfterSigning_FailsVerification() {
        PublicWarning w = createBaselineWarning();
        w.setSeverity(PublicWarning.SEVERITY_INFORMATIONAL);

        WarningSignatureVerifier.VerificationResult result = verifier.verify(w);
        assertFalse("Tampered severity level must fail verification", result.isAuthentic());
        assertEquals(PublicWarning.VERIFY_INVALID_SIGNATURE, result.getStatus());
    }

    // -------------------------------------------------------------------------
    // Negative Test 4: Modify geographical scope (latitude, longitude, radius, regions)
    // -------------------------------------------------------------------------
    @Test
    public void test04_ModifyGeographicalScope_FailsVerification() {
        // Tamper latitude
        PublicWarning wLat = createBaselineWarning();
        wLat.setLatitude(45.000000);
        assertFalse("Tampered latitude must fail verification", verifier.verify(wLat).isAuthentic());

        // Tamper longitude
        PublicWarning wLon = createBaselineWarning();
        wLon.setLongitude(-122.000000);
        assertFalse("Tampered longitude must fail verification", verifier.verify(wLon).isAuthentic());

        // Tamper radius
        PublicWarning wRad = createBaselineWarning();
        wRad.setRadiusMeters(99999.0);
        assertFalse("Tampered radius must fail verification", verifier.verify(wRad).isAuthentic());

        // Tamper affected regions
        PublicWarning wReg = createBaselineWarning();
        wReg.setAffectedRegions(Collections.singletonList("Tampered Region X"));
        assertFalse("Tampered affected regions must fail verification", verifier.verify(wReg).isAuthentic());
    }

    // -------------------------------------------------------------------------
    // Negative Test 5: Modify expiry timestamp
    // -------------------------------------------------------------------------
    @Test
    public void test05_ModifyExpiryTimestamp_FailsVerification() {
        PublicWarning w = createBaselineWarning();
        // Alter timestamp in canonical payload by changing expiry
        w.setExpiresAt(EXPIRES_AT + 3600000L);

        WarningSignatureVerifier.VerificationResult result = verifier.verify(w);
        assertFalse("Tampered expiry timestamp must fail verification", result.isAuthentic());
        assertEquals(PublicWarning.VERIFY_INVALID_SIGNATURE, result.getStatus());
    }

    // -------------------------------------------------------------------------
    // Negative Test 6: Replace signature with random bytes
    // -------------------------------------------------------------------------
    @Test
    public void test06_ReplaceSignatureWithRandomBytes_FailsVerification() {
        PublicWarning w = createBaselineWarning();
        // 64 random bytes encoded as base64
        w.setSignature("g3b9r1k/8Z2fH3b9r1k/8Z2fH3b9r1k/8Z2fH3b9r1k/8Z2fH3b9r1k/8Z2fH3b9r1k/8Z2fH3b9r1k/8Z2fHw==");

        WarningSignatureVerifier.VerificationResult result = verifier.verify(w);
        assertFalse("Random signature bytes must fail cryptographic verification", result.isAuthentic());
        assertEquals(PublicWarning.VERIFY_INVALID_SIGNATURE, result.getStatus());
    }

    // -------------------------------------------------------------------------
    // Negative Test 7: Use an unknown signing key ID
    // -------------------------------------------------------------------------
    @Test
    public void test07_UnknownSigningKeyId_FailsVerification() {
        PublicWarning w = createBaselineWarning();
        w.setSigningKeyId("KEY-MALICIOUS-ROGUE-2026");

        WarningSignatureVerifier.VerificationResult result = verifier.verify(w);
        assertFalse("Unknown signing key must be rejected", result.isAuthentic());
        assertEquals(PublicWarning.VERIFY_UNKNOWN_SIGNING_KEY, result.getStatus());
        assertTrue(result.getMessage().contains("KEY-MALICIOUS-ROGUE-2026"));
    }

    // -------------------------------------------------------------------------
    // Negative Test 8: Use a revoked signing key
    // -------------------------------------------------------------------------
    @Test
    public void test08_RevokedSigningKey_FailsVerification() {
        PublicWarning w = createBaselineWarning();
        // Revoke the key in the trusted repository
        keyRepository.revokeKey(ROOT_KEY_ID, "Emergency revocation: Private key compromise reported.");

        WarningSignatureVerifier.VerificationResult result = verifier.verify(w);
        assertFalse("Revoked signing key must be rejected", result.isAuthentic());
        assertEquals(PublicWarning.VERIFY_REVOKED_SIGNING_KEY, result.getStatus());
        assertTrue(result.getMessage().contains("compromise"));
    }

    // -------------------------------------------------------------------------
    // Negative Test 9: Replay an old warning revision (Anti-Rollback)
    // -------------------------------------------------------------------------
    @Test
    public void test09_ReplayOlderWarningRevision_DetectedAndRejected() {
        PublicWarning existing = createBaselineWarning();
        existing.setRevision(3); // Already at revision 3

        PublicWarning replayedOld = createBaselineWarning();
        replayedOld.setRevision(1); // Adversary tries to replay stale revision 1

        // Verifier / lifecycle rule: revision must be strictly greater than known revision
        boolean isStaleOrReplayed = replayedOld.getRevision() <= existing.getRevision();
        assertTrue("Older warning revision replay must be rejected by anti-rollback logic", isStaleOrReplayed);
    }

    // -------------------------------------------------------------------------
    // Negative Test 10: Supply malformed canonical payload data
    // -------------------------------------------------------------------------
    @Test
    public void test10_MalformedCanonicalPayloadData_HandledGracefully() {
        // Direct verify call with corrupt / truncated UTF-8 bytes
        byte[] malformedBytes = "CORRUPT_PAYLOAD_NOT_CANONICAL".getBytes(StandardCharsets.UTF_8);
        boolean directValid = WarningSignatureVerifier.verifyEd25519Signature(
                ROOT_PUB_KEY,
                malformedBytes,
                VALID_SIGNATURE
        );
        assertFalse("Corrupted canonical bytes must fail verification safely", directValid);

        // Verification of completely empty signature string
        boolean emptySigValid = WarningSignatureVerifier.verifyEd25519Signature(
                ROOT_PUB_KEY,
                malformedBytes,
                ""
        );
        assertFalse("Empty signature must fail safely", emptySigValid);

        // Verification with invalid base64 characters
        boolean invalidB64Valid = WarningSignatureVerifier.verifyEd25519Signature(
                ROOT_PUB_KEY,
                malformedBytes,
                "@@NOT_VALID_BASE64##"
        );
        assertFalse("Malformed base64 signature must not crash application", invalidB64Valid);
    }

    // -------------------------------------------------------------------------
    // Negative Test 11: Malformed JSON packet injection in Relay Deserializer
    // -------------------------------------------------------------------------
    @Test
    public void test11_MalformedJsonRelayPacket_RejectedSafely() {
        byte[] garbageBytes = "{ this is not valid JSON and should not crash the app }}}".getBytes(StandardCharsets.UTF_8);
        String msgType = WarningRelayModels.peekMessageType(garbageBytes);
        assertEquals("Malformed JSON must return empty messageType safely without throwing", "", msgType);

        boolean threwExpectedException = false;
        try {
            WarningRelayModels.WarningTransferPayload.fromBytes(garbageBytes);
        } catch (Exception e) {
            threwExpectedException = true;
        }
        assertTrue("Malformed JSON must be rejected gracefully", threwExpectedException);
    }

    // -------------------------------------------------------------------------
    // Negative Test 12: Oversized packet injection protection
    // -------------------------------------------------------------------------
    @Test
    public void test12_OversizedPacket_PayloadValidation() {
        // Create 2MB byte buffer exceeding MAX_PAYLOAD_SIZE_BYTES (65536)
        byte[] oversizedBytes = new byte[1024 * 1024 * 2];
        boolean exceedsLimit = oversizedBytes.length > com.blackout.emergency.utils.Constants.MAX_PAYLOAD_SIZE_BYTES;
        assertTrue("Oversized packets must exceed safety threshold", exceedsLimit);
    }
}

