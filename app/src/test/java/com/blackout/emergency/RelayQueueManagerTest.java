package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.blackout.emergency.database.EmergencyMessageDao;
import com.blackout.emergency.database.EmergencyMessageEntity;
import com.blackout.emergency.database.RelayHistoryDao;
import com.blackout.emergency.database.RelayHistoryEntity;
import com.blackout.emergency.database.RelayQueueDao;
import com.blackout.emergency.database.RelayQueueEntity;
import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.relay.RelayPolicy;
import com.blackout.emergency.relay.RelayQueueManager;
import com.blackout.emergency.utils.Constants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests verifying store-carry-and-forward relay queue operations:
 * - Message enqueueing with eligibility checks
 * - Prioritized retrieval against peer inventories
 * - Duplicate suppression
 * - Delivery success acknowledgements and history tracking
 * - Bounded retry backoff scheduling
 */
public class RelayQueueManagerTest {

    private final Map<String, RelayQueueEntity> inMemoryQueue = new HashMap<>();
    private final List<RelayHistoryEntity> inMemoryHistory = new ArrayList<>();
    private final Map<String, EmergencyMessageEntity> inMemoryMessages = new HashMap<>();

    private RelayQueueManager queueManager;

    @Before
    public void setUp() {
        inMemoryQueue.clear();
        inMemoryHistory.clear();
        inMemoryMessages.clear();

        RelayQueueDao fakeQueueDao = new RelayQueueDao() {
            @Override
            public long insert(RelayQueueEntity entity) {
                if (inMemoryQueue.containsKey(entity.getMessageId())) {
                    return -1;
                }
                inMemoryQueue.put(entity.getMessageId(), entity);
                return 1;
            }

            @Override
            public void update(RelayQueueEntity entity) {
                inMemoryQueue.put(entity.getMessageId(), entity);
            }

            @Override
            public RelayQueueEntity getByMessageId(String messageId) {
                return inMemoryQueue.get(messageId);
            }

            @Override
            public List<RelayQueueEntity> getEligibleQueueItems(long now, int limit) {
                List<RelayQueueEntity> result = new ArrayList<>();
                for (RelayQueueEntity e : inMemoryQueue.values()) {
                    if ((Constants.RELAY_STATUS_PENDING.equals(e.getRelayStatus()) ||
                            Constants.RELAY_STATUS_RETRY_WAIT.equals(e.getRelayStatus())) &&
                            e.getNextAttemptAt() <= now) {
                        result.add(e);
                    }
                }
                return result;
            }

            @Override
            public List<RelayQueueEntity> getAllQueueItems() {
                return new ArrayList<>(inMemoryQueue.values());
            }

            @Override
            public void updateStatus(String messageId, String status, long now) {
                RelayQueueEntity e = inMemoryQueue.get(messageId);
                if (e != null) {
                    e.setRelayStatus(status);
                    e.setLastAttemptAt(now);
                }
            }

            @Override
            public void updateRetry(String messageId, String status, int attemptCount, long nextAttemptAt, long lastAttemptAt, String error) {
                RelayQueueEntity e = inMemoryQueue.get(messageId);
                if (e != null) {
                    e.setRelayStatus(status);
                    e.setAttemptCount(attemptCount);
                    e.setNextAttemptAt(nextAttemptAt);
                    e.setLastAttemptAt(lastAttemptAt);
                    e.setLastError(error);
                }
            }

            @Override
            public void deleteByMessageId(String messageId) {
                inMemoryQueue.remove(messageId);
            }

            @Override
            public int getPendingRelayCount() {
                int count = 0;
                for (RelayQueueEntity e : inMemoryQueue.values()) {
                    if (Constants.RELAY_STATUS_PENDING.equals(e.getRelayStatus()) ||
                            Constants.RELAY_STATUS_RETRY_WAIT.equals(e.getRelayStatus()) ||
                            Constants.RELAY_STATUS_IN_PROGRESS.equals(e.getRelayStatus())) {
                        count++;
                    }
                }
                return count;
            }

            @Override
            public void deleteAll() {
                inMemoryQueue.clear();
            }
        };

        RelayHistoryDao fakeHistoryDao = new RelayHistoryDao() {
            @Override
            public long insert(RelayHistoryEntity entity) {
                inMemoryHistory.add(entity);
                return inMemoryHistory.size();
            }

            @Override
            public List<RelayHistoryEntity> getHistoryForMessage(String messageId) {
                List<RelayHistoryEntity> res = new ArrayList<>();
                for (RelayHistoryEntity e : inMemoryHistory) {
                    if (messageId.equals(e.getMessageId())) {
                        res.add(e);
                    }
                }
                return res;
            }

            @Override
            public List<RelayHistoryEntity> getRecentHistory(int limit) {
                return new ArrayList<>(inMemoryHistory);
            }

            @Override
            public int countDeliveredToPeer(String messageId, String peerDeviceId) {
                int count = 0;
                for (RelayHistoryEntity e : inMemoryHistory) {
                    if (messageId.equals(e.getMessageId()) && peerDeviceId.equals(e.getToDeviceId())
                            && Constants.STATUS_RELAYED.equals(e.getTransferStatus())) {
                        count++;
                    }
                }
                return count;
            }

            @Override
            public int getTotalRelayCount() {
                return inMemoryHistory.size();
            }

            @Override
            public void deleteAll() {
                inMemoryHistory.clear();
            }
        };

        EmergencyMessageDao fakeMessageDao = new EmergencyMessageDao() {
            @Override
            public void insert(EmergencyMessageEntity entity) {
                inMemoryMessages.put(entity.getMessageId(), entity);
            }

            @Override
            public void insertAll(List<EmergencyMessageEntity> entities) {
                for (EmergencyMessageEntity e : entities) inMemoryMessages.put(e.getMessageId(), e);
            }

            @Override
            public void update(EmergencyMessageEntity entity) {
                inMemoryMessages.put(entity.getMessageId(), entity);
            }

            @Override
            public void delete(EmergencyMessageEntity entity) {
                inMemoryMessages.remove(entity.getMessageId());
            }

            @Override
            public void deleteById(String id) {
                inMemoryMessages.remove(id);
            }

            @Override
            public List<EmergencyMessageEntity> getAllMessages() {
                return new ArrayList<>(inMemoryMessages.values());
            }

            @Override
            public EmergencyMessageEntity getMessageById(String id) {
                return inMemoryMessages.get(id);
            }

            @Override
            public List<EmergencyMessageEntity> getMessagesByCategory(String category) {
                return Collections.emptyList();
            }

            @Override
            public int getMessageCount() {
                return inMemoryMessages.size();
            }

            @Override
            public void updateStatus(String id, String status) {
                EmergencyMessageEntity e = inMemoryMessages.get(id);
                if (e != null) {
                    e.setStatus(status);
                }
            }

            @Override
            public List<EmergencyMessageEntity> getOutgoingMessages() {
                return new ArrayList<>(inMemoryMessages.values());
            }

            @Override
            public int countById(String id) {
                return inMemoryMessages.containsKey(id) ? 1 : 0;
            }

            @Override
            public void deleteAll() {
                inMemoryMessages.clear();
            }
        };

        ExecutorService directExecutor = Executors.newSingleThreadExecutor();
        queueManager = new RelayQueueManager(fakeQueueDao, fakeHistoryDao, fakeMessageDao, directExecutor);
    }

    @Test
    public void testEnqueueEligibleSos() throws Exception {
        EmergencyMessage msg = new EmergencyMessage(
                "DEV-A", "NODE-A", Constants.CATEGORY_FLOOD,
                "Flash flood warning. Water rising rapidly.", Constants.PRIORITY_CRITICAL,
                28.6, 77.2, Constants.STATUS_LOCAL_SAVED
        );
        inMemoryMessages.put(msg.getMessageId(), EmergencyMessageEntity.fromModel(msg));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean result = new AtomicBoolean(false);
        queueManager.enqueueForRelay(msg, success -> {
            result.set(Boolean.TRUE.equals(success));
            latch.countDown();
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertTrue(result.get());
        assertTrue(inMemoryQueue.containsKey(msg.getMessageId()));
        assertEquals(Constants.RELAY_STATUS_PENDING, inMemoryQueue.get(msg.getMessageId()).getRelayStatus());
    }

    @Test
    public void testDuplicateEnqueueIsSuppressed() throws Exception {
        EmergencyMessage msg = new EmergencyMessage(
                "DEV-A", "NODE-A", Constants.CATEGORY_FIRE,
                "Forest fire spreading east.", Constants.PRIORITY_HIGH,
                28.6, 77.2, Constants.STATUS_LOCAL_SAVED
        );
        inMemoryMessages.put(msg.getMessageId(), EmergencyMessageEntity.fromModel(msg));

        CountDownLatch latch1 = new CountDownLatch(1);
        queueManager.enqueueForRelay(msg, s -> latch1.countDown());
        assertTrue(latch1.await(3, TimeUnit.SECONDS));

        // Re-enqueue same message
        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicBoolean secondResult = new AtomicBoolean(true);
        queueManager.enqueueForRelay(msg, success -> {
            secondResult.set(Boolean.TRUE.equals(success));
            latch2.countDown();
        });
        assertTrue(latch2.await(3, TimeUnit.SECONDS));
        assertFalse(secondResult.get());
        assertEquals(1, inMemoryQueue.size());
    }

    @Test
    public void testMaxHopMessageRejectedFromQueue() throws Exception {
        EmergencyMessage msg = new EmergencyMessage(
                "DEV-A", "NODE-A", Constants.CATEGORY_TRAPPED,
                "Trapped civilian.", Constants.PRIORITY_CRITICAL,
                28.6, 77.2, Constants.STATUS_LOCAL_SAVED
        );
        msg.setHopCount(RelayPolicy.DEFAULT_MAX_HOPS);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean result = new AtomicBoolean(true);
        queueManager.enqueueForRelay(msg, success -> {
            result.set(Boolean.TRUE.equals(success));
            latch.countDown();
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertFalse(result.get());
        assertTrue(inMemoryQueue.isEmpty());
    }

    @Test
    public void testGetEligibleMessagesForPeerFiltersKnownInventory() throws Exception {
        EmergencyMessage msg1 = new EmergencyMessage(
                "DEV-A", "NODE-A", Constants.CATEGORY_MEDICAL, "SOS 1",
                Constants.PRIORITY_CRITICAL, 28.6, 77.2, Constants.STATUS_LOCAL_SAVED
        );
        EmergencyMessage msg2 = new EmergencyMessage(
                "DEV-A", "NODE-A", Constants.CATEGORY_FIRE, "SOS 2",
                Constants.PRIORITY_HIGH, 28.6, 77.2, Constants.STATUS_LOCAL_SAVED
        );

        inMemoryMessages.put(msg1.getMessageId(), EmergencyMessageEntity.fromModel(msg1));
        inMemoryMessages.put(msg2.getMessageId(), EmergencyMessageEntity.fromModel(msg2));

        CountDownLatch l1 = new CountDownLatch(2);
        queueManager.enqueueForRelay(msg1, s -> l1.countDown());
        queueManager.enqueueForRelay(msg2, s -> l1.countDown());
        assertTrue(l1.await(3, TimeUnit.SECONDS));

        // Peer B already has msg1 in its inventory
        List<String> peerKnown = Collections.singletonList(msg1.getMessageId());

        CountDownLatch l2 = new CountDownLatch(1);
        AtomicReference<List<EmergencyMessage>> eligibleRef = new AtomicReference<>();
        queueManager.getEligibleMessagesForPeer("DEV-PEER-B", peerKnown, 10, msgs -> {
            eligibleRef.set(msgs);
            l2.countDown();
        });
        assertTrue(l2.await(3, TimeUnit.SECONDS));

        List<EmergencyMessage> eligible = eligibleRef.get();
        assertNotNull(eligible);
        assertEquals(1, eligible.size());
        assertEquals(msg2.getMessageId(), eligible.get(0).getMessageId());
    }

    @Test
    public void testRecordDeliverySuccessUpdatesQueueAndHistory() throws Exception {
        EmergencyMessage msg = new EmergencyMessage(
                "DEV-A", "NODE-A", Constants.CATEGORY_OTHER, "Urgent call for shelter.",
                Constants.PRIORITY_MODERATE, 28.6, 77.2, Constants.STATUS_LOCAL_SAVED
        );
        inMemoryMessages.put(msg.getMessageId(), EmergencyMessageEntity.fromModel(msg));

        CountDownLatch l1 = new CountDownLatch(1);
        queueManager.enqueueForRelay(msg, s -> l1.countDown());
        assertTrue(l1.await(3, TimeUnit.SECONDS));

        // Record delivery success
        CountDownLatch l2 = new CountDownLatch(1);
        queueManager.recordDeliverySuccess(msg.getMessageId(), "DEV-LOCAL", "DEV-PEER-B", 1, s -> l2.countDown());
        assertTrue(l2.await(3, TimeUnit.SECONDS));

        // Verify queue status
        assertEquals(Constants.RELAY_STATUS_RELAYED_TO_PEER, inMemoryQueue.get(msg.getMessageId()).getRelayStatus());
        // Verify message status
        assertEquals(Constants.STATUS_TRANSFERRED, inMemoryMessages.get(msg.getMessageId()).getStatus());
        // Verify history logged
        assertEquals(1, inMemoryHistory.size());
        assertEquals("DEV-PEER-B", inMemoryHistory.get(0).getToDeviceId());
    }

    @Test
    public void testRecordDeliveryFailureSchedulesRetry() throws Exception {
        EmergencyMessage msg = new EmergencyMessage(
                "DEV-A", "NODE-A", Constants.CATEGORY_MEDICAL, "Insulin needed.",
                Constants.PRIORITY_CRITICAL, 28.6, 77.2, Constants.STATUS_LOCAL_SAVED
        );
        inMemoryMessages.put(msg.getMessageId(), EmergencyMessageEntity.fromModel(msg));

        CountDownLatch l1 = new CountDownLatch(1);
        queueManager.enqueueForRelay(msg, s -> l1.countDown());
        assertTrue(l1.await(3, TimeUnit.SECONDS));

        // First failure: should enter RETRY_WAIT with attempt count 1
        CountDownLatch l2 = new CountDownLatch(1);
        queueManager.recordDeliveryFailure(msg.getMessageId(), "Peer disconnected abruptly", s -> l2.countDown());
        assertTrue(l2.await(3, TimeUnit.SECONDS));

        RelayQueueEntity item = inMemoryQueue.get(msg.getMessageId());
        assertNotNull(item);
        assertEquals(Constants.RELAY_STATUS_RETRY_WAIT, item.getRelayStatus());
        assertEquals(1, item.getAttemptCount());
        assertTrue(item.getNextAttemptAt() > System.currentTimeMillis());
    }
}
