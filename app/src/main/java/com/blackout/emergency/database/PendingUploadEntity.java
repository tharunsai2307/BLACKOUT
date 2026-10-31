package com.blackout.emergency.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Database entity representing an emergency SOS message queued for
 * upload to the central cloud backend when internet connectivity becomes available.
 */
@Entity(
        tableName = "pending_uploads",
        indices = {
                @Index(value = {"uploadStatus"}),
                @Index(value = {"nextAttemptAt"})
        }
)
public class PendingUploadEntity {

    @PrimaryKey
    @NonNull
    private String messageId;
    private String uploadStatus;
    private String gatewayDeviceId;
    private int attemptCount;
    private long nextAttemptAt;
    private long lastAttemptAt;
    private long uploadedAt;
    private String backendReference;
    private String lastErrorCode;

    public PendingUploadEntity() {
        this.messageId = "";
    }

    public PendingUploadEntity(@NonNull String messageId, String uploadStatus, String gatewayDeviceId,
                               int attemptCount, long nextAttemptAt, long lastAttemptAt,
                               long uploadedAt, String backendReference, String lastErrorCode) {
        this.messageId = messageId;
        this.uploadStatus = uploadStatus;
        this.gatewayDeviceId = gatewayDeviceId;
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
        this.lastAttemptAt = lastAttemptAt;
        this.uploadedAt = uploadedAt;
        this.backendReference = backendReference;
        this.lastErrorCode = lastErrorCode;
    }

    @NonNull
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(@NonNull String messageId) {
        this.messageId = messageId;
    }

    public String getUploadStatus() {
        return uploadStatus;
    }

    public void setUploadStatus(String uploadStatus) {
        this.uploadStatus = uploadStatus;
    }

    public String getGatewayDeviceId() {
        return gatewayDeviceId;
    }

    public void setGatewayDeviceId(String gatewayDeviceId) {
        this.gatewayDeviceId = gatewayDeviceId;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public long getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(long nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public long getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(long lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public long getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(long uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getBackendReference() {
        return backendReference;
    }

    public void setBackendReference(String backendReference) {
        this.backendReference = backendReference;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }
}
