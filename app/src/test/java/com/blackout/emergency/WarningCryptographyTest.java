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
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * Automated cryptographic verification and tamper detection tests (Phase 7).
 */
public class WarningCryptographyTest {

    private TrustedKeyRepository keyRepository;
    private WarningSignatureVerifier verifier;

    // Test vectors generated with generate_vectors.js
    private static final String SAMPLE_WARNING_ID = "d3b07384-d113-46fb-9b24-9b2bfa6eb542";
    private static final String ROOT_KEY_ID = "KEY-BLACKOUT-ROOT-2026";
    private static final String ROOT_PUB_KEY = "k5EwtLq5Md0tNFqNxdqdm2vL6c/q1ZgHcb6Uku32/Xc=";
    private static final String VALID_SIGNATURE = "sL8/lOZt03vDDP2IIlFbVjTpgWDy22y1Z8kiF0BJ4IxuBDkAp4NUwl4i0kVZu5v36GVVa90TmyrpnUxscmd2AA==";
    private static final String EXPIRED_SIGNATURE = "071zwkkhSeCsC/UAqT1I89l3M1FTRsU/LV44O/vsPVFv+ycp/dHIVC9drgaOanl181Tt1ewcYNzHOFB43dRcAA==";

    // Fixed timestamps: issued now (1793400000000L), expires in future (1850000000000L)
    private static final long ISSUED_AT = 1793400000000L;
    private static final long EFFECTIVE_AT = 1793400000000L;
    private static final long EXPIRES_AT = 1850000000000L;

    @Before
    public void setUp() {
        keyRepository = new TrustedKeyRepository();
        verifier = new WarningSignatureVerifier(keyRepository);
    }

    private PublicWarning createValidWarning() {
        List<String> regions = Arrays.asList("Sector 4", "Central Basin", "North Riverbank");
        PublicWarning w = new PublicWarning(
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
        return w;
    }

    @Test
    public void testCanonicalSerializationFormat() {
        PublicWarning w = createValidWarning();
        String canonical = WarningCanonicalSerializer.buildCanonicalString(w);

        assertNotNull(canonical);
        assertTrue(canonical.startsWith("BLACKOUT_WARNING_V1\n"));
        assertTrue(canonical.contains("warningId=" + SAMPLE_WARNING_ID));
        assertTrue(canonical.contains("category=FLOOD"));
        assertTrue(canonical.contains("severity=CRITICAL"));
        // Regions must be sorted alphabetically: Central Basin,North Riverbank,Sector 4
        assertTrue(canonical.contains("affectedRegions=Central Basin,North Riverbank,Sector 4"));
        assertTrue(canonical.contains("latitude=13.082680"));
        assertTrue(canonical.contains("longitude=80.270718"));
        assertTrue(canonical.contains("radiusMeters=5000.00"));
    }

    @Test
    public void testValidEd25519SignatureDirectVerification() {
        PublicWarning w = createValidWarning();
        String canonical = WarningCanonicalSerializer.buildCanonicalString(w);

        boolean valid = WarningSignatureVerifier.verifyEd25519Signature(
                ROOT_PUB_KEY,
                canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                VALID_SIGNATURE
        );
        assertTrue("Signature must verify directly using Ed25519", valid);
    }

    @Test
    public void testTamperedTitleFailsSignatureVerification() {
        PublicWarning w = createValidWarning();
        w.setTitle("Severe Flash Flooding Alert (ALTERED TITLE)");

        String canonical = WarningCanonicalSerializer.buildCanonicalString(w);
        boolean valid = WarningSignatureVerifier.verifyEd25519Signature(
                ROOT_PUB_KEY,
                canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                VALID_SIGNATURE
        );
        assertFalse("Tampered title must cause cryptographic verification to FAIL", valid);
    }

    @Test
    public void testTamperedDescriptionFailsSignatureVerification() {
        PublicWarning w = createValidWarning();
        w.setDescription("Fake instructions: All clear, no flood.");

        String canonical = WarningCanonicalSerializer.buildCanonicalString(w);
        boolean valid = WarningSignatureVerifier.verifyEd25519Signature(
                ROOT_PUB_KEY,
                canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                VALID_SIGNATURE
        );
        assertFalse("Tampered description must cause cryptographic verification to FAIL", valid);
    }

    @Test
    public void testTamperedSeverityFailsSignatureVerification() {
        PublicWarning w = createValidWarning();
        w.setSeverity(PublicWarning.SEVERITY_INFORMATIONAL);

        String canonical = WarningCanonicalSerializer.buildCanonicalString(w);
        boolean valid = WarningSignatureVerifier.verifyEd25519Signature(
                ROOT_PUB_KEY,
                canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                VALID_SIGNATURE
        );
        assertFalse("Tampered severity must cause cryptographic verification to FAIL", valid);
    }

    @Test
    public void testTamperedCoordinatesFailsSignatureVerification() {
        PublicWarning w = createValidWarning();
        w.setLatitude(13.999999);

        String canonical = WarningCanonicalSerializer.buildCanonicalString(w);
        boolean valid = WarningSignatureVerifier.verifyEd25519Signature(
                ROOT_PUB_KEY,
                canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                VALID_SIGNATURE
        );
        assertFalse("Tampered coordinates must cause cryptographic verification to FAIL", valid);
    }

    @Test
    public void testTamperedAffectedRegionsFailsSignatureVerification() {
        PublicWarning w = createValidWarning();
        w.setAffectedRegions(Arrays.asList("Sector 99"));

        String canonical = WarningCanonicalSerializer.buildCanonicalString(w);
        boolean valid = WarningSignatureVerifier.verifyEd25519Signature(
                ROOT_PUB_KEY,
                canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                VALID_SIGNATURE
        );
        assertFalse("Tampered affected regions must cause cryptographic verification to FAIL", valid);
    }

    @Test
    public void testTamperedRevisionFailsSignatureVerification() {
        PublicWarning w = createValidWarning();
        w.setRevision(2);

        String canonical = WarningCanonicalSerializer.buildCanonicalString(w);
        boolean valid = WarningSignatureVerifier.verifyEd25519Signature(
                ROOT_PUB_KEY,
                canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                VALID_SIGNATURE
        );
        assertFalse("Tampered revision number must cause cryptographic verification to FAIL", valid);
    }

    @Test
    public void testUnknownSigningKeyFailsVerification() {
        PublicWarning w = createValidWarning();
        w.setSigningKeyId("KEY-UNKNOWN-ROGUE-999");

        WarningSignatureVerifier.VerificationResult result = verifier.verify(w);
        assertFalse(result.isAuthentic());
        assertEquals(PublicWarning.VERIFY_UNKNOWN_SIGNING_KEY, result.getStatus());
    }

    @Test
    public void testRevokedSigningKeyFailsVerification() {
        PublicWarning w = createValidWarning();
        keyRepository.revokeKey(ROOT_KEY_ID, "Key compromised during incident");

        WarningSignatureVerifier.VerificationResult result = verifier.verify(w);
        assertFalse(result.isAuthentic());
        assertEquals(PublicWarning.VERIFY_REVOKED_SIGNING_KEY, result.getStatus());
    }

    @Test
    public void testValidWarningVerificationSucceeds() {
        PublicWarning w = createValidWarning();
        WarningSignatureVerifier.VerificationResult result = verifier.verify(w);
        assertTrue("Valid warning must verify successfully: " + result.getMessage(), result.isAuthentic());
        assertEquals(PublicWarning.VERIFY_VERIFIED, result.getStatus());
    }

    @Test
    public void testExpiredWarningDetected() {
        PublicWarning w = createValidWarning();
        w.setIssuedAt(1700000000000L);
        w.setEffectiveAt(1700000000000L);
        w.setExpiresAt(1710000000000L); // Expired timestamp
        w.setSignature(EXPIRED_SIGNATURE);

        WarningSignatureVerifier.VerificationResult result = verifier.verify(w);
        assertFalse(result.isAuthentic());
        assertEquals(PublicWarning.VERIFY_EXPIRED, result.getStatus());
    }

    @Test
    public void testMalformedSignatureEncodingFailsGracefully() {
        boolean valid = WarningSignatureVerifier.verifyEd25519Signature(
                ROOT_PUB_KEY,
                "data".getBytes(),
                "NOT_BASE_64!@#$"
        );
        assertFalse(valid);
    }

    @Test
    public void testPayloadHashGeneration() {
        PublicWarning w = createValidWarning();
        String canonical = WarningCanonicalSerializer.buildCanonicalString(w);
        String hash = WarningCanonicalSerializer.computePayloadHash(canonical);

        assertNotNull(hash);
        assertEquals("SHA-256 hex string must be 64 characters", 64, hash.length());
    }
}
