package com.blackout.emergency.warnings.relay;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.blackout.emergency.utils.Constants;
import com.blackout.emergency.warnings.models.PublicWarning;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Protocol models and binary JSON serializers for Phase 7 offline warning distribution:
 * - WARNING_INVENTORY
 * - WARNING_PAYLOAD
 * - WARNING_DELIVERY_ACK
 * - WARNING_DELIVERY_REJECTED
 */
public final class WarningRelayModels {

    private WarningRelayModels() {
        // Prevent instantiation
    }

    public static String peekMessageType(@Nullable byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        try {
            String str = new String(bytes, StandardCharsets.UTF_8);
            if (!str.startsWith("{")) return "";
            JSONObject json = new JSONObject(str);
            return json.optString("messageType", "");
        } catch (Exception e) {
            return "";
        }
    }

    // =========================================================================
    // 1. Warning Inventory Item & Payload
    // =========================================================================

    public static class InventoryItem {
        public final String warningId;
        public final int revision;
        public final String payloadHash;
        public final long expiresAt;
        public final String verificationStatus;

        public InventoryItem(String warningId, int revision, String payloadHash,
                             long expiresAt, String verificationStatus) {
            this.warningId = warningId;
            this.revision = revision;
            this.payloadHash = payloadHash != null ? payloadHash : "";
            this.expiresAt = expiresAt;
            this.verificationStatus = verificationStatus != null ? verificationStatus : "";
        }

        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("warningId", warningId);
            obj.put("revision", revision);
            obj.put("payloadHash", payloadHash);
            obj.put("expiresAt", expiresAt);
            obj.put("verificationStatus", verificationStatus);
            return obj;
        }

        public static InventoryItem fromJson(JSONObject obj) {
            return new InventoryItem(
                    obj.optString("warningId", ""),
                    obj.optInt("revision", 1),
                    obj.optString("payloadHash", ""),
                    obj.optLong("expiresAt", 0),
                    obj.optString("verificationStatus", "")
            );
        }
    }

    public static class WarningInventoryPayload {
        private final int protocolVersion;
        private final String senderDeviceId;
        private final List<InventoryItem> items;

        public WarningInventoryPayload(String senderDeviceId, List<InventoryItem> items) {
            this.protocolVersion = Constants.PROTOCOL_VERSION;
            this.senderDeviceId = senderDeviceId;
            this.items = items != null ? items : new ArrayList<>();
        }

        public int getProtocolVersion() {
            return protocolVersion;
        }

        public String getSenderDeviceId() {
            return senderDeviceId;
        }

        public List<InventoryItem> getItems() {
            return items;
        }

        @NonNull
        public byte[] toBytes() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("protocolVersion", protocolVersion);
            json.put("messageType", Constants.MESSAGE_TYPE_WARNING_INVENTORY);
            json.put("senderDeviceId", senderDeviceId);

            JSONArray arr = new JSONArray();
            for (InventoryItem item : items) {
                arr.put(item.toJson());
            }
            json.put("inventory", arr);
            return json.toString().getBytes(StandardCharsets.UTF_8);
        }

        @NonNull
        public static WarningInventoryPayload fromBytes(@Nullable byte[] bytes) throws JSONException, IllegalArgumentException {
            if (bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("Warning inventory payload is empty");
            }
            String str = new String(bytes, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(str);

            int version = json.optInt("protocolVersion", -1);
            if (version != Constants.PROTOCOL_VERSION) {
                throw new IllegalArgumentException("Unsupported protocol version: " + version);
            }

            String senderDev = json.optString("senderDeviceId", "UNKNOWN_DEV");
            JSONArray arr = json.optJSONArray("inventory");
            List<InventoryItem> itemList = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    itemList.add(InventoryItem.fromJson(arr.getJSONObject(i)));
                }
            }
            return new WarningInventoryPayload(senderDev, itemList);
        }
    }

    // =========================================================================
    // 2. Transferred Warning Payload
    // =========================================================================

    public static class WarningTransferPayload {
        private final int protocolVersion;
        private final String senderDeviceId;
        private final PublicWarning warning;

        public WarningTransferPayload(String senderDeviceId, PublicWarning warning) {
            this.protocolVersion = Constants.PROTOCOL_VERSION;
            this.senderDeviceId = senderDeviceId;
            this.warning = warning;
        }

        public int getProtocolVersion() {
            return protocolVersion;
        }

        public String getSenderDeviceId() {
            return senderDeviceId;
        }

        public PublicWarning getWarning() {
            return warning;
        }

        @NonNull
        public byte[] toBytes() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("protocolVersion", protocolVersion);
            json.put("messageType", Constants.MESSAGE_TYPE_WARNING_PAYLOAD);
            json.put("senderDeviceId", senderDeviceId);
            json.put("warning", warning.toJson());
            return json.toString().getBytes(StandardCharsets.UTF_8);
        }

        @NonNull
        public static WarningTransferPayload fromBytes(@Nullable byte[] bytes) throws JSONException, IllegalArgumentException {
            if (bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("Warning transfer payload is empty");
            }
            String str = new String(bytes, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(str);

            int version = json.optInt("protocolVersion", -1);
            if (version != Constants.PROTOCOL_VERSION) {
                throw new IllegalArgumentException("Unsupported protocol version: " + version);
            }

            String senderDev = json.optString("senderDeviceId", "UNKNOWN_DEV");
            JSONObject warningObj = json.getJSONObject("warning");
            PublicWarning warning = PublicWarning.fromJson(warningObj);
            return new WarningTransferPayload(senderDev, warning);
        }
    }

    // =========================================================================
    // 3. Delivery Acknowledgement
    // =========================================================================

    public static class WarningAckPayload {
        private final int protocolVersion;
        private final String warningId;
        private final int revision;
        private final String senderDeviceId;
        private final String status; // ACCEPTED, DUPLICATE

        public WarningAckPayload(String warningId, int revision, String senderDeviceId, String status) {
            this.protocolVersion = Constants.PROTOCOL_VERSION;
            this.warningId = warningId;
            this.revision = revision;
            this.senderDeviceId = senderDeviceId;
            this.status = status;
        }

        public String getWarningId() {
            return warningId;
        }

        public int getRevision() {
            return revision;
        }

        public String getSenderDeviceId() {
            return senderDeviceId;
        }

        public String getStatus() {
            return status;
        }

        @NonNull
        public byte[] toBytes() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("protocolVersion", protocolVersion);
            json.put("messageType", Constants.MESSAGE_TYPE_WARNING_ACK);
            json.put("warningId", warningId);
            json.put("revision", revision);
            json.put("senderDeviceId", senderDeviceId);
            json.put("status", status);
            return json.toString().getBytes(StandardCharsets.UTF_8);
        }

        @NonNull
        public static WarningAckPayload fromBytes(@Nullable byte[] bytes) throws JSONException {
            String str = new String(bytes, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(str);
            return new WarningAckPayload(
                    json.getString("warningId"),
                    json.optInt("revision", 1),
                    json.optString("senderDeviceId", ""),
                    json.optString("status", "ACCEPTED")
            );
        }
    }

    // =========================================================================
    // 4. Delivery Rejection
    // =========================================================================

    public static class WarningRejectPayload {
        private final int protocolVersion;
        private final String warningId;
        private final int revision;
        private final String senderDeviceId;
        private final String reason;

        public WarningRejectPayload(String warningId, int revision, String senderDeviceId, String reason) {
            this.protocolVersion = Constants.PROTOCOL_VERSION;
            this.warningId = warningId;
            this.revision = revision;
            this.senderDeviceId = senderDeviceId;
            this.reason = reason;
        }

        public String getWarningId() {
            return warningId;
        }

        public int getRevision() {
            return revision;
        }

        public String getSenderDeviceId() {
            return senderDeviceId;
        }

        public String getReason() {
            return reason;
        }

        @NonNull
        public byte[] toBytes() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("protocolVersion", protocolVersion);
            json.put("messageType", Constants.MESSAGE_TYPE_WARNING_REJECTED);
            json.put("warningId", warningId);
            json.put("revision", revision);
            json.put("senderDeviceId", senderDeviceId);
            json.put("reason", reason);
            return json.toString().getBytes(StandardCharsets.UTF_8);
        }

        @NonNull
        public static WarningRejectPayload fromBytes(@Nullable byte[] bytes) throws JSONException {
            String str = new String(bytes, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(str);
            return new WarningRejectPayload(
                    json.optString("warningId", ""),
                    json.optInt("revision", 1),
                    json.optString("senderDeviceId", ""),
                    json.optString("reason", "Rejected")
            );
        }
    }
}
