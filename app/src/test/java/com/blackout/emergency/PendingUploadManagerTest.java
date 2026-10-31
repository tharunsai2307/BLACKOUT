package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.blackout.emergency.database.EmergencyMessageDao;
import com.blackout.emergency.database.EmergencyMessageEntity;
import com.blackout.emergency.database.PendingUploadDao;
import com.blackout.emergency.database.PendingUploadEntity;
import com.blackout.emergency.gateway.BackendApiClient;
import com.blackout.emergency.gateway.PendingUploadManager;
import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.utils.Constants;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for PendingUploadManager:
 * - Queueing newly created and received messages
 * - Processing eligible batches
 * - State transitions: PENDING -> BACKEND_ACCEPTED, DUPLICATE_ACCEPTED, RETRY_WAIT, REJECTED, EXPIRED
 * - Exponential backoff calculation
 */
public class PendingUploadManagerTest {

    private FakePendingUploadDao uploadDao;
    private FakeEmergencyMessageDao messageDao;
    private BackendApiClient apiClient;
    private ExecutorService directExecutor;
    private PendingUploadManager uploadManager;

    @Before
    public void setUp() {
        uploadDao = new FakePendingUploadDao();
        messageDao = new FakeEmergencyMessageDao();
        apiClient = new BackendApiClient("https://fake-supabase.co", "test-key");
        directExecutor = Executors.newSingleThreadExecutor();

        uploadManager = new PendingUploadManager(null, uploadDao, messageDao, apiClient, directExecutor);
    }

    private EmergencyMessage createSampleMessage(String id) {
        EmergencyMessage msg = new EmergencyMessage(
                "DEV-PHONE-1",
                "NODE-7F84",
                Constants.CATEGORY_FIRE,
                "Structural fire detected on 3rd floor. Evacuating building.",
                Constants.PRIORITY_CRITICAL,
                12.9716,
                77.5946,
                Constants.STATUS_LOCAL_SAVED
        );
        msg.setMessageId(id);
        return msg;
    }

    @Test
    public void testEnqueueForUploadInsertsRecord() throws Exception {
        EmergencyMessage msg = createSampleMessage(UUID.randomUUID().toString());

        uploadManager.enqueueForUpload(msg, null);
        Thread.sleep(100);

        PendingUploadEntity entity = uploadDao.getByMessageId(msg.getMessageId());
        assertNotNull(entity);
        assertEquals(msg.getMessageId(), entity.getMessageId());
        assertEquals(Constants.UPLOAD_STATUS_PENDING, entity.getUploadStatus());
        assertEquals(0, entity.getAttemptCount());
    }

    @Test
    public void testFlushPendingUploadsSuccess() throws Exception {
        String msgId = UUID.randomUUID().toString();
        EmergencyMessage msg = createSampleMessage(msgId);
        messageDao.insert(EmergencyMessageEntity.fromModel(msg));

        uploadDao.insert(new PendingUploadEntity(
                msgId,
                Constants.UPLOAD_STATUS_PENDING,
                "GATEWAY-TEST",
                0,
                System.currentTimeMillis() - 1000,
                0L, 0L, null, null
        ));

        // Mock 201 Created from backend
        final String successResp = "{\"status\":\"SUCCESS\",\"backendReference\":\"REF-XYZ-123\"}";
        apiClient.setConnectionProvider(url -> new FakeHttpURLConnection(201, successResp));

        uploadManager.flushPendingUploads(null);
        Thread.sleep(150);

        PendingUploadEntity updated = uploadDao.getByMessageId(msgId);
        assertNotNull(updated);
        assertEquals(Constants.UPLOAD_STATUS_BACKEND_ACCEPTED, updated.getUploadStatus());
        assertEquals("REF-XYZ-123", updated.getBackendReference());
        assertTrue(updated.getUploadedAt() > 0);
    }

    @Test
    public void testFlushPendingUploadsDuplicateAccepted() throws Exception {
        String msgId = UUID.randomUUID().toString();
        EmergencyMessage msg = createSampleMessage(msgId);
        messageDao.insert(EmergencyMessageEntity.fromModel(msg));

        uploadDao.insert(new PendingUploadEntity(
                msgId,
                Constants.UPLOAD_STATUS_PENDING,
                "GATEWAY-TEST",
                0,
                System.currentTimeMillis() - 1000,
                0L, 0L, null, null
        ));

        // Mock 200 Duplicate Accepted from backend
        final String dupResp = "{\"status\":\"DUPLICATE_ACCEPTED\",\"backendReference\":\"EXISTING-REF-999\"}";
        apiClient.setConnectionProvider(url -> new FakeHttpURLConnection(200, dupResp));

        uploadManager.flushPendingUploads(null);
        Thread.sleep(150);

        PendingUploadEntity updated = uploadDao.getByMessageId(msgId);
        assertNotNull(updated);
        assertEquals(Constants.UPLOAD_STATUS_DUPLICATE_ACCEPTED, updated.getUploadStatus());
        assertEquals("EXISTING-REF-999", updated.getBackendReference());
    }

    @Test
    public void testFlushPendingUploadsRetryableFailureUpdatesBackoff() throws Exception {
        String msgId = UUID.randomUUID().toString();
        EmergencyMessage msg = createSampleMessage(msgId);
        messageDao.insert(EmergencyMessageEntity.fromModel(msg));

        uploadDao.insert(new PendingUploadEntity(
                msgId,
                Constants.UPLOAD_STATUS_PENDING,
                "GATEWAY-TEST",
                0,
                System.currentTimeMillis() - 1000,
                0L, 0L, null, null
        ));

        // Mock 500 Server Error
        final String errResp = "{\"error\":\"DATABASE_TIMEOUT\"}";
        apiClient.setConnectionProvider(url -> new FakeHttpURLConnection(500, errResp));

        long beforeFlush = System.currentTimeMillis();
        uploadManager.flushPendingUploads(null);
        Thread.sleep(150);

        PendingUploadEntity updated = uploadDao.getByMessageId(msgId);
        assertNotNull(updated);
        assertEquals(Constants.UPLOAD_STATUS_RETRY_WAIT, updated.getUploadStatus());
        assertEquals(1, updated.getAttemptCount());
        assertTrue(updated.getNextAttemptAt() >= beforeFlush + 5000L); // Exponential backoff
    }

    @Test
    public void testFlushPendingUploadsNonRetryableRejection() throws Exception {
        String msgId = UUID.randomUUID().toString();
        EmergencyMessage msg = createSampleMessage(msgId);
        messageDao.insert(EmergencyMessageEntity.fromModel(msg));

        uploadDao.insert(new PendingUploadEntity(
                msgId,
                Constants.UPLOAD_STATUS_PENDING,
                "GATEWAY-TEST",
                0,
                System.currentTimeMillis() - 1000,
                0L, 0L, null, null
        ));

        // Mock 400 Bad Request
        final String errResp = "{\"error\":\"INVALID_CATEGORY\",\"message\":\"Bad category\"}";
        apiClient.setConnectionProvider(url -> new FakeHttpURLConnection(400, errResp));

        uploadManager.flushPendingUploads(null);
        Thread.sleep(150);

        PendingUploadEntity updated = uploadDao.getByMessageId(msgId);
        assertNotNull(updated);
        assertEquals(Constants.UPLOAD_STATUS_REJECTED, updated.getUploadStatus());
        assertEquals(Long.MAX_VALUE, updated.getNextAttemptAt());
    }

    @Test
    public void testFlushPendingUploadsExpiredAlertMarkedExpired() throws Exception {
        String msgId = UUID.randomUUID().toString();
        EmergencyMessage msg = createSampleMessage(msgId);
        msg.setExpiresAt(System.currentTimeMillis() - 1000); // Already expired
        messageDao.insert(EmergencyMessageEntity.fromModel(msg));

        uploadDao.insert(new PendingUploadEntity(
                msgId,
                Constants.UPLOAD_STATUS_PENDING,
                "GATEWAY-TEST",
                0,
                System.currentTimeMillis() - 1000,
                0L, 0L, null, null
        ));

        uploadManager.flushPendingUploads(null);
        Thread.sleep(150);

        PendingUploadEntity updated = uploadDao.getByMessageId(msgId);
        assertNotNull(updated);
        assertEquals(Constants.UPLOAD_STATUS_EXPIRED, updated.getUploadStatus());
    }

    // In-memory fake DAO for PendingUploadEntity
    private static class FakePendingUploadDao implements PendingUploadDao {
        private final Map<String, PendingUploadEntity> map = new HashMap<>();

        @Override
        public synchronized long insert(PendingUploadEntity entity) {
            map.put(entity.getMessageId(), entity);
            return 1L;
        }

        @Override
        public synchronized void update(PendingUploadEntity entity) {
            map.put(entity.getMessageId(), entity);
        }

        @Override
        public synchronized PendingUploadEntity getByMessageId(String messageId) {
            return map.get(messageId);
        }

        @Override
        public synchronized List<PendingUploadEntity> getEligibleUploads(long now, int limit) {
            List<PendingUploadEntity> list = new ArrayList<>();
            for (PendingUploadEntity entity : map.values()) {
                if ((Constants.UPLOAD_STATUS_PENDING.equals(entity.getUploadStatus())
                        || Constants.UPLOAD_STATUS_RETRY_WAIT.equals(entity.getUploadStatus()))
                        && entity.getNextAttemptAt() <= now) {
                    list.add(entity);
                    if (list.size() >= limit) break;
                }
            }
            return list;
        }

        @Override
        public synchronized List<PendingUploadEntity> getAllUploads() {
            return new ArrayList<>(map.values());
        }

        @Override
        public synchronized void markAccepted(String messageId, String status, long uploadedAt, String backendRef) {
            PendingUploadEntity entity = map.get(messageId);
            if (entity != null) {
                entity.setUploadStatus(status);
                entity.setUploadedAt(uploadedAt);
                entity.setBackendReference(backendRef);
            }
        }

        @Override
        public synchronized void updateRetry(String messageId, String status, int attemptCount, long nextAttemptAt, long lastAttemptAt, String error) {
            PendingUploadEntity entity = map.get(messageId);
            if (entity != null) {
                entity.setUploadStatus(status);
                entity.setAttemptCount(attemptCount);
                entity.setNextAttemptAt(nextAttemptAt);
                entity.setLastAttemptAt(lastAttemptAt);
                entity.setLastErrorCode(error);
            }
        }

        @Override
        public synchronized int getPendingUploadCount() {
            int count = 0;
            for (PendingUploadEntity entity : map.values()) {
                if (Constants.UPLOAD_STATUS_PENDING.equals(entity.getUploadStatus())
                        || Constants.UPLOAD_STATUS_RETRY_WAIT.equals(entity.getUploadStatus())
                        || Constants.UPLOAD_STATUS_UPLOADING.equals(entity.getUploadStatus())) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public synchronized void deleteByMessageId(String messageId) {
            map.remove(messageId);
        }

        @Override
        public synchronized void deleteAll() {
            map.clear();
        }
    }

    // In-memory fake DAO for EmergencyMessageEntity
    private static class FakeEmergencyMessageDao implements EmergencyMessageDao {
        private final Map<String, EmergencyMessageEntity> map = new HashMap<>();

        @Override
        public synchronized void insert(EmergencyMessageEntity entity) {
            map.put(entity.getMessageId(), entity);
        }

        @Override
        public synchronized void insertAll(List<EmergencyMessageEntity> entities) {
            for (EmergencyMessageEntity e : entities) map.put(e.getMessageId(), e);
        }

        @Override
        public synchronized void update(EmergencyMessageEntity entity) {
            map.put(entity.getMessageId(), entity);
        }

        @Override
        public synchronized void delete(EmergencyMessageEntity entity) {
            map.remove(entity.getMessageId());
        }

        @Override
        public synchronized void deleteById(String id) {
            map.remove(id);
        }

        @Override
        public synchronized List<EmergencyMessageEntity> getAllMessages() {
            return new ArrayList<>(map.values());
        }

        @Override
        public synchronized EmergencyMessageEntity getMessageById(String id) {
            return map.get(id);
        }

        @Override
        public synchronized List<EmergencyMessageEntity> getMessagesByCategory(String category) {
            List<EmergencyMessageEntity> list = new ArrayList<>();
            for (EmergencyMessageEntity e : map.values()) {
                if (category.equals(e.getEmergencyCategory())) list.add(e);
            }
            return list;
        }

        @Override
        public synchronized int getMessageCount() {
            return map.size();
        }

        @Override
        public synchronized void updateStatus(String id, String status) {
            EmergencyMessageEntity e = map.get(id);
            if (e != null) e.setStatus(status);
        }

        @Override
        public synchronized List<EmergencyMessageEntity> getOutgoingMessages() {
            return new ArrayList<>(map.values());
        }

        @Override
        public synchronized int countById(String id) {
            return map.containsKey(id) ? 1 : 0;
        }

        @Override
        public synchronized void deleteAll() {
            map.clear();
        }
    }

    private static class FakeHttpURLConnection extends HttpURLConnection {
        private final int responseCode;
        private final String responseBody;
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        FakeHttpURLConnection(int responseCode, String responseBody) {
            super(null);
            this.responseCode = responseCode;
            this.responseBody = responseBody;
        }

        @Override public void disconnect() {}
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() {}
        @Override public int getResponseCode() { return responseCode; }
        @Override public OutputStream getOutputStream() { return outputStream; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8)); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8)); }
    }
}
