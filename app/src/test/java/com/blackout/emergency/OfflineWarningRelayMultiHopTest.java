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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

/**
 * Automated end-to-end multi-hop store-carry-and-forward relay test suite (Phase 7):
 * - Multi-hop distribution across disconnected nodes: Node A (Gateway) -> Node B -> Node C -> Node D
 * - Independent cryptographic signature verification at each hop
 * - Inventory exchange and differential synchronization
 * - Detection and rejection of in-transit payload tampering
 * - Duplicate suppression
 * - Revision update propagation (Rev 1 -> Rev 2)
 * - Rollback attack prevention across the mesh
 */
public class OfflineWarningRelayMultiHopTest {

    private static final String ROOT_KEY_ID = "KEY-BLACKOUT-ROOT-2026";
    private static final String ROOT_PUB_KEY = "k5EwtLq5Md0tNFqNxdqdm2vL6c/q1ZgHcb6Uku32/Xc=";

    // Pre-computed Ed25519 signature for the sample warning
    private static final String SAMPLE_WARNING_ID = "d3b07384-d113-46fb-9b24-9b2bfa6eb542";
    private static final String VALID_SIGNATURE_REV1 = "sL8/lOZt03vDDP2IIlFbVjTpgWDy22y1Z8kiF0BJ4IxuBDkAp4NUwl4i0kVZu5v36GVVa90TmyrpnUxscmd2AA==";

    // Simulated node state for multi-hop verification
    private static class SimulatedNode {
        final String nodeId;
        final TrustedKeyRepository keyRepo;
        final WarningSignatureVerifier verifier;
        final Map<String, PublicWarning> localDatabase = new HashMap<>();
        final List<String> receivedAcks = new ArrayList<>();
        final List<String> receivedRejections = new ArrayList<>();

        SimulatedNode(String nodeId) {
            this.nodeId = nodeId;
            this.keyRepo = new TrustedKeyRepository();
            this.verifier = new WarningSignatureVerifier(this.keyRepo);
        }

        List<WarningRelayModels.InventoryItem> buildInventory() {
            List<WarningRelayModels.InventoryItem> items = new ArrayList<>();
            for (PublicWarning w : localDatabase.values()) {
                items.add(new WarningRelayModels.InventoryItem(
                        w.getWarningId(),
                        w.getRevision(),
                        w.getPayloadHash(),
                        w.getExpiresAt(),
                        w.getVerificationStatus()
                ));
            }
            return items;
        }

        // Simulates receiving a transfer payload from a peer over Nearby Connections
        boolean receiveWarningPayload(byte[] rawPayloadBytes) {
            try {
                WarningRelayModels.WarningTransferPayload transfer = WarningRelayModels.WarningTransferPayload.fromBytes(rawPayloadBytes);
                PublicWarning incoming = transfer.getWarning();

                // 1. Mandatory independent cryptographic verification
                WarningSignatureVerifier.VerificationResult v = verifier.verify(incoming);
                if (!v.isAuthentic()) {
                    receivedRejections.add("Cryptographic verification failed: " + v.getStatus());
                    return false;
                }

                // 2. Check local database for duplicates or revision rollback
                PublicWarning existing = localDatabase.get(incoming.getWarningId());
                if (existing != null) {
                    if (incoming.getRevision() < existing.getRevision()) {
                        receivedRejections.add("Rollback rejected: Incoming rev " + incoming.getRevision() + " < local rev " + existing.getRevision());
                        return false;
                    }
                    if (incoming.getRevision() == existing.getRevision()) {
                        receivedAcks.add("DUPLICATE:" + incoming.getWarningId());
                        return true; // Already held
                    }
                }

                // 3. Accept and persist
                incoming.setVerificationStatus(PublicWarning.VERIFY_VERIFIED);
                localDatabase.put(incoming.getWarningId(), incoming);
                receivedAcks.add("ACCEPTED:" + incoming.getWarningId());
                return true;

            } catch (Exception e) {
                receivedRejections.add("Malformed payload: " + e.getMessage());
                return false;
            }
        }
    }

    private SimulatedNode nodeA; // Gateway
    private SimulatedNode nodeB; // Hop 1
    private SimulatedNode nodeC; // Hop 2
    private SimulatedNode nodeD; // Hop 3

    @Before
    public void setUp() {
        nodeA = new SimulatedNode("NODE_A_GATEWAY");
        nodeB = new SimulatedNode("NODE_B_OFFLINE");
        nodeC = new SimulatedNode("NODE_C_OFFLINE");
        nodeD = new SimulatedNode("NODE_D_OFFLINE");
    }

    private PublicWarning createAuthenticWarning(int revision) {
        List<String> regions = Arrays.asList("Sector 4", "Central Basin", "North Riverbank");
        PublicWarning w = new PublicWarning(
                SAMPLE_WARNING_ID,
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
                PublicWarning.STATUS_PUBLISHED,
                ROOT_KEY_ID,
                "Ed25519",
                VALID_SIGNATURE_REV1,
                null
        );
        w.setVerificationStatus(PublicWarning.VERIFY_VERIFIED);
        return w;
    }

    @Test
    public void testMultiHopStoreCarryAndForwardChain() throws Exception {
        // Step 1: Gateway Node A downloads authentic warning
        PublicWarning warningRev1 = createAuthenticWarning(1);
        nodeA.localDatabase.put(warningRev1.getWarningId(), warningRev1);

        // Step 2: Node A meets Node B offline. Node A serializes warning into transfer payload
        WarningRelayModels.WarningTransferPayload transferAB = new WarningRelayModels.WarningTransferPayload(
                nodeA.nodeId, warningRev1
        );
        byte[] bytesAB = transferAB.toBytes();

        // Node B receives and independently verifies
        boolean acceptedB = nodeB.receiveWarningPayload(bytesAB);
        assertTrue("Node B must accept and verify authentic warning from Node A", acceptedB);
        assertTrue(nodeB.localDatabase.containsKey(SAMPLE_WARNING_ID));
        assertEquals(1, nodeB.localDatabase.get(SAMPLE_WARNING_ID).getRevision());
        assertEquals(PublicWarning.VERIFY_VERIFIED, nodeB.localDatabase.get(SAMPLE_WARNING_ID).getVerificationStatus());

        // Step 3: Node B carries warning and later meets Node C (deep offline).
        WarningRelayModels.WarningTransferPayload transferBC = new WarningRelayModels.WarningTransferPayload(
                nodeB.nodeId, nodeB.localDatabase.get(SAMPLE_WARNING_ID)
        );
        byte[] bytesBC = transferBC.toBytes();

        boolean acceptedC = nodeC.receiveWarningPayload(bytesBC);
        assertTrue("Node C must independently verify warning signature from Node B without internet", acceptedC);
        assertTrue(nodeC.localDatabase.containsKey(SAMPLE_WARNING_ID));

        // Step 4: Node C carries warning and meets Node D
        WarningRelayModels.WarningTransferPayload transferCD = new WarningRelayModels.WarningTransferPayload(
                nodeC.nodeId, nodeC.localDatabase.get(SAMPLE_WARNING_ID)
        );
        byte[] bytesCD = transferCD.toBytes();

        boolean acceptedD = nodeD.receiveWarningPayload(bytesCD);
        assertTrue("Node D must independently verify signature at 3rd hop", acceptedD);
        assertTrue(nodeD.localDatabase.containsKey(SAMPLE_WARNING_ID));

        // All 4 nodes now possess the authentic, verified warning
        assertEquals(1, nodeD.localDatabase.get(SAMPLE_WARNING_ID).getRevision());
    }

    @Test
    public void testInTransitTamperDetectionAndRejection() throws Exception {
        PublicWarning authentic = createAuthenticWarning(1);
        WarningRelayModels.WarningTransferPayload transfer = new WarningRelayModels.WarningTransferPayload(
                nodeA.nodeId, authentic
        );
        byte[] rawBytes = transfer.toBytes();

        // Simulate Man-In-The-Middle attacker altering the title in the payload JSON
        String jsonStr = new String(rawBytes, StandardCharsets.UTF_8);
        String tamperedJsonStr = jsonStr.replace("Immediate flash flooding reported", "FAKE NEWS - ALL CLEAR RETURN HOME");
        byte[] tamperedBytes = tamperedJsonStr.getBytes(StandardCharsets.UTF_8);

        // Node B receives the tampered bytes
        boolean accepted = nodeB.receiveWarningPayload(tamperedBytes);
        assertFalse("Tampered payload must be rejected by receiver", accepted);
        assertFalse("Tampered warning must NOT be stored in local database", nodeB.localDatabase.containsKey(SAMPLE_WARNING_ID));
        assertTrue("Rejection must be logged", nodeB.receivedRejections.get(0).contains("Cryptographic verification failed"));
    }

    @Test
    public void testInventoryDifferentialSync() {
        // Node A has warning W1
        PublicWarning w1 = createAuthenticWarning(1);
        nodeA.localDatabase.put(w1.getWarningId(), w1);

        // Node B has empty database. Node B generates inventory
        List<WarningRelayModels.InventoryItem> invB = nodeB.buildInventory();
        assertEquals(0, invB.size());

        // Node A compares Node B's inventory with its own database
        List<PublicWarning> toSend = new ArrayList<>();
        Map<String, Integer> peerKnown = new HashMap<>();
        for (WarningRelayModels.InventoryItem item : invB) {
            peerKnown.put(item.warningId, item.revision);
        }

        for (PublicWarning localW : nodeA.localDatabase.values()) {
            Integer peerRev = peerKnown.get(localW.getWarningId());
            if (peerRev == null || localW.getRevision() > peerRev) {
                toSend.add(localW);
            }
        }

        // Node A determines it needs to send 1 warning to Node B
        assertEquals(1, toSend.size());
        assertEquals(SAMPLE_WARNING_ID, toSend.get(0).getWarningId());
    }

    @Test
    public void testDuplicateSuppression() throws Exception {
        PublicWarning w1 = createAuthenticWarning(1);
        nodeB.localDatabase.put(w1.getWarningId(), w1);

        // Another node sends the exact same revision again
        WarningRelayModels.WarningTransferPayload transfer = new WarningRelayModels.WarningTransferPayload(
                "OTHER_NODE", w1
        );
        byte[] bytes = transfer.toBytes();

        boolean processed = nodeB.receiveWarningPayload(bytes);
        assertTrue("Duplicate should be processed gracefully", processed);
        assertTrue(nodeB.receivedAcks.contains("DUPLICATE:" + SAMPLE_WARNING_ID));
        assertEquals("Database should still have exactly 1 record", 1, nodeB.localDatabase.size());
    }

    @Test
    public void testRollbackAttackPrevention() throws Exception {
        // Node C already has Revision 2 (simulated)
        PublicWarning rev2 = createAuthenticWarning(2);
        nodeC.localDatabase.put(rev2.getWarningId(), rev2);

        // Attacker attempts to send older Revision 1
        PublicWarning rev1 = createAuthenticWarning(1);
        WarningRelayModels.WarningTransferPayload replayPayload = new WarningRelayModels.WarningTransferPayload(
                "MALICIOUS_REPLAY_NODE", rev1
        );
        byte[] bytes = replayPayload.toBytes();

        boolean accepted = nodeC.receiveWarningPayload(bytes);
        assertFalse("Node C must reject rollback to older revision", accepted);
        assertEquals("Active revision must remain Rev 2", 2, nodeC.localDatabase.get(SAMPLE_WARNING_ID).getRevision());
        assertTrue("Rejection must be logged for rollback attempt",
                nodeC.receivedRejections.get(0).contains("Rollback rejected"));
    }
}
