package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.nearby.SosPayloadSerializer;
import com.blackout.emergency.utils.Constants;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Unit tests verifying JSON byte payload serialization, deserialization,
 * strict schema validation, security limits, and error handling for Phase 3.
 */
public class SosPayloadSerializerTest {

    @Test
    public void testSerializeAndDeserializeCompleteMessageWithLocation() throws Exception {
        long now = System.currentTimeMillis();
        EmergencyMessage original = new EmergencyMessage(
                "DEV-PHONE-A",
                "NODE-7F84",
                Constants.CATEGORY_MEDICAL,
                "Elderly civilian suffering from heatstroke. Requires immediate IV fluids and shade.",
                Constants.PRIORITY_CRITICAL,
                28.6139,
                77.2090,
                3.5f,
                now,
                Constants.STATUS_LOCAL_SAVED,
                Constants.LOC_STATUS_ACQUIRED
        );

        byte[] payloadBytes = SosPayloadSerializer.serialize(original);
        assertNotNull(payloadBytes);
        assertTrue(payloadBytes.length > 0);

        EmergencyMessage reconstructed = SosPayloadSerializer.deserialize(payloadBytes);
        assertNotNull(reconstructed);
        assertEquals(original.getMessageId(), reconstructed.getMessageId());
        assertEquals("DEV-PHONE-A", reconstructed.getSenderDeviceId());
        assertEquals("NODE-7F84", reconstructed.getSenderCallsign());
        assertEquals(Constants.CATEGORY_MEDICAL, reconstructed.getEmergencyCategory());
        assertEquals(original.getDescription(), reconstructed.getDescription());
        assertEquals(Constants.PRIORITY_CRITICAL, reconstructed.getPriority());
        assertTrue(reconstructed.hasCoordinates());
        assertEquals(Double.valueOf(28.6139), reconstructed.getLatitude());
        assertEquals(Double.valueOf(77.2090), reconstructed.getLongitude());
        assertEquals(Float.valueOf(3.5f), reconstructed.getLocationAccuracy());
        assertEquals(Long.valueOf(now), reconstructed.getLocationTimestamp());
        assertEquals(Constants.LOC_STATUS_ACQUIRED, reconstructed.getLocationStatus());
        assertEquals(Constants.STATUS_RECEIVED, reconstructed.getStatus());
    }

    @Test
    public void testSerializeAndDeserializeWithoutLocation() throws Exception {
        EmergencyMessage original = new EmergencyMessage(
                "DEV-PHONE-B",
                "NODE-B234",
                Constants.CATEGORY_TRAPPED,
                "Flooded basement with blocked exit. 2 people stranded.",
                Constants.PRIORITY_HIGH,
                null,
                null,
                null,
                null,
                Constants.STATUS_LOCATION_UNAVAILABLE,
                Constants.LOC_STATUS_UNAVAILABLE
        );

        byte[] payloadBytes = SosPayloadSerializer.serialize(original);
        EmergencyMessage reconstructed = SosPayloadSerializer.deserialize(payloadBytes);

        assertNotNull(reconstructed);
        assertFalse(reconstructed.hasCoordinates());
        assertNull(reconstructed.getLatitude());
        assertNull(reconstructed.getLongitude());
        assertNull(reconstructed.getLocationAccuracy());
        assertEquals(Constants.LOC_STATUS_UNAVAILABLE, reconstructed.getLocationStatus());
        assertEquals(Constants.STATUS_RECEIVED, reconstructed.getStatus());
    }

    @Test
    public void testRejectEmptyOrNullPayload() {
        try {
            SosPayloadSerializer.deserialize(null);
            fail("Expected PayloadValidationException for null payload");
        } catch (SosPayloadSerializer.PayloadValidationException expected) {
            assertTrue(expected.getMessage().contains("empty or null"));
        }

        try {
            SosPayloadSerializer.deserialize(new byte[0]);
            fail("Expected PayloadValidationException for empty payload");
        } catch (SosPayloadSerializer.PayloadValidationException expected) {
            assertTrue(expected.getMessage().contains("empty or null"));
        }
    }

    @Test
    public void testRejectPayloadExceedingSizeLimit() {
        byte[] oversized = new byte[Constants.MAX_PAYLOAD_SIZE_BYTES + 10];
        try {
            SosPayloadSerializer.deserialize(oversized);
            fail("Expected PayloadValidationException for oversized payload");
        } catch (SosPayloadSerializer.PayloadValidationException expected) {
            assertTrue(expected.getMessage().contains("exceeds maximum permitted size"));
        }
    }

    @Test
    public void testRejectUnsupportedProtocolVersion() throws Exception {
        JSONObject json = createValidSosJson();
        json.put("protocolVersion", 99); // Unsupported version

        try {
            SosPayloadSerializer.deserialize(json.toString().getBytes(StandardCharsets.UTF_8));
            fail("Expected PayloadValidationException for unsupported protocol version");
        } catch (SosPayloadSerializer.PayloadValidationException expected) {
            assertTrue(expected.getMessage().contains("Unsupported protocol version"));
        }
    }

    @Test
    public void testRejectInvalidMessageType() throws Exception {
        JSONObject json = createValidSosJson();
        json.put("messageType", "PING"); // Non-SOS type

        try {
            SosPayloadSerializer.deserialize(json.toString().getBytes(StandardCharsets.UTF_8));
            fail("Expected PayloadValidationException for invalid message type");
        } catch (SosPayloadSerializer.PayloadValidationException expected) {
            assertTrue(expected.getMessage().contains("Invalid message type"));
        }
    }

    @Test
    public void testRejectInvalidUuidMessageId() throws Exception {
        JSONObject json = createValidSosJson();
        json.put("messageId", "not-a-valid-uuid-1234");

        try {
            SosPayloadSerializer.deserialize(json.toString().getBytes(StandardCharsets.UTF_8));
            fail("Expected PayloadValidationException for malformed UUID");
        } catch (SosPayloadSerializer.PayloadValidationException expected) {
            assertTrue(expected.getMessage().contains("Invalid UUID"));
        }
    }

    @Test
    public void testRejectUnsupportedCategory() throws Exception {
        JSONObject json = createValidSosJson();
        json.put("category", "INVALID_CUSTOM_CATEGORY");

        try {
            SosPayloadSerializer.deserialize(json.toString().getBytes(StandardCharsets.UTF_8));
            fail("Expected PayloadValidationException for invalid category");
        } catch (SosPayloadSerializer.PayloadValidationException expected) {
            assertTrue(expected.getMessage().contains("Unsupported or invalid emergency category"));
        }
    }

    @Test
    public void testRejectEmptyDescription() throws Exception {
        JSONObject json = createValidSosJson();
        json.put("description", "   ");

        try {
            SosPayloadSerializer.deserialize(json.toString().getBytes(StandardCharsets.UTF_8));
            fail("Expected PayloadValidationException for empty description");
        } catch (SosPayloadSerializer.PayloadValidationException expected) {
            assertTrue(expected.getMessage().contains("cannot be empty"));
        }
    }

    @Test
    public void testRejectOutOfRangeCoordinates() throws Exception {
        JSONObject json = createValidSosJson();
        json.put("latitude", 120.0); // Out of range [-90, 90]

        try {
            SosPayloadSerializer.deserialize(json.toString().getBytes(StandardCharsets.UTF_8));
            fail("Expected PayloadValidationException for latitude out of range");
        } catch (SosPayloadSerializer.PayloadValidationException expected) {
            assertTrue(expected.getMessage().contains("Latitude out of range"));
        }
    }

    private JSONObject createValidSosJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put("protocolVersion", 1);
        json.put("messageType", "SOS");
        json.put("messageId", UUID.randomUUID().toString());
        json.put("senderDeviceId", "DEV-TEST");
        json.put("senderCallsign", "NODE-TEST");
        json.put("category", Constants.CATEGORY_FIRE);
        json.put("description", "Kitchen fire spreading to roof.");
        json.put("priority", Constants.PRIORITY_CRITICAL);
        json.put("latitude", 34.0522);
        json.put("longitude", -118.2437);
        json.put("accuracyMeters", 5.0);
        json.put("locationTimestamp", System.currentTimeMillis());
        json.put("locationStatus", Constants.LOC_STATUS_ACQUIRED);
        json.put("createdAt", System.currentTimeMillis());
        json.put("expiresAt", System.currentTimeMillis() + Constants.PACKET_TTL_MILLIS);
        json.put("hopCount", 0);
        return json;
    }
}
