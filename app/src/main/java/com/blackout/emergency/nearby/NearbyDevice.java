package com.blackout.emergency.nearby;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.Serializable;
import java.util.Objects;

/**
 * Model representing a physical Android device discovered or connected via Nearby Connections API.
 */
public class NearbyDevice implements Serializable {

    public enum ConnectionState {
        DISCOVERED,
        CONNECTING,
        REQUESTED_INCOMING,
        REQUESTED_OUTGOING,
        CONNECTED,
        DISCONNECTED
    }

    private final String endpointId;
    private String endpointName;
    private ConnectionState state;
    private String authDigits;
    private long lastSeenTimestamp;

    public NearbyDevice(@NonNull String endpointId, @Nullable String endpointName) {
        this.endpointId = endpointId;
        this.endpointName = endpointName != null && !endpointName.trim().isEmpty() ? endpointName : "BLACKOUT-NODE";
        this.state = ConnectionState.DISCOVERED;
        this.lastSeenTimestamp = System.currentTimeMillis();
    }

    @NonNull
    public String getEndpointId() {
        return endpointId;
    }

    public String getEndpointName() {
        return endpointName;
    }

    public void setEndpointName(String endpointName) {
        this.endpointName = endpointName;
    }

    public ConnectionState getState() {
        return state;
    }

    public void setState(ConnectionState state) {
        this.state = state;
    }

    @Nullable
    public String getAuthDigits() {
        return authDigits;
    }

    public void setAuthDigits(@Nullable String authDigits) {
        this.authDigits = authDigits;
    }

    public long getLastSeenTimestamp() {
        return lastSeenTimestamp;
    }

    public void setLastSeenTimestamp(long lastSeenTimestamp) {
        this.lastSeenTimestamp = lastSeenTimestamp;
    }

    public boolean isConnected() {
        return state == ConnectionState.CONNECTED;
    }

    public boolean isPendingApproval() {
        return state == ConnectionState.REQUESTED_INCOMING || state == ConnectionState.REQUESTED_OUTGOING;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NearbyDevice that = (NearbyDevice) o;
        return Objects.equals(endpointId, that.endpointId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpointId);
    }

    @NonNull
    @Override
    public String toString() {
        return endpointName + " (" + endpointId + ") - " + state;
    }
}
