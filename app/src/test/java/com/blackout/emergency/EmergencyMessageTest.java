package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.utils.Constants;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for EmergencyMessage model, coordinate handling,
 * Phase 2 status assignments, and the 8 emergency categories.
 */
public class EmergencyMessageTest {

    @Test
    public void testMessageCreationWithoutCoordinates() {
        EmergencyMessage message = new EmergencyMessage(
                "SENDER_123",
                "NODE-7F84",
                Constants.CATEGORY_MEDICAL,
                "Cardiac arrest reported. Immediate assistance required.",
                Constants.PRIORITY_CRITICAL,
                null,
                null,
                null,
                null,
                Constants.STATUS_LOCATION_UNAVAILABLE,
                Constants.LOC_STATUS_UNAVAILABLE
        );

        assertNotNull(message.getMessageId());
        assertEquals("SENDER_123", message.getSenderDeviceId());
        assertEquals("NODE-7F84", message.getSenderCallsign());
        assertEquals(Constants.CATEGORY_MEDICAL, message.getEmergencyCategory());
        assertEquals(Constants.PRIORITY_CRITICAL, message.getPriority());
        assertEquals(Constants.STATUS_LOCATION_UNAVAILABLE, message.getStatus());
        assertEquals(Constants.LOC_STATUS_UNAVAILABLE, message.getLocationStatus());
        assertFalse(message.hasCoordinates());
        assertNull(message.getLatitude());
        assertNull(message.getLongitude());
        assertNull(message.getLocationAccuracy());
        assertEquals(0, message.getHopCount());
    }

    @Test
    public void testMessageCreationWithCoordinates() {
        long now = System.currentTimeMillis();
        EmergencyMessage message = new EmergencyMessage(
                "SENDER_456",
                "NODE-BETA",
                Constants.CATEGORY_FIRE,
                "Wildfire approaching sector 3.",
                Constants.PRIORITY_CRITICAL,
                34.0522,
                -118.2437,
                4.5f,
                now,
                Constants.STATUS_LOCAL_SAVED,
                Constants.LOC_STATUS_ACQUIRED
        );

        assertTrue(message.hasCoordinates());
        assertEquals(Double.valueOf(34.0522), message.getLatitude());
        assertEquals(Double.valueOf(-118.2437), message.getLongitude());
        assertEquals(Float.valueOf(4.5f), message.getLocationAccuracy());
        assertEquals(Long.valueOf(now), message.getLocationTimestamp());
        assertEquals(Constants.STATUS_LOCAL_SAVED, message.getStatus());
        assertEquals(Constants.LOC_STATUS_ACQUIRED, message.getLocationStatus());
    }

    @Test
    public void testEightEmergencyCategoriesValidation() {
        List<String> validCategories = Arrays.asList(
                Constants.CATEGORY_MEDICAL,
                Constants.CATEGORY_ROAD_ACCIDENT,
                Constants.CATEGORY_TRAPPED,
                Constants.CATEGORY_FLOOD,
                Constants.CATEGORY_FIRE,
                Constants.CATEGORY_DISASTER,
                Constants.CATEGORY_PERSONAL_SAFETY,
                Constants.CATEGORY_OTHER
        );

        assertEquals(8, validCategories.size());

        for (String category : validCategories) {
            EmergencyMessage msg = new EmergencyMessage();
            msg.setEmergencyCategory(category);
            assertEquals(category, msg.getEmergencyCategory());
            assertEquals(category, msg.getCategory());
        }
    }

    @Test
    public void testMessageExpiryDefaultTtl() {
        EmergencyMessage msg = new EmergencyMessage();
        assertNotNull(msg.getCreatedAt());
        assertNotNull(msg.getExpiresAt());
        long diff = msg.getExpiresAt() - msg.getCreatedAt();
        assertEquals(Constants.PACKET_TTL_MILLIS, diff);
        assertEquals(48 * 60 * 60 * 1000L, diff);
    }

    @Test
    public void testPhase2StatusAssignments() {
        EmergencyMessage savedMsg = new EmergencyMessage();
        savedMsg.setStatus(Constants.STATUS_LOCAL_SAVED);
        assertEquals("LOCAL_SAVED", savedMsg.getStatus());

        EmergencyMessage noLocMsg = new EmergencyMessage();
        noLocMsg.setStatus(Constants.STATUS_LOCATION_UNAVAILABLE);
        assertEquals("LOCATION_UNAVAILABLE", noLocMsg.getStatus());

        EmergencyMessage expiredMsg = new EmergencyMessage();
        expiredMsg.setStatus(Constants.STATUS_EXPIRED);
        assertEquals("EXPIRED", expiredMsg.getStatus());
    }
}
