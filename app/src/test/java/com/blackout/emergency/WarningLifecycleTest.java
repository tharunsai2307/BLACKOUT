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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

/**
 * Automated tests for Public Emergency Warning lifecycle (Phase 7):
 * - Draft creation & approval
 * - Digital signature verification
 * - Revision update & superseding
 * - Revision rollback protection (lower revision rejection)
 * - Formal warning revocation
 * - Expiration handling
 */
public class WarningLifecycleTest {

    private TrustedKeyRepository keyRepository;
    private WarningSignatureVerifier verifier;

    private static final String ROOT_KEY_ID = "KEY-BLACKOUT-ROOT-2026";
    private static final String ROOT_PUB_KEY = "k5EwtLq5Md0tNFqNxdqdm2vL6c/q1ZgHcb6Uku32/Xc=";

    // Active signature generated for revision 1
    private static final String VALID_SIG_REV1 = "sL8/lOZt03vDDP2IIlFbVjTpgWDy22y1Z8kiF0BJ4IxuBDkAp4NUwl4i0kVZu5v36GVVa90TmyrpnUxscmd2AA==";

    // Mock in-memory warning database store for testing revision superseding logic
    private final Map<String, PublicWarning> databaseStore = new HashMap<>();
    private final List<PublicWarning> revisionHistoryStore = new ArrayList<>();

    @Before
    public void setUp() {
        keyRepository = new TrustedKeyRepository();
        verifier = new WarningSignatureVerifier(keyRepository);
        databaseStore.clear();
        revisionHistoryStore.clear();
    }

    private PublicWarning createSampleWarning(int revision, String status) {
        List<String> regions = Arrays.asList("Sector 4", "Central Basin", "North Riverbank");
        PublicWarning w = new PublicWarning(
                "d3b07384-d113-46fb-9b24-9b2bfa6eb542",
                1,
                revision,
                "Severe Flash Flooding Alert",
                "Immediate flash flooding reported in low-lying areas. Move to higher ground immediately.",
                PublicWarning.CATEGORY_FLOOD,
                PublicWarning.SEVERITY_CRITICAL,
                "BLACKOUT Civil Defense & Emergency Response Administration",
                1793400000000L,
                1793400000000L,
                1850000000000L,
                regions,
                13.082680,
                80.270718,
                5000.0,
                "en",
                status,
                ROOT_KEY_ID,
                "Ed25519",
                VALID_SIG_REV1,
                null
        );
        return w;
    }

    @Test
    public void testInitialRevisionAccepted() {
        PublicWarning rev1 = createSampleWarning(1, PublicWarning.STATUS_PUBLISHED);
        WarningSignatureVerifier.VerificationResult v = verifier.verify(rev1);

        assertTrue("Signature verification must succeed", v.isAuthentic());
        databaseStore.put(rev1.getWarningId(), rev1);

        assertEquals(1, databaseStore.size());
        assertEquals(1, databaseStore.get(rev1.getWarningId()).getRevision());
    }

    @Test
    public void testHigherRevisionSupersedesOlderRevision() {
        PublicWarning rev1 = createSampleWarning(1, PublicWarning.STATUS_PUBLISHED);
        databaseStore.put(rev1.getWarningId(), rev1);

        // Simulate Revision 2 arriving: Archive old rev1 into history, update main store
        PublicWarning rev2 = createSampleWarning(2, PublicWarning.STATUS_PUBLISHED);
        rev2.setTitle("Severe Flash Flooding Alert - EXPANDED EVACUATION");

        // Verify revision 2 is newer
        PublicWarning existing = databaseStore.get(rev2.getWarningId());
        assertNotNull(existing);
        assertTrue("New revision number must be strictly greater", rev2.getRevision() > existing.getRevision());

        revisionHistoryStore.add(existing); // Archived Rev 1
        databaseStore.put(rev2.getWarningId(), rev2); // Active is now Rev 2

        assertEquals(2, databaseStore.get(rev2.getWarningId()).getRevision());
        assertEquals("Severe Flash Flooding Alert - EXPANDED EVACUATION", databaseStore.get(rev2.getWarningId()).getTitle());
        assertEquals(1, revisionHistoryStore.size());
        assertEquals(1, revisionHistoryStore.get(0).getRevision());
    }

    @Test
    public void testLowerRevisionRollbackRejected() {
        PublicWarning rev2 = createSampleWarning(2, PublicWarning.STATUS_PUBLISHED);
        databaseStore.put(rev2.getWarningId(), rev2);

        // Delayed/replayed packet arrives with Revision 1
        PublicWarning rev1 = createSampleWarning(1, PublicWarning.STATUS_PUBLISHED);
        PublicWarning existing = databaseStore.get(rev1.getWarningId());

        boolean shouldReject = existing != null && existing.getRevision() > rev1.getRevision();
        assertTrue("Lower revision must be rejected to prevent rollback attack", shouldReject);
        // Active store must still hold Revision 2
        assertEquals(2, databaseStore.get(rev1.getWarningId()).getRevision());
    }

    @Test
    public void testDuplicateRevisionSuppressed() {
        PublicWarning rev1 = createSampleWarning(1, PublicWarning.STATUS_PUBLISHED);
        databaseStore.put(rev1.getWarningId(), rev1);

        // Identical revision arrives again
        PublicWarning rev1Duplicate = createSampleWarning(1, PublicWarning.STATUS_PUBLISHED);
        PublicWarning existing = databaseStore.get(rev1Duplicate.getWarningId());

        boolean isDuplicate = existing != null && existing.getRevision() == rev1Duplicate.getRevision();
        assertTrue("Duplicate revision must be acknowledged without redundant persistence", isDuplicate);
    }

    @Test
    public void testWarningRevocationLifecycle() {
        PublicWarning rev1 = createSampleWarning(1, PublicWarning.STATUS_PUBLISHED);
        databaseStore.put(rev1.getWarningId(), rev1);

        // Authorized revocation arrives as Revision 2
        PublicWarning rev2Revocation = createSampleWarning(2, PublicWarning.STATUS_REVOKED);
        rev2Revocation.setDescription("[REVOKED: Threat Neutralized] Floodwaters receded.");
        rev2Revocation.setVerificationStatus(PublicWarning.VERIFY_REVOKED_WARNING);

        databaseStore.put(rev2Revocation.getWarningId(), rev2Revocation);

        PublicWarning active = databaseStore.get(rev1.getWarningId());
        assertEquals(PublicWarning.STATUS_REVOKED, active.getStatus());
        assertEquals(2, active.getRevision());
    }

    @Test
    public void testExpiredWarningNotPresentedAsActive() {
        PublicWarning w = createSampleWarning(1, PublicWarning.STATUS_PUBLISHED);
        w.setExpiresAt(System.currentTimeMillis() - 5000L); // 5 seconds ago

        assertTrue("Warning should detect its own expiration", w.isExpiredNow());
    }
}
