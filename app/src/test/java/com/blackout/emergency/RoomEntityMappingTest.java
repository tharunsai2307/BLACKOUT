package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.blackout.emergency.database.EmergencyMessageEntity;
import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.utils.Constants;
import org.junit.Test;

/**
 * Unit tests verifying bidirectional mapping between EmergencyMessage model
 * and Room EmergencyMessageEntity.
 */
public class RoomEntityMappingTest {

    @Test
    public void testModelToEntityAndBackWithCoordinates() {
        long now = System.currentTimeMillis();
        EmergencyMessage original = new EmergencyMessage(
                "DEV-001",
                "NODE-7F84",
                Constants.CATEGORY_FLOOD,
                "Flash flood warning. Water rising rapidly.",
                Constants.PRIORITY_HIGH,
                12.9716,
                77.5946,
                8.2f,
                now,
                Constants.STATUS_LOCAL_SAVED,
                Constants.LOC_STATUS_ACQUIRED
        );

        // Model -> Entity
        EmergencyMessageEntity entity = EmergencyMessageEntity.fromModel(original);
        assertNotNull(entity);
        assertEquals(original.getMessageId(), entity.getMessageId());
        assertEquals(original.getSenderDeviceId(), entity.getSenderDeviceId());
        assertEquals(original.getSenderCallsign(), entity.getSenderCallsign());
        assertEquals(original.getEmergencyCategory(), entity.getEmergencyCategory());
        assertEquals(original.getDescription(), entity.getDescription());
        assertEquals(original.getPriority(), entity.getPriority());
        assertEquals(original.getLatitude(), entity.getLatitude());
        assertEquals(original.getLongitude(), entity.getLongitude());
        assertEquals(original.getLocationAccuracy(), entity.getLocationAccuracy());
        assertEquals(original.getLocationTimestamp(), entity.getLocationTimestamp());
        assertEquals(original.getStatus(), entity.getStatus());
        assertEquals(original.getLocationStatus(), entity.getLocationStatus());
        assertEquals(original.getCreatedAt(), entity.getCreatedAt());
        assertEquals(original.getExpiresAt(), entity.getExpiresAt());

        // Entity -> Model
        EmergencyMessage reconstructed = entity.toModel();
        assertNotNull(reconstructed);
        assertEquals(original.getMessageId(), reconstructed.getMessageId());
        assertEquals(original.getSenderDeviceId(), reconstructed.getSenderDeviceId());
        assertEquals(original.getSenderCallsign(), reconstructed.getSenderCallsign());
        assertEquals(original.getEmergencyCategory(), reconstructed.getEmergencyCategory());
        assertEquals(original.getDescription(), reconstructed.getDescription());
        assertEquals(original.getPriority(), reconstructed.getPriority());
        assertEquals(original.getLatitude(), reconstructed.getLatitude());
        assertEquals(original.getLongitude(), reconstructed.getLongitude());
        assertEquals(original.getLocationAccuracy(), reconstructed.getLocationAccuracy());
        assertEquals(original.getLocationTimestamp(), reconstructed.getLocationTimestamp());
        assertEquals(original.getStatus(), reconstructed.getStatus());
        assertEquals(original.getLocationStatus(), reconstructed.getLocationStatus());
        assertEquals(original.getCreatedAt(), reconstructed.getCreatedAt());
        assertEquals(original.getExpiresAt(), reconstructed.getExpiresAt());
    }

    @Test
    public void testModelToEntityAndBackWithoutCoordinates() {
        EmergencyMessage original = new EmergencyMessage(
                "DEV-002",
                "NODE-7F84",
                Constants.CATEGORY_PERSONAL_SAFETY,
                "Individual stranded in remote sector.",
                Constants.PRIORITY_HIGH,
                null,
                null,
                null,
                null,
                Constants.STATUS_LOCATION_UNAVAILABLE,
                Constants.LOC_STATUS_UNAVAILABLE
        );

        EmergencyMessageEntity entity = EmergencyMessageEntity.fromModel(original);
        assertNotNull(entity);
        assertNull(entity.getLatitude());
        assertNull(entity.getLongitude());
        assertNull(entity.getLocationAccuracy());
        assertEquals(Constants.STATUS_LOCATION_UNAVAILABLE, entity.getStatus());

        EmergencyMessage reconstructed = entity.toModel();
        assertNotNull(reconstructed);
        assertNull(reconstructed.getLatitude());
        assertNull(reconstructed.getLongitude());
        assertNull(reconstructed.getLocationAccuracy());
        assertEquals(Constants.STATUS_LOCATION_UNAVAILABLE, reconstructed.getStatus());
    }

    @Test
    public void testNullModelReturnsNullEntity() {
        assertNull(EmergencyMessageEntity.fromModel(null));
    }
}
