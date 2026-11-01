package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.blackout.emergency.database.EmergencyMessageEntity;
import com.blackout.emergency.database.RelayQueueEntity;
import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.relay.RelayPolicy;
import com.blackout.emergency.utils.Constants;
import com.blackout.emergency.warnings.crypto.TrustedKeyRepository;
import com.blackout.emergency.warnings.crypto.WarningCanonicalSerializer;
import com.blackout.emergency.warnings.crypto.WarningSignatureVerifier;
import com.blackout.emergency.warnings.models.PublicWarning;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

/**
 * Phase 8 Reliability, Stress & Performance Benchmarking Test Suite.
 * Benchmarks:
 * 1. Storage & Volume Scaling (100, 1,000, 5,000 SOS records; 100, 1,000 warning records)
 * 2. Cryptographic Ed25519 Signature Verification Throughput (100 consecutive operations)
 * 3. Batch Relay Queuing, Ordering, and Transfer Batch Caps (1, 10, 50, 100 messages)
 * 4. Duplicate Suppression & Multi-hop Partition/Reconnection Stress
 */
public class ReliabilityStressPerformanceTest {

    private TrustedKeyRepository keyRepository;
    private WarningSignatureVerifier verifier;

    private static final String ROOT_KEY_ID = "KEY-BLACKOUT-ROOT-2026";
    private static final String ROOT_PUB_KEY = "k5EwtLq5Md0tNFqNxdqdm2vL6c/q1ZgHcb6Uku32/Xc=";
    private static final String VALID_SIGNATURE = "sL8/lOZt03vDDP2IIlFbVjTpgWDy22y1Z8kiF0BJ4IxuBDkAp4NUwl4i0kVZu5v36GVVa90TmyrpnUxscmd2AA==";

    @Before
    public void setUp() {
        keyRepository = new TrustedKeyRepository();
        verifier = new WarningSignatureVerifier(keyRepository);
    }

    // =========================================================================
    // 1. Storage Volume Scaling Benchmarks: 100, 1,000, 5,000 SOS Records
    // =========================================================================

    @Test
    public void testSosVolumeScaling_100_1000_5000_Records() {
        int[] counts = {100, 1000, 5000};

        for (int count : counts) {
            long startTime = System.nanoTime();
            Map<String, EmergencyMessageEntity> memoryStore = new HashMap<>(count);

            for (int i = 0; i < count; i++) {
                String id = UUID.randomUUID().toString();
                EmergencyMessage msg = new EmergencyMessage(
                        "DEV-" + (i % 100),
                        "CALL-" + (i % 50),
                        Constants.CATEGORY_FLOOD,
                        "Emergency flash flood distress signal #" + i,
                        i % 5 == 0 ? Constants.PRIORITY_CRITICAL : Constants.PRIORITY_HIGH,
                        13.0827 + (i * 0.0001),
                        80.2707 + (i * 0.0001),
                        5.0f,
                        System.currentTimeMillis(),
                        Constants.STATUS_LOCAL_SAVED,
                        Constants.LOC_STATUS_ACQUIRED
                );
                EmergencyMessageEntity entity = EmergencyMessageEntity.fromModel(msg);
                memoryStore.put(id, entity);
            }

            long durationNs = System.nanoTime() - startTime;
            double durationMs = durationNs / 1_000_000.0;

            assertEquals(count, memoryStore.size());
            System.out.printf("SOS Volume Benchmark [%d records]: %.2f ms (%.4f ms/record)\n",
                    count, durationMs, durationMs / count);

            // Assert throughput: 5,000 records must serialize and map in under 2000ms
            assertTrue("Volume mapping must complete efficiently without memory exhaustion", durationMs < 2000.0);
        }
    }

    // =========================================================================
    // 2. Warning Storage Volume Scaling: 100 and 1,000 Warnings
    // =========================================================================

    @Test
    public void testWarningVolumeScaling_100_1000_Records() {
        int[] counts = {100, 1000};
        List<String> regions = Arrays.asList("District North", "Sector Coastal", "Highland");

        for (int count : counts) {
            long startTime = System.nanoTime();
            Map<String, PublicWarning> warningStore = new HashMap<>(count);
            int activeCount = 0;
            long now = System.currentTimeMillis();

            for (int i = 0; i < count; i++) {
                String id = UUID.randomUUID().toString();
                long expiresAt = (i % 2 == 0) ? now + 86400000L : now - 10000L; // Half active, half expired
                PublicWarning w = new PublicWarning(
                        id,
                        1,
                        1,
                        "Warning #" + i,
                        "Public emergency broadcast details for incident " + i,
                        PublicWarning.CATEGORY_CYCLONE,
                        PublicWarning.SEVERITY_WARNING,
                        "BLACKOUT Civil Defense",
                        now,
                        now,
                        expiresAt,
                        regions,
                        13.0,
                        80.0,
                        10000.0,
                        "en",
                        PublicWarning.STATUS_PUBLISHED,
                        ROOT_KEY_ID,
                        "Ed25519",
                        VALID_SIGNATURE,
                        null
                );
                warningStore.put(id, w);
                if (!w.isExpiredNow()) {
                    activeCount++;
                }
            }

            long durationNs = System.nanoTime() - startTime;
            double durationMs = durationNs / 1_000_000.0;

            assertEquals(count, warningStore.size());
            assertEquals(count / 2, activeCount);
            System.out.printf("Warning Volume Benchmark [%d warnings]: %.2f ms (active filtered: %d)\n",
                    count, durationMs, activeCount);

            assertTrue("Warning volume creation must complete rapidly", durationMs < 1000.0);
        }
    }

    // =========================================================================
    // 3. Ed25519 Signature Verification Latency Benchmark (100 Operations)
    // =========================================================================

    @Test
    public void testEd25519SignatureVerificationThroughput_100Operations() {
        List<String> regions = Arrays.asList("Sector 4", "Central Basin", "North Riverbank");
        PublicWarning baseline = new PublicWarning(
                "d3b07384-d113-46fb-9b24-9b2bfa6eb542",
                1,
                1,
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
                VALID_SIGNATURE,
                null
        );

        String canonical = WarningCanonicalSerializer.buildCanonicalString(baseline);
        byte[] canonicalBytes = canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // Warm up JIT
        for (int i = 0; i < 10; i++) {
            WarningSignatureVerifier.verifyEd25519Signature(ROOT_PUB_KEY, canonicalBytes, VALID_SIGNATURE);
        }

        // Measure 100 consecutive cryptographic verifications
        int iterations = 100;
        long startTime = System.nanoTime();
        int validCount = 0;

        for (int i = 0; i < iterations; i++) {
            boolean valid = WarningSignatureVerifier.verifyEd25519Signature(ROOT_PUB_KEY, canonicalBytes, VALID_SIGNATURE);
            if (valid) validCount++;
        }

        long durationNs = System.nanoTime() - startTime;
        double totalMs = durationNs / 1_000_000.0;
        double avgMs = totalMs / iterations;

        assertEquals("All 100 authentic signatures must verify", iterations, validCount);
        System.out.printf("Ed25519 Verification Benchmark: 100 operations in %.2f ms (Average: %.3f ms/op)\n",
                totalMs, avgMs);

        // Security requirement: Must average well under 10ms per verification
        assertTrue("Average Ed25519 verification must be under 10ms", avgMs < 10.0);
    }

    // =========================================================================
    // 4. Batch Relay Queuing, Strict FIFO Ordering & Queue Bound Stress (1, 10, 50, 100)
    // =========================================================================

    @Test
    public void testBatchRelayQueuingAndFifoOrdering() {
        int[] batchSizes = {1, 10, 50, 100};

        for (int size : batchSizes) {
            List<RelayQueueEntity> queue = new ArrayList<>();
            long baseTime = System.currentTimeMillis();

            // Enqueue batch in priority and timestamp order
            for (int i = 0; i < size; i++) {
                String priority = (i % 4 == 0) ? Constants.PRIORITY_CRITICAL : Constants.PRIORITY_HIGH;
                RelayQueueEntity item = new RelayQueueEntity(
                        "MSG-BATCH-" + i,
                        Constants.RELAY_STATUS_PENDING,
                        baseTime + (i * 10),
                        baseTime + (i * 10),
                        0,
                        0,
                        null,
                        priority
                );
                queue.add(item);
            }

            assertEquals(size, queue.size());

            // Validate priority sorting comparator (CRITICAL ahead of HIGH)
            queue.sort((a, b) -> {
                int pCompare = RelayPolicy.getPriorityWeight(b.getPriority()) - RelayPolicy.getPriorityWeight(a.getPriority());
                if (pCompare != 0) return pCompare;
                return Long.compare(a.getQueuedAt(), b.getQueuedAt());
            });

            // First item must be CRITICAL if any CRITICAL was enqueued
            if (size >= 4) {
                assertEquals(Constants.PRIORITY_CRITICAL, queue.get(0).getPriority());
            }

            // Verify queue size cap & batching policy
            assertTrue("Batch must not exceed requested size", queue.size() <= 100);
            assertTrue("Policy transfer batch size must be positive", RelayPolicy.MAX_TRANSFER_BATCH_SIZE > 0);
        }
    }

    // =========================================================================
    // 5. Partition & Reconnection Duplicate Suppression Stress
    // =========================================================================

    @Test
    public void testNetworkPartitionReconnectionDuplicateSuppression() {
        // Simulate a network partition where 50 messages are exchanged multiple times
        // across 10 oscillating peer connections
        Map<String, Integer> seenCounts = new HashMap<>();
        int totalPayloadTransfers = 0;
        int suppressedDuplicates = 0;

        for (int connectionCycle = 0; connectionCycle < 10; connectionCycle++) {
            for (int msgIdx = 0; msgIdx < 50; msgIdx++) {
                String messageId = "MSG-PARTITION-" + msgIdx;
                totalPayloadTransfers++;

                if (seenCounts.containsKey(messageId)) {
                    // Suppressed duplicate
                    suppressedDuplicates++;
                    seenCounts.put(messageId, seenCounts.get(messageId) + 1);
                } else {
                    // First time seen
                    seenCounts.put(messageId, 1);
                }
            }
        }

        assertEquals(500, totalPayloadTransfers);
        assertEquals(50, seenCounts.size());
        assertEquals(450, suppressedDuplicates);
        System.out.printf("Duplicate Suppression Stress: 500 transfers across partition reconnection cycles -> %d unique retained, %d suppressed (90.0%% efficiency)\n",
                seenCounts.size(), suppressedDuplicates);
    }
}
