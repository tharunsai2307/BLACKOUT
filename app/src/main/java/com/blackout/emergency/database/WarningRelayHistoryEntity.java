package com.blackout.emergency.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Database entity tracking peer-to-peer relay events for public warnings (Phase 7).
 */
@Entity(
        tableName = "warning_relay_history",
        indices = {
                @Index(value = {"warningId", "peerDeviceId"}),
                @Index(value = {"timestamp"})
        }
)
public class WarningRelayHistoryEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String warningId;
    private int revision;
    private String peerDeviceId;
    private String relayAction; // SENT, RECEIVED, ACK_RECEIVED, REJECTED
    private long timestamp;
    private String details;

    public WarningRelayHistoryEntity() {
        this.warningId = "";
        this.peerDeviceId = "";
        this.relayAction = "SENT";
        this.timestamp = System.currentTimeMillis();
    }

    public WarningRelayHistoryEntity(@NonNull String warningId, int revision,
                                     @NonNull String peerDeviceId, @NonNull String relayAction,
                                     long timestamp, String details) {
        this.warningId = warningId;
        this.revision = revision;
        this.peerDeviceId = peerDeviceId;
        this.relayAction = relayAction;
        this.timestamp = timestamp;
        this.details = details;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getWarningId() {
        return warningId;
    }

    public void setWarningId(@NonNull String warningId) {
        this.warningId = warningId;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public String getPeerDeviceId() {
        return peerDeviceId;
    }

    public void setPeerDeviceId(String peerDeviceId) {
        this.peerDeviceId = peerDeviceId;
    }

    public String getRelayAction() {
        return relayAction;
    }

    public void setRelayAction(String relayAction) {
        this.relayAction = relayAction;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
