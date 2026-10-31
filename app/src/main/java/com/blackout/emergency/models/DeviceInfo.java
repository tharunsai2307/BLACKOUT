package com.blackout.emergency.models;

import java.io.Serializable;

/**
 * Represents a nearby discovered peer node participating in the BLACKOUT mesh network.
 */
public class DeviceInfo implements Serializable {

    private String deviceId;
    private String callsign;
    private int rssi;
    private boolean isConnected;
    private double distanceEstimate;
    private String supportedProtocols;
    private long lastSeenTimestamp;

    public DeviceInfo() {
        this.lastSeenTimestamp = System.currentTimeMillis();
    }

    public DeviceInfo(String deviceId, String callsign, int rssi, boolean isConnected,
                      double distanceEstimate, String supportedProtocols) {
        this.deviceId = deviceId;
        this.callsign = callsign;
        this.rssi = rssi;
        this.isConnected = isConnected;
        this.distanceEstimate = distanceEstimate;
        this.supportedProtocols = supportedProtocols;
        this.lastSeenTimestamp = System.currentTimeMillis();
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(String callsign) {
        this.callsign = callsign;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }

    public double getDistanceEstimate() {
        return distanceEstimate;
    }

    public void setDistanceEstimate(double distanceEstimate) {
        this.distanceEstimate = distanceEstimate;
    }

    public String getSupportedProtocols() {
        return supportedProtocols;
    }

    public void setSupportedProtocols(String supportedProtocols) {
        this.supportedProtocols = supportedProtocols;
    }

    public long getLastSeenTimestamp() {
        return lastSeenTimestamp;
    }

    public void setLastSeenTimestamp(long lastSeenTimestamp) {
        this.lastSeenTimestamp = lastSeenTimestamp;
    }
}
