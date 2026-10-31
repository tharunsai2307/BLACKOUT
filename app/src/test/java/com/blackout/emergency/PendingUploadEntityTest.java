package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.blackout.emergency.database.PendingUploadEntity;
import com.blackout.emergency.utils.Constants;
import java.util.UUID;
import org.junit.Test;

/**
 * Unit tests for PendingUploadEntity.
 */
public class PendingUploadEntityTest {

    @Test
    public void testEntityConstructorAndAccessors() {
        String msgId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        PendingUploadEntity entity = new PendingUploadEntity(
                msgId,
                Constants.UPLOAD_STATUS_PENDING,
                "GATEWAY-TEST-01",
                2,
                now + 10000,
                now,
                0L,
                null,
                "HTTP 500: Internal Server Error"
        );

        assertEquals(msgId, entity.getMessageId());
        assertEquals(Constants.UPLOAD_STATUS_PENDING, entity.getUploadStatus());
        assertEquals("GATEWAY-TEST-01", entity.getGatewayDeviceId());
        assertEquals(2, entity.getAttemptCount());
        assertEquals(now + 10000, entity.getNextAttemptAt());
        assertEquals(now, entity.getLastAttemptAt());
        assertEquals(0L, entity.getUploadedAt());
        assertNull(entity.getBackendReference());
        assertEquals("HTTP 500: Internal Server Error", entity.getLastErrorCode());

        // Test mutators
        entity.setUploadStatus(Constants.UPLOAD_STATUS_BACKEND_ACCEPTED);
        entity.setUploadedAt(now + 15000);
        entity.setBackendReference("REF-UUID-1234");
        entity.setAttemptCount(3);
        entity.setGatewayDeviceId("GATEWAY-NEW");
        entity.setLastErrorCode(null);

        assertEquals(Constants.UPLOAD_STATUS_BACKEND_ACCEPTED, entity.getUploadStatus());
        assertEquals(now + 15000, entity.getUploadedAt());
        assertEquals("REF-UUID-1234", entity.getBackendReference());
        assertEquals(3, entity.getAttemptCount());
        assertEquals("GATEWAY-NEW", entity.getGatewayDeviceId());
        assertNull(entity.getLastErrorCode());
    }

    @Test
    public void testDefaultConstructor() {
        PendingUploadEntity entity = new PendingUploadEntity();
        assertNotNull(entity.getMessageId());
        assertEquals("", entity.getMessageId());
    }
}
