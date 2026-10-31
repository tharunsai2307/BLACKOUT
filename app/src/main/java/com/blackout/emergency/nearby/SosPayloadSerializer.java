package com.blackout.emergency.nearby;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.relay.RelayPolicy;
import com.blackout.emergency.utils.Constants;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Serializes and deserializes EmergencyMessage objects to/from JSON byte payloads
 * for direct peer-to-peer transmission via Nearby Connections API.
 * Enforces strict protocol validation, payload size limits, hop limits, and sanitization.
 */
public class SosPayloadSerializer {

    private static final Set<String> VALID_CATEGORIES = new HashSet<>(Arrays.asList(
            Constants.CATEGORY_MEDICAL,
            Constants.CATEGORY_ROAD_ACCIDENT,
            Constants.CATEGORY_TRAPPED,
            Constants.CATEGORY_FLOOD,
            Constants.CATEGORY_FIRE,
            Constants.CATEGORY_DISASTER,
            Constants.CATEGORY_PERSONAL_SAFETY,
            Constants.CATEGORY_OTHER
    ));

    private static final Set<String> VALID_PRIORITIES = new HashSet<>(Arrays.asList(
            Constants.PRIORITY_CRITICAL,
            Constants.PRIORITY_HIGH,
            Constants.PRIORITY_MODERATE,
            Constants.PRIORITY_LOW
    ));

    public static class PayloadValidationException extends Exception {
        public PayloadValidationException(String message) {
            super(message);
        }

        public PayloadValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Serializes an EmergencyMessage into a standardized UTF-8 JSON byte payload.
     */
    @NonNull
    public static byte[] serialize(@NonNull EmergencyMessage message) throws PayloadValidationException {
        return serialize(message, null);
    }

    /**
     * Serializes an EmergencyMessage with relay forwarding metadata.
     * @param message The original emergency message
     * @param currentRelayDeviceId The ID of the current device forwarding this message (optional)
     */
    @NonNull
    public static byte[] serialize(@NonNull EmergencyMessage message, @Nullable String currentRelayDeviceId) throws PayloadValidationException {
        try {
            JSONObject json = new JSONObject();
            json.put("protocolVersion", Constants.PROTOCOL_VERSION);
            json.put("messageType", Constants.MESSAGE_TYPE_SOS);
            json.put("messageId", message.getMessageId());
            json.put("senderDeviceId", message.getSenderDeviceId() != null ? message.getSenderDeviceId() : "UNKNOWN_DEV");
            json.put("originalSenderDeviceId", message.getSenderDeviceId() != null ? message.getSenderDeviceId() : "UNKNOWN_DEV");
            json.put("currentSenderDeviceId", currentRelayDeviceId != null ? currentRelayDeviceId : message.getSenderDeviceId());
            json.put("senderCallsign", message.getSenderCallsign() != null ? message.getSenderCallsign() : "UNKNOWN_NODE");
            json.put("category", message.getEmergencyCategory());
            json.put("description", message.getDescription());
            json.put("priority", message.getPriority());

            if (message.getLatitude() != null) {
                json.put("latitude", message.getLatitude().doubleValue());
            } else {
                json.put("latitude", JSONObject.NULL);
            }

            if (message.getLongitude() != null) {
                json.put("longitude", message.getLongitude().doubleValue());
            } else {
                json.put("longitude", JSONObject.NULL);
            }

            if (message.getLocationAccuracy() != null) {
                json.put("accuracyMeters", message.getLocationAccuracy().doubleValue());
            } else {
                json.put("accuracyMeters", JSONObject.NULL);
            }

            if (message.getLocationTimestamp() != null) {
                json.put("locationTimestamp", message.getLocationTimestamp().longValue());
            } else {
                json.put("locationTimestamp", JSONObject.NULL);
            }

            json.put("locationStatus", message.getLocationStatus() != null ? message.getLocationStatus() : Constants.LOC_STATUS_UNAVAILABLE);
            json.put("createdAt", message.getCreatedAt());
            json.put("expiresAt", message.getExpiresAt());
            json.put("hopCount", message.getHopCount());
            json.put("maximumHopCount", RelayPolicy.DEFAULT_MAX_HOPS);
            json.put("lastForwardedAt", System.currentTimeMillis());

            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > Constants.MAX_PAYLOAD_SIZE_BYTES) {
                throw new PayloadValidationException("Serialized payload exceeds maximum size of " + Constants.MAX_PAYLOAD_SIZE_BYTES + " bytes");
            }
            return bytes;
        } catch (JSONException e) {
            throw new PayloadValidationException("Failed to serialize EmergencyMessage to JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Deserializes and rigorously validates an incoming JSON byte payload into an EmergencyMessage.
     */
    @NonNull
    public static EmergencyMessage deserialize(@Nullable byte[] payloadBytes) throws PayloadValidationException {
        if (payloadBytes == null || payloadBytes.length == 0) {
            throw new PayloadValidationException("Payload is empty or null");
        }

        if (payloadBytes.length > Constants.MAX_PAYLOAD_SIZE_BYTES) {
            throw new PayloadValidationException("Payload exceeds maximum permitted size of " + Constants.MAX_PAYLOAD_SIZE_BYTES + " bytes");
        }

        String jsonString = new String(payloadBytes, StandardCharsets.UTF_8);
        try {
            JSONObject json = new JSONObject(jsonString);

            // 1. Protocol Version Check
            int protocolVersion = json.optInt("protocolVersion", -1);
            if (protocolVersion != Constants.PROTOCOL_VERSION) {
                throw new PayloadValidationException("Unsupported protocol version: " + protocolVersion);
            }

            // 2. Message Type Check
            String messageType = json.optString("messageType", "");
            if (!Constants.MESSAGE_TYPE_SOS.equalsIgnoreCase(messageType)) {
                throw new PayloadValidationException("Invalid message type: " + messageType);
            }

            // 3. Message ID Validation (Must be valid UUID)
            String messageId = json.optString("messageId", "");
            if (messageId.trim().isEmpty()) {
                throw new PayloadValidationException("Missing messageId in payload");
            }
            try {
                UUID.fromString(messageId);
            } catch (IllegalArgumentException iae) {
                throw new PayloadValidationException("Invalid UUID format for messageId: " + messageId);
            }

            // 4. Sender Identifiers (originalSenderDeviceId is prioritized if available)
            String senderDeviceId = json.optString("originalSenderDeviceId", "");
            if (senderDeviceId.isEmpty()) {
                senderDeviceId = json.optString("senderDeviceId", "UNKNOWN_DEV");
            }
            if (senderDeviceId.length() > 64) {
                senderDeviceId = senderDeviceId.substring(0, 64);
            }

            String senderCallsign = json.optString("senderCallsign", "UNKNOWN_NODE");
            if (senderCallsign.length() > 32) {
                senderCallsign = senderCallsign.substring(0, 32);
            }

            // 5. Emergency Category Validation
            String category = json.optString("category", "");
            if (!VALID_CATEGORIES.contains(category)) {
                throw new PayloadValidationException("Unsupported or invalid emergency category: " + category);
            }

            // 6. Description Validation & Sanitization
            String description = json.optString("description", "");
            if (description.trim().isEmpty()) {
                throw new PayloadValidationException("Emergency description cannot be empty");
            }
            if (description.length() > Constants.MAX_DESCRIPTION_LENGTH) {
                description = description.substring(0, Constants.MAX_DESCRIPTION_LENGTH);
            }

            // 7. Priority Validation
            String priority = json.optString("priority", Constants.PRIORITY_MODERATE);
            if (!VALID_PRIORITIES.contains(priority)) {
                priority = Constants.PRIORITY_MODERATE;
            }

            // 8. Coordinates & Location Validation
            Double latitude = null;
            if (json.has("latitude") && !json.isNull("latitude")) {
                double latVal = json.getDouble("latitude");
                if (latVal >= -90.0 && latVal <= 90.0) {
                    latitude = latVal;
                } else {
                    throw new PayloadValidationException("Latitude out of range [-90, 90]: " + latVal);
                }
            }

            Double longitude = null;
            if (json.has("longitude") && !json.isNull("longitude")) {
                double lonVal = json.getDouble("longitude");
                if (lonVal >= -180.0 && lonVal <= 180.0) {
                    longitude = lonVal;
                } else {
                    throw new PayloadValidationException("Longitude out of range [-180, 180]: " + lonVal);
                }
            }

            Float accuracy = null;
            if (json.has("accuracyMeters") && !json.isNull("accuracyMeters")) {
                float accVal = (float) json.getDouble("accuracyMeters");
                if (accVal >= 0f) {
                    accuracy = accVal;
                }
            }

            Long locationTimestamp = null;
            if (json.has("locationTimestamp") && !json.isNull("locationTimestamp")) {
                long locTs = json.getLong("locationTimestamp");
                if (locTs > 0) {
                    locationTimestamp = locTs;
                }
            }

            String locationStatus = json.optString("locationStatus",
                    latitude != null ? Constants.LOC_STATUS_ACQUIRED : Constants.LOC_STATUS_UNAVAILABLE);

            // 9. Timestamps Validation
            long createdAt = json.optLong("createdAt", 0L);
            if (createdAt <= 0) {
                createdAt = System.currentTimeMillis();
            }

            long expiresAt = json.optLong("expiresAt", 0L);
            if (expiresAt <= 0 || expiresAt < createdAt) {
                expiresAt = createdAt + Constants.PACKET_TTL_MILLIS;
            }

            // Check if message is already expired
            if (expiresAt <= System.currentTimeMillis()) {
                throw new PayloadValidationException("Emergency message has expired (expiresAt: " + expiresAt + ")");
            }

            int hopCount = json.optInt("hopCount", 0);
            if (hopCount < 0) {
                hopCount = 0;
            }

            // Enforce maximum hop limit
            if (hopCount >= RelayPolicy.DEFAULT_MAX_HOPS) {
                throw new PayloadValidationException("Message has reached or exceeded maximum hop count: " + hopCount);
            }

            // Build reconstructed model
            EmergencyMessage message = new EmergencyMessage(
                    senderDeviceId,
                    senderCallsign,
                    category,
                    description,
                    priority,
                    latitude,
                    longitude,
                    accuracy,
                    locationTimestamp,
                    Constants.STATUS_RECEIVED,
                    locationStatus
            );
            message.setMessageId(messageId);
            message.setCreatedAt(createdAt);
            message.setExpiresAt(expiresAt);
            message.setHopCount(hopCount);

            return message;
        } catch (JSONException e) {
            throw new PayloadValidationException("Malformed JSON payload: " + e.getMessage(), e);
        }
    }
}
