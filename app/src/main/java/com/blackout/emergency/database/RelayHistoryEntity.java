package com.blackout.emergency.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Database entity recording successful and attempted peer-to-peer relay transfers.
 * Used for duplicate suppression and delivery audits.
 */
@Entity(
        tableName = "relay_history",
        indices = {
                @Index(value = {"messageId"}),
                @Index(value = {"toDeviceId"}),
                @Index(value = {"messageId", "toDeviceId"})
        }
)
public class RelayHistoryEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String messageId;
    private String fromDeviceId;
    private String toDeviceId;
    private int hopNumber;
    private String transferStatus;
    private long timestamp;

    public RelayHistoryEntity() {
        this.messageId = "";
    }

    public RelayHistoryEntity(@NonNull String messageId, String fromDeviceId, String toDeviceId,
                              int hopNumber, String transferStatus, long timestamp) {
        this.messageId = messageId;
        this.fromDeviceId = fromDeviceId;
        this.toDeviceId = toDeviceId;
        this.hopNumber = hopNumber;
        this.transferStatus = transferStatus;
        this.timestamp = timestamp;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(@NonNull String messageId) {
        this.messageId = messageId;
    }

    public String getFromDeviceId() {
        return fromDeviceId;
    }

    public void setFromDeviceId(String fromDeviceId) {
        this.fromDeviceId = fromDeviceId;
    }

    public String getToDeviceId() {
        return toDeviceId;
    }

    public void setToDeviceId(String toDeviceId) {
        this.toDeviceId = toDeviceId;
    }

    public int getHopNumber() {
        return hopNumber;
    }

    public void setHopNumber(int hopNumber) {
        this.hopNumber = hopNumber;
    }

    public String getTransferStatus() {
        return transferStatus;
    }

    public void setTransferStatus(String transferStatus) {
        this.transferStatus = transferStatus;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
