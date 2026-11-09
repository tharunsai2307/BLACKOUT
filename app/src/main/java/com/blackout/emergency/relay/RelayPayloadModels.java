package com.blackout.emergency.relay;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.blackout.emergency.utils.Constants;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Protocol models and binary JSON serializers/deserializers for Phase 4 messages:
 * - MESSAGE_INVENTORY (list of known message IDs held by a peer)
 * - DELIVERY_ACK (application-level acknowledgement after persistence)
 * - DELIVERY_REJECTED (explicit rejection with reason code)
 */
public final class RelayPayloadModels {

    private RelayPayloadModels() {
        // Prevent instantiation
    }

    // =========================================================================
    // 1. Message Inventory Payload
    // =========================================================================

    public static class InventoryPayload {
        private final int protocolVersion;
        private final String senderDeviceId;
        private final List<String> knownMessageIds;

        public InventoryPayload(String senderDeviceId, List<String> knownMessageIds) {
            this.protocolVersion = Constants.PROTOCOL_VERSION;
            this.senderDeviceId = senderDeviceId;
            this.knownMessageIds = knownMessageIds != null ? knownMessageIds : new ArrayList<>();
        }

        public int getProtocolVersion() {
            return protocolVersion;
        }

        public String getSenderDeviceId() {
            return senderDeviceId;
        }

        public List<String> getKnownMessageIds() {
            return knownMessageIds;
        }

        @NonNull
        public byte[] toBytes() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("protocolVersion", protocolVersion);
            json.put("messageType", Constants.MESSAGE_TYPE_INVENTORY);
            json.put("senderDeviceId", senderDeviceId);

            JSONArray arr = new JSONArray();
            int count = Math.min(knownMessageIds.size(), RelayPolicy.MAX_INVENTORY_SIZE);
            for (int i = 0; i < count; i++) {
                arr.put(knownMessageIds.get(i));
            }
            json.put("knownMessageIds", arr);
            return json.toString().getBytes(StandardCharsets.UTF_8);
        }

        @NonNull
        public static InventoryPayload fromBytes(@Nullable byte[] bytes) throws JSONException, IllegalArgumentException {
            if (bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("Inventory payload is empty");
            }
            String str = new String(bytes, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(str);

            int version = json.optInt("protocolVersion", -1);
            if (version != Constants.PROTOCOL_VERSION) {
                throw new IllegalArgumentException("Unsupported protocol version in inventory: " + version);
            }

            String type = json.optString("messageType", "");
            if (!Constants.MESSAGE_TYPE_INVENTORY.equals(type)) {
                throw new IllegalArgumentException("Invalid messageType for inventory: " + type);
            }

            String senderDev = json.optString("senderDeviceId", "UNKNOWN_DEV");
            JSONArray arr = json.optJSONArray("knownMessageIds");
            List<String> ids = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    ids.add(arr.optString(i));
                }
            }
            return new InventoryPayload(senderDev, ids);
        }
    }

    // =========================================================================
    // 2. Delivery Acknowledgement Payload
    // =========================================================================

    public static class DeliveryAckPayload {
        private final int protocolVersion;
        private final String messageId;
        private final String receiverDeviceId;
        private final long acknowledgedAt;
        private final int hopNumber;

        public DeliveryAckPayload(String messageId, String receiverDeviceId, int hopNumber) {
            this(Constants.PROTOCOL_VERSION, messageId, receiverDeviceId, System.currentTimeMillis(), hopNumber);
        }

        public DeliveryAckPayload(int protocolVersion, String messageId, String receiverDeviceId, long acknowledgedAt, int hopNumber) {
            this.protocolVersion = protocolVersion;
            this.messageId = messageId;
            this.receiverDeviceId = receiverDeviceId;
            this.acknowledgedAt = acknowledgedAt;
            this.hopNumber = hopNumber;
        }

        public int getProtocolVersion() {
            return protocolVersion;
        }

        public String getMessageId() {
            return messageId;
        }

        public String getReceiverDeviceId() {
            return receiverDeviceId;
        }

        public long getAcknowledgedAt() {
            return acknowledgedAt;
        }

        public int getHopNumber() {
            return hopNumber;
        }

        @NonNull
        public byte[] toBytes() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("protocolVersion", protocolVersion);
            json.put("messageType", Constants.MESSAGE_TYPE_DELIVERY_ACK);
            json.put("messageId", messageId);
            json.put("receiverDeviceId", receiverDeviceId);
            json.put("acknowledgedAt", acknowledgedAt);
            json.put("hopNumber", hopNumber);
            return json.toString().getBytes(StandardCharsets.UTF_8);
        }

        @NonNull
        public static DeliveryAckPayload fromBytes(@Nullable byte[] bytes) throws JSONException, IllegalArgumentException {
            if (bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("ACK payload is empty");
            }
            String str = new String(bytes, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(str);

            int version = json.optInt("protocolVersion", -1);
            if (version != Constants.PROTOCOL_VERSION) {
                throw new IllegalArgumentException("Unsupported protocol version in ACK: " + version);
            }

            String type = json.optString("messageType", "");
            if (!Constants.MESSAGE_TYPE_DELIVERY_ACK.equals(type)) {
                throw new IllegalArgumentException("Invalid messageType for ACK: " + type);
            }

            String msgId = json.optString("messageId", "");
            if (msgId.isEmpty()) {
                throw new IllegalArgumentException("Missing messageId in ACK");
            }

            String receiverDev = json.optString("receiverDeviceId", "UNKNOWN_DEV");
            long ackAt = json.optLong("acknowledgedAt", System.currentTimeMillis());
            int hops = json.optInt("hopNumber", 0);

            return new DeliveryAckPayload(version, msgId, receiverDev, ackAt, hops);
        }
    }

    // =========================================================================
    // 3. Delivery Rejection Payload
    // =========================================================================

    public static class DeliveryRejectPayload {
        private final int protocolVersion;
        private final String messageId;
        private final String receiverDeviceId;
        private final String reason;

        public DeliveryRejectPayload(String messageId, String receiverDeviceId, String reason) {
            this.protocolVersion = Constants.PROTOCOL_VERSION;
            this.messageId = messageId;
            this.receiverDeviceId = receiverDeviceId;
            this.reason = reason;
        }

        public int getProtocolVersion() {
            return protocolVersion;
        }

        public String getMessageId() {
            return messageId;
        }

        public String getReceiverDeviceId() {
            return receiverDeviceId;
        }

        public String getReason() {
            return reason;
        }

        @NonNull
        public byte[] toBytes() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("protocolVersion", protocolVersion);
            json.put("messageType", Constants.MESSAGE_TYPE_DELIVERY_REJECTED);
            json.put("messageId", messageId);
            json.put("receiverDeviceId", receiverDeviceId);
            json.put("reason", reason);
            return json.toString().getBytes(StandardCharsets.UTF_8);
        }

        @NonNull
        public static DeliveryRejectPayload fromBytes(@Nullable byte[] bytes) throws JSONException, IllegalArgumentException {
            if (bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("Reject payload is empty");
            }
            String str = new String(bytes, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(str);

            int version = json.optInt("protocolVersion", -1);
            if (version != Constants.PROTOCOL_VERSION) {
                throw new IllegalArgumentException("Unsupported protocol version in reject: " + version);
            }

            String type = json.optString("messageType", "");
            if (!Constants.MESSAGE_TYPE_DELIVERY_REJECTED.equals(type)) {
                throw new IllegalArgumentException("Invalid messageType for reject: " + type);
            }

            String msgId = json.optString("messageId", "");
            String receiverDev = json.optString("receiverDeviceId", "UNKNOWN_DEV");
            String rsn = json.optString("reason", "UNKNOWN_ERROR");

            return new DeliveryRejectPayload(msgId, receiverDev, rsn);
        }
    }

    // =========================================================================
    // 4. Gateway Capability Advertisement Payload (Phase 12 Autonomous Routing)
    // =========================================================================

    public static class GatewayCapabilityPayload {
        private final int protocolVersion;
        private final String senderDeviceId;
        private final String senderCallsign;
        private final boolean isInternetAvailable;
        private final String networkType;
        private final int batteryPct;
        private final int pendingUploadCount;
        private final long timestamp;

        public GatewayCapabilityPayload(String senderDeviceId, String senderCallsign,
                                       boolean isInternetAvailable, String networkType,
                                       int batteryPct, int pendingUploadCount) {
            this(Constants.PROTOCOL_VERSION, senderDeviceId, senderCallsign, isInternetAvailable,
                    networkType, batteryPct, pendingUploadCount, System.currentTimeMillis());
        }

        public GatewayCapabilityPayload(int protocolVersion, String senderDeviceId, String senderCallsign,
                                       boolean isInternetAvailable, String networkType,
                                       int batteryPct, int pendingUploadCount, long timestamp) {
            this.protocolVersion = protocolVersion;
            this.senderDeviceId = senderDeviceId;
            this.senderCallsign = senderCallsign != null ? senderCallsign : "NODE";
            this.isInternetAvailable = isInternetAvailable;
            this.networkType = networkType != null ? networkType : "None";
            this.batteryPct = batteryPct;
            this.pendingUploadCount = pendingUploadCount;
            this.timestamp = timestamp;
        }

        public int getProtocolVersion() { return protocolVersion; }
        public String getSenderDeviceId() { return senderDeviceId; }
        public String getSenderCallsign() { return senderCallsign; }
        public boolean isInternetAvailable() { return isInternetAvailable; }
        public String getNetworkType() { return networkType; }
        public int getBatteryPct() { return batteryPct; }
        public int getPendingUploadCount() { return pendingUploadCount; }
        public long getTimestamp() { return timestamp; }

        @NonNull
        public byte[] toBytes() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("protocolVersion", protocolVersion);
            json.put("messageType", Constants.MESSAGE_TYPE_GATEWAY_CAPABILITY);
            json.put("senderDeviceId", senderDeviceId);
            json.put("senderCallsign", senderCallsign);
            json.put("isInternetAvailable", isInternetAvailable);
            json.put("networkType", networkType);
            json.put("batteryPct", batteryPct);
            json.put("pendingUploadCount", pendingUploadCount);
            json.put("timestamp", timestamp);
            return json.toString().getBytes(StandardCharsets.UTF_8);
        }

        @NonNull
        public static GatewayCapabilityPayload fromBytes(@Nullable byte[] bytes) throws JSONException, IllegalArgumentException {
            if (bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("Gateway capability payload is empty");
            }
            String str = new String(bytes, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(str);

            int version = json.optInt("protocolVersion", 1);
            String type = json.optString("messageType", "");
            if (!Constants.MESSAGE_TYPE_GATEWAY_CAPABILITY.equals(type)) {
                throw new IllegalArgumentException("Invalid messageType for gateway capability: " + type);
            }

            return new GatewayCapabilityPayload(
                    version,
                    json.optString("senderDeviceId", "UNKNOWN_DEV"),
                    json.optString("senderCallsign", "NODE"),
                    json.optBoolean("isInternetAvailable", false),
                    json.optString("networkType", "None"),
                    json.optInt("batteryPct", 100),
                    json.optInt("pendingUploadCount", 0),
                    json.optLong("timestamp", System.currentTimeMillis())
            );
        }
    }

    // =========================================================================
    // 5. Peer Cryptographic Handshake Payload (Noise Protocol-Inspired Session)
    // =========================================================================

    public static class PeerHandshakePayload {
        private final int protocolVersion;
        private final String senderDeviceId;
        private final String senderCallsign;
        private final String publicKeyBase64;
        private final String nonce;
        private final long timestamp;
        private final List<String> supportedFeatures;

        public PeerHandshakePayload(String senderDeviceId, String senderCallsign,
                                    String publicKeyBase64, String nonce, List<String> supportedFeatures) {
            this(Constants.PROTOCOL_VERSION, senderDeviceId, senderCallsign, publicKeyBase64,
                    nonce, System.currentTimeMillis(), supportedFeatures);
        }

        public PeerHandshakePayload(int protocolVersion, String senderDeviceId, String senderCallsign,
                                    String publicKeyBase64, String nonce, long timestamp,
                                    List<String> supportedFeatures) {
            this.protocolVersion = protocolVersion;
            this.senderDeviceId = senderDeviceId;
            this.senderCallsign = senderCallsign;
            this.publicKeyBase64 = publicKeyBase64;
            this.nonce = nonce;
            this.timestamp = timestamp;
            this.supportedFeatures = supportedFeatures != null ? supportedFeatures : new ArrayList<>();
        }

        public int getProtocolVersion() { return protocolVersion; }
        public String getSenderDeviceId() { return senderDeviceId; }
        public String getSenderCallsign() { return senderCallsign; }
        public String getPublicKeyBase64() { return publicKeyBase64; }
        public String getNonce() { return nonce; }
        public long getTimestamp() { return timestamp; }
        public List<String> getSupportedFeatures() { return supportedFeatures; }

        @NonNull
        public byte[] toBytes() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("protocolVersion", protocolVersion);
            json.put("messageType", Constants.MESSAGE_TYPE_PEER_HANDSHAKE);
            json.put("senderDeviceId", senderDeviceId);
            json.put("senderCallsign", senderCallsign);
            json.put("publicKeyBase64", publicKeyBase64);
            json.put("nonce", nonce);
            json.put("timestamp", timestamp);

            JSONArray features = new JSONArray();
            for (String f : supportedFeatures) {
                features.put(f);
            }
            json.put("supportedFeatures", features);
            return json.toString().getBytes(StandardCharsets.UTF_8);
        }

        @NonNull
        public static PeerHandshakePayload fromBytes(@Nullable byte[] bytes) throws JSONException, IllegalArgumentException {
            if (bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("Handshake payload is empty");
            }
            String str = new String(bytes, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(str);

            int version = json.optInt("protocolVersion", 1);
            String type = json.optString("messageType", "");
            if (!Constants.MESSAGE_TYPE_PEER_HANDSHAKE.equals(type)) {
                throw new IllegalArgumentException("Invalid messageType for handshake: " + type);
            }

            List<String> feats = new ArrayList<>();
            JSONArray arr = json.optJSONArray("supportedFeatures");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    feats.add(arr.optString(i));
                }
            }

            return new PeerHandshakePayload(
                    version,
                    json.optString("senderDeviceId", "UNKNOWN_DEV"),
                    json.optString("senderCallsign", "NODE"),
                    json.optString("publicKeyBase64", ""),
                    json.optString("nonce", ""),
                    json.optLong("timestamp", System.currentTimeMillis()),
                    feats
            );
        }
    }

    // =========================================================================
    // 6. Peer Handshake Acknowledgement Payload
    // =========================================================================

    public static class PeerHandshakeAckPayload {
        private final int protocolVersion;
        private final String senderDeviceId;
        private final String nonce;
        private final boolean accepted;

        public PeerHandshakeAckPayload(String senderDeviceId, String nonce, boolean accepted) {
            this(Constants.PROTOCOL_VERSION, senderDeviceId, nonce, accepted);
        }

        public PeerHandshakeAckPayload(int protocolVersion, String senderDeviceId, String nonce, boolean accepted) {
            this.protocolVersion = protocolVersion;
            this.senderDeviceId = senderDeviceId;
            this.nonce = nonce;
            this.accepted = accepted;
        }

        public int getProtocolVersion() { return protocolVersion; }
        public String getSenderDeviceId() { return senderDeviceId; }
        public String getNonce() { return nonce; }
        public boolean isAccepted() { return accepted; }

        @NonNull
        public byte[] toBytes() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("protocolVersion", protocolVersion);
            json.put("messageType", Constants.MESSAGE_TYPE_PEER_HANDSHAKE_ACK);
            json.put("senderDeviceId", senderDeviceId);
            json.put("nonce", nonce);
            json.put("accepted", accepted);
            return json.toString().getBytes(StandardCharsets.UTF_8);
        }

        @NonNull
        public static PeerHandshakeAckPayload fromBytes(@Nullable byte[] bytes) throws JSONException, IllegalArgumentException {
            if (bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("Handshake ACK payload is empty");
            }
            String str = new String(bytes, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(str);

            int version = json.optInt("protocolVersion", 1);
            String type = json.optString("messageType", "");
            if (!Constants.MESSAGE_TYPE_PEER_HANDSHAKE_ACK.equals(type)) {
                throw new IllegalArgumentException("Invalid messageType for handshake ACK: " + type);
            }

            return new PeerHandshakeAckPayload(
                    version,
                    json.optString("senderDeviceId", "UNKNOWN_DEV"),
                    json.optString("nonce", ""),
                    json.optBoolean("accepted", true)
            );
        }
    }

    // =========================================================================
    // 7. Explicit Transfer Request Payload
    // =========================================================================

    public static class TransferRequestPayload {
        private final int protocolVersion;
        private final String senderDeviceId;
        private final List<String> requestedMessageIds;

        public TransferRequestPayload(String senderDeviceId, List<String> requestedMessageIds) {
            this(Constants.PROTOCOL_VERSION, senderDeviceId, requestedMessageIds);
        }

        public TransferRequestPayload(int protocolVersion, String senderDeviceId, List<String> requestedMessageIds) {
            this.protocolVersion = protocolVersion;
            this.senderDeviceId = senderDeviceId;
            this.requestedMessageIds = requestedMessageIds != null ? requestedMessageIds : new ArrayList<>();
        }

        public int getProtocolVersion() { return protocolVersion; }
        public String getSenderDeviceId() { return senderDeviceId; }
        public List<String> getRequestedMessageIds() { return requestedMessageIds; }

        @NonNull
        public byte[] toBytes() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("protocolVersion", protocolVersion);
            json.put("messageType", Constants.MESSAGE_TYPE_TRANSFER_REQUEST);
            json.put("senderDeviceId", senderDeviceId);

            JSONArray arr = new JSONArray();
            for (String id : requestedMessageIds) {
                arr.put(id);
            }
            json.put("requestedMessageIds", arr);
            return json.toString().getBytes(StandardCharsets.UTF_8);
        }

        @NonNull
        public static TransferRequestPayload fromBytes(@Nullable byte[] bytes) throws JSONException, IllegalArgumentException {
            if (bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("Transfer request payload is empty");
            }
            String str = new String(bytes, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(str);

            int version = json.optInt("protocolVersion", 1);
            String type = json.optString("messageType", "");
            if (!Constants.MESSAGE_TYPE_TRANSFER_REQUEST.equals(type)) {
                throw new IllegalArgumentException("Invalid messageType for transfer request: " + type);
            }

            List<String> ids = new ArrayList<>();
            JSONArray arr = json.optJSONArray("requestedMessageIds");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    ids.add(arr.optString(i));
                }
            }

            return new TransferRequestPayload(
                    version,
                    json.optString("senderDeviceId", "UNKNOWN_DEV"),
                    ids
            );
        }
    }

    /**
     * Inspects a raw byte payload to quickly determine its messageType without full parsing.
     * Returns Constants.MESSAGE_TYPE_SOS if unspecified or on error.
     */
    @NonNull
    public static String peekMessageType(@Nullable byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return Constants.MESSAGE_TYPE_SOS;
        }
        try {
            String str = new String(bytes, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(str);
            return json.optString("messageType", Constants.MESSAGE_TYPE_SOS);
        } catch (Exception e) {
            return Constants.MESSAGE_TYPE_SOS;
        }
    }
}
