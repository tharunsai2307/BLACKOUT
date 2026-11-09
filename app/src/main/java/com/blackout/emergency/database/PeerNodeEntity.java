package com.blackout.emergency.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Database entity representing a nearby mesh peer node,
 * its cryptographic session metadata, and gateway capabilities (Phase 12).
 */
@Entity(
        tableName = "peer_nodes",
        indices = {
                @Index(value = {"endpointId"}),
                @Index(value = {"isGateway"}),
                @Index(value = {"lastSeen"})
        }
)
public class PeerNodeEntity {

    @PrimaryKey
    @NonNull
    private String deviceId;
    private String callsign;
    private String endpointId;
    private boolean isGateway;
    private String networkType;
    private int batteryLevel;
    private long lastSeen;
    private int trustScore;
    private String publicKey;
    private String handshakeStatus;

    public PeerNodeEntity(@NonNull String deviceId, String callsign, String endpointId,
                          boolean isGateway, String networkType, int batteryLevel,
                          long lastSeen, int trustScore, String publicKey, String handshakeStatus) {
        this.deviceId = deviceId;
        this.callsign = callsign;
        this.endpointId = endpointId;
        this.isGateway = isGateway;
        this.networkType = networkType;
        this.batteryLevel = batteryLevel;
        this.lastSeen = lastSeen;
        this.trustScore = trustScore;
        this.publicKey = publicKey;
        this.handshakeStatus = handshakeStatus;
    }

    @NonNull
    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(@NonNull String deviceId) {
        this.deviceId = deviceId;
    }

    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(String callsign) {
        this.callsign = callsign;
    }

    public String getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(String endpointId) {
        this.endpointId = endpointId;
    }

    public boolean isGateway() {
        return isGateway;
    }

    public void setGateway(boolean gateway) {
        isGateway = gateway;
    }

    public String getNetworkType() {
        return networkType;
    }

    public void setNetworkType(String networkType) {
        this.networkType = networkType;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public int getTrustScore() {
        return trustScore;
    }

    public void setTrustScore(int trustScore) {
        this.trustScore = trustScore;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getHandshakeStatus() {
        return handshakeStatus;
    }

    public void setHandshakeStatus(String handshakeStatus) {
        this.handshakeStatus = handshakeStatus;
    }
}

