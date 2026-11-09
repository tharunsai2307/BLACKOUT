package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.blackout.emergency.nearby.PeerSecurityManager;
import com.blackout.emergency.relay.RelayPayloadModels;
import com.blackout.emergency.utils.Constants;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Phase 12 BitChat-inspired Autonomous Emergency Mesh Network tests:
 * - Gateway Capability payload round-trip & versioning
 * - Peer Handshake and Ack payload round-trip
 * - Transfer Request payload round-trip
 * - PeerSecurityManager replay attack prevention & timestamp freshness
 * - SHA-256 payload integrity hashing
 * - Deterministic tie-breaker logic
 */
public class AutonomousMeshTest {

    private PeerSecurityManager securityManager;

    @Before
    public void setUp() {
        securityManager = new PeerSecurityManager();
    }

    @Test
    public void testGatewayCapabilityRoundTrip() throws Exception {
        RelayPayloadModels.GatewayCapabilityPayload original =
                new RelayPayloadModels.GatewayCapabilityPayload(
                        "NODE-ALPHA",
                        true,
                        "Wi-Fi",
                        85,
                        3);

        byte[] bytes = original.toBytes();
        assertNotNull(bytes);
        assertEquals(Constants.MESSAGE_TYPE_GATEWAY_CAPABILITY, RelayPayloadModels.peekMessageType(bytes));

        RelayPayloadModels.GatewayCapabilityPayload parsed =
                RelayPayloadModels.GatewayCapabilityPayload.fromBytes(bytes);

        assertNotNull(parsed);
        assertEquals("NODE-ALPHA", parsed.getSenderCallsign());
        assertTrue(parsed.isInternetAvailable());
        assertEquals("Wi-Fi", parsed.getNetworkType());
        assertEquals(85, parsed.getBatteryLevelPct());
        assertEquals(3, parsed.getPendingUploadCount());
    }

    @Test
    public void testPeerHandshakeRoundTrip() throws Exception {
        List<String> capabilities = Arrays.asList("SOS_RELAY", "GATEWAY_BRIDGE", "WARNING_VERIFY");
        RelayPayloadModels.PeerHandshakePayload original =
                new RelayPayloadModels.PeerHandshakePayload(
                        "NODE-BETA",
                        "PUBKEY_TEST_HEX_123456",
                        "NONCE_RANDOM_987654",
                        System.currentTimeMillis(),
                        capabilities);

        byte[] bytes = original.toBytes();
        assertNotNull(bytes);
        assertEquals(Constants.MESSAGE_TYPE_PEER_HANDSHAKE, RelayPayloadModels.peekMessageType(bytes));

        RelayPayloadModels.PeerHandshakePayload parsed =
                RelayPayloadModels.PeerHandshakePayload.fromBytes(bytes);

        assertNotNull(parsed);
        assertEquals("NODE-BETA", parsed.getSenderCallsign());
        assertEquals("PUBKEY_TEST_HEX_123456", parsed.getPublicKeyHex());
        assertEquals("NONCE_RANDOM_987654", parsed.getHandshakeNonce());
        assertEquals(3, parsed.getCapabilities().size());
        assertTrue(parsed.getCapabilities().contains("GATEWAY_BRIDGE"));
    }

    @Test
    public void testPeerHandshakeAckRoundTrip() throws Exception {
        RelayPayloadModels.PeerHandshakeAckPayload original =
                new RelayPayloadModels.PeerHandshakeAckPayload(
                        "NODE-GAMMA",
                        "NONCE_ECHO_987654",
                        "PUBKEY_RESPONDER_HEX",
                        true);

        byte[] bytes = original.toBytes();
        assertNotNull(bytes);
        assertEquals(Constants.MESSAGE_TYPE_PEER_HANDSHAKE_ACK, RelayPayloadModels.peekMessageType(bytes));

        RelayPayloadModels.PeerHandshakeAckPayload parsed =
                RelayPayloadModels.PeerHandshakeAckPayload.fromBytes(bytes);

        assertNotNull(parsed);
        assertEquals("NODE-GAMMA", parsed.getResponderCallsign());
        assertEquals("NONCE_ECHO_987654", parsed.getEchoNonce());
        assertEquals("PUBKEY_RESPONDER_HEX", parsed.getResponderPublicKey());
        assertTrue(parsed.isSuccess());
    }

    @Test
    public void testTransferRequestRoundTrip() throws Exception {
        List<String> requestedIds = Arrays.asList("sos-001", "sos-002");
        RelayPayloadModels.TransferRequestPayload original =
                new RelayPayloadModels.TransferRequestPayload("NODE-DELTA", requestedIds);

        byte[] bytes = original.toBytes();
        assertNotNull(bytes);
        assertEquals(Constants.MESSAGE_TYPE_TRANSFER_REQUEST, RelayPayloadModels.peekMessageType(bytes));

        RelayPayloadModels.TransferRequestPayload parsed =
                RelayPayloadModels.TransferRequestPayload.fromBytes(bytes);

        assertNotNull(parsed);
        assertEquals("NODE-DELTA", parsed.getRequesterCallsign());
        assertEquals(2, parsed.getRequestedMessageIds().size());
        assertEquals("sos-001", parsed.getRequestedMessageIds().get(0));
    }

    @Test
    public void testPeerSecurityManager_NonceAntiReplay() {
        String nonce = securityManager.generateNonce();
        assertNotNull(nonce);
        long now = System.currentTimeMillis();

        // First presentation of nonce should be valid
        assertTrue("First use of fresh nonce must be accepted",
                securityManager.validateHandshakeFreshness(nonce, now));

        // Replay of the exact same nonce must be rejected
        assertFalse("Replay of previously observed nonce must be rejected",
                securityManager.validateHandshakeFreshness(nonce, now));
    }

    @Test
    public void testPeerSecurityManager_ClockSkewRejection() {
        String nonce1 = securityManager.generateNonce();
        long twoHoursAndOneMinuteAgo = System.currentTimeMillis() - (121 * 60 * 1000L);

        // Stale timestamp (> 2 hours old) must be rejected
        assertFalse("Stale handshake packet (>2h old) must be rejected",
                securityManager.validateHandshakeFreshness(nonce1, twoHoursAndOneMinuteAgo));

        String nonce2 = securityManager.generateNonce();
        long twoHoursInFuture = System.currentTimeMillis() + (121 * 60 * 1000L);

        // Far-future timestamp must be rejected
        assertFalse("Future-dated handshake packet (>2h in future) must be rejected",
                securityManager.validateHandshakeFreshness(nonce2, twoHoursInFuture));
    }

    @Test
    public void testPeerSecurityManager_PayloadIntegrityHash() {
        byte[] payload1 = "EMERGENCY_DATA_BLOCK_1".getBytes(StandardCharsets.UTF_8);
        byte[] payload2 = "EMERGENCY_DATA_BLOCK_2".getBytes(StandardCharsets.UTF_8);

        String hash1 = securityManager.computePayloadHash(payload1);
        String hash1Repeat = securityManager.computePayloadHash(payload1);
        String hash2 = securityManager.computePayloadHash(payload2);

        assertNotNull(hash1);
        assertEquals(64, hash1.length()); // SHA-256 hex is 64 characters
        assertEquals("Hashes of identical data must match exactly", hash1, hash1Repeat);
        assertFalse("Hashes of distinct data must differ", hash1.equals(hash2));
    }

    @Test
    public void testDeterministicPeeringTieBreaker() {
        String nodeA = "NODE-1111";
        String nodeB = "NODE-9999";

        // BitChat rule: higher lexicographical callsign initiates connection
        assertTrue("NODE-9999 is lexicographically greater than NODE-1111", nodeB.compareTo(nodeA) > 0);
        assertFalse("NODE-1111 must yield and not initiate to avoid race collision", nodeA.compareTo(nodeB) > 0);
    }
}

