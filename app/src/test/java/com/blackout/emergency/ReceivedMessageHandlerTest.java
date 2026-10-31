package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.blackout.emergency.database.EmergencyMessageDao;
import com.blackout.emergency.database.EmergencyMessageEntity;
import com.blackout.emergency.database.EmergencyRepository;
import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.nearby.ReceivedMessageHandler;
import com.blackout.emergency.nearby.SosPayloadSerializer;
import com.blackout.emergency.utils.Constants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests verifying ReceivedMessageHandler logic, duplicate prevention,
 * schema validation, and transfer status updates off the main thread.
 */
public class ReceivedMessageHandlerTest {

    private EmergencyRepository repository;
    private ReceivedMessageHandler messageHandler;
    private final Map<String, EmergencyMessageEntity> inMemoryDb = new HashMap<>();

    @Before
    public void setUp() {
        inMemoryDb.clear();

        EmergencyMessageDao fakeDao = new EmergencyMessageDao() {
            @Override
            public void insert(EmergencyMessageEntity entity) {
                inMemoryDb.put(entity.getMessageId(), entity);
            }

            @Override
            public void insertAll(List<EmergencyMessageEntity> entities) {
                for (EmergencyMessageEntity e : entities) {
                    inMemoryDb.put(e.getMessageId(), e);
                }
            }

            @Override
            public void update(EmergencyMessageEntity entity) {
                inMemoryDb.put(entity.getMessageId(), entity);
            }

            @Override
            public void delete(EmergencyMessageEntity entity) {
                inMemoryDb.remove(entity.getMessageId());
            }

            @Override
            public void deleteById(String id) {
                inMemoryDb.remove(id);
            }

            @Override
            public List<EmergencyMessageEntity> getAllMessages() {
                return new ArrayList<>(inMemoryDb.values());
            }

            @Override
            public EmergencyMessageEntity getMessageById(String id) {
                return inMemoryDb.get(id);
            }

            @Override
            public List<EmergencyMessageEntity> getMessagesByCategory(String category) {
                List<EmergencyMessageEntity> result = new ArrayList<>();
                for (EmergencyMessageEntity e : inMemoryDb.values()) {
                    if (category.equals(e.getEmergencyCategory())) {
                        result.add(e);
                    }
                }
                return result;
            }

            @Override
            public int getMessageCount() {
                return inMemoryDb.size();
            }

            @Override
            public void updateStatus(String id, String status) {
                EmergencyMessageEntity entity = inMemoryDb.get(id);
                if (entity != null) {
                    entity.setStatus(status);
                }
            }

            @Override
            public List<EmergencyMessageEntity> getOutgoingMessages() {
                List<EmergencyMessageEntity> result = new ArrayList<>();
                for (EmergencyMessageEntity e : inMemoryDb.values()) {
                    if (!Constants.STATUS_RECEIVED.equals(e.getStatus())) {
                        result.add(e);
                    }
                }
                return result;
            }

            @Override
            public int countById(String id) {
                return inMemoryDb.containsKey(id) ? 1 : 0;
            }

            @Override
            public void deleteAll() {
                inMemoryDb.clear();
            }
        };

        // Same-thread executor for deterministic unit testing
        ExecutorService directExecutor = Executors.newSingleThreadExecutor();
        repository = new EmergencyRepository(fakeDao, directExecutor);
        messageHandler = new ReceivedMessageHandler(repository, directExecutor);
    }

    @Test
    public void testProcessValidIncomingPayload() throws Exception {
        EmergencyMessage original = new EmergencyMessage(
                "DEV-PEER-1",
                "NODE-PEER",
                Constants.CATEGORY_ROAD_ACCIDENT,
                "Multi-vehicle collision on highway. Ambulance required.",
                Constants.PRIORITY_HIGH,
                19.0760,
                72.8777,
                4.0f,
                System.currentTimeMillis(),
                Constants.STATUS_LOCAL_SAVED,
                Constants.LOC_STATUS_ACQUIRED
        );

        byte[] payloadBytes = SosPayloadSerializer.serialize(original);

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<ReceivedMessageHandler.ProcessingOutcome> outcomeRef = new AtomicReference<>();
        final AtomicReference<EmergencyMessage> msgRef = new AtomicReference<>();

        messageHandler.processIncomingPayload(payloadBytes, (outcome, message, error) -> {
            outcomeRef.set(outcome);
            msgRef.set(message);
            latch.countDown();
        });

        // Wait for handler execution
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        assertEquals(ReceivedMessageHandler.ProcessingOutcome.SUCCESS, outcomeRef.get());
        assertNotNull(msgRef.get());
        assertEquals(Constants.STATUS_RECEIVED, msgRef.get().getStatus());
        assertEquals(original.getMessageId(), msgRef.get().getMessageId());
        assertEquals("NODE-PEER", msgRef.get().getSenderCallsign());

        // Verify in-memory database contains record
        EmergencyMessageEntity savedEntity = inMemoryDb.get(original.getMessageId());
        assertNotNull(savedEntity);
        assertEquals(Constants.STATUS_RECEIVED, savedEntity.getStatus());
    }

    @Test
    public void testPreventDuplicateReceivedMessage() throws Exception {
        EmergencyMessage original = new EmergencyMessage(
                "DEV-PEER-2",
                "NODE-PEER2",
                Constants.CATEGORY_DISASTER,
                "Earthquake damage near district hospital.",
                Constants.PRIORITY_CRITICAL,
                null,
                null,
                null,
                null,
                Constants.STATUS_LOCAL_SAVED,
                Constants.LOC_STATUS_UNAVAILABLE
        );

        byte[] payloadBytes = SosPayloadSerializer.serialize(original);

        // 1. First reception: should succeed
        final CountDownLatch latch1 = new CountDownLatch(1);
        final AtomicReference<ReceivedMessageHandler.ProcessingOutcome> outcome1 = new AtomicReference<>();
        messageHandler.processIncomingPayload(payloadBytes, (outcome, message, error) -> {
            outcome1.set(outcome);
            latch1.countDown();
        });
        assertTrue(latch1.await(3, TimeUnit.SECONDS));
        assertEquals(ReceivedMessageHandler.ProcessingOutcome.SUCCESS, outcome1.get());
        assertEquals(1, inMemoryDb.size());

        // 2. Second reception of exact same payload: should be rejected as DUPLICATE_IGNORED
        final CountDownLatch latch2 = new CountDownLatch(1);
        final AtomicReference<ReceivedMessageHandler.ProcessingOutcome> outcome2 = new AtomicReference<>();
        messageHandler.processIncomingPayload(payloadBytes, (outcome, message, error) -> {
            outcome2.set(outcome);
            latch2.countDown();
        });
        assertTrue(latch2.await(3, TimeUnit.SECONDS));
        assertEquals(ReceivedMessageHandler.ProcessingOutcome.DUPLICATE_IGNORED, outcome2.get());
        assertEquals(1, inMemoryDb.size()); // Size did not change, not overwritten
    }

    @Test
    public void testRejectCorruptedPayload() throws Exception {
        byte[] corruptedBytes = "NOT_A_VALID_JSON_OBJECT".getBytes();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<ReceivedMessageHandler.ProcessingOutcome> outcomeRef = new AtomicReference<>();

        messageHandler.processIncomingPayload(corruptedBytes, (outcome, message, error) -> {
            outcomeRef.set(outcome);
            latch.countDown();
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(ReceivedMessageHandler.ProcessingOutcome.INVALID_PAYLOAD, outcomeRef.get());
        assertEquals(0, inMemoryDb.size());
    }

    @Test
    public void testTransferStateUpdates() throws Exception {
        EmergencyMessage original = new EmergencyMessage(
                "LOCAL_DEV",
                "NODE-LOCAL",
                Constants.CATEGORY_FIRE,
                "Commercial warehouse fire.",
                Constants.PRIORITY_CRITICAL,
                12.3456,
                78.9012,
                Constants.STATUS_LOCAL_SAVED
        );

        // Insert locally
        inMemoryDb.put(original.getMessageId(), EmergencyMessageEntity.fromModel(original));

        // Update to TRANSFER_IN_PROGRESS
        CountDownLatch latch1 = new CountDownLatch(1);
        repository.updateMessageStatus(original.getMessageId(), Constants.STATUS_TRANSFER_IN_PROGRESS, success -> latch1.countDown());
        assertTrue(latch1.await(3, TimeUnit.SECONDS));
        assertEquals(Constants.STATUS_TRANSFER_IN_PROGRESS, inMemoryDb.get(original.getMessageId()).getStatus());

        // Update to TRANSFERRED_TO_NEARBY_DEVICE
        CountDownLatch latch2 = new CountDownLatch(1);
        repository.updateMessageStatus(original.getMessageId(), Constants.STATUS_TRANSFERRED, success -> latch2.countDown());
        assertTrue(latch2.await(3, TimeUnit.SECONDS));
        assertEquals(Constants.STATUS_TRANSFERRED, inMemoryDb.get(original.getMessageId()).getStatus());

        // Update to TRANSFER_FAILED
        CountDownLatch latch3 = new CountDownLatch(1);
        repository.updateMessageStatus(original.getMessageId(), Constants.STATUS_TRANSFER_FAILED, success -> latch3.countDown());
        assertTrue(latch3.await(3, TimeUnit.SECONDS));
        assertEquals(Constants.STATUS_TRANSFER_FAILED, inMemoryDb.get(original.getMessageId()).getStatus());
    }
}
