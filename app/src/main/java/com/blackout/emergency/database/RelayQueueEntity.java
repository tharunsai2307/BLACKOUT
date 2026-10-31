package com.blackout.emergency.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Database entity representing a message queued in the persistent
 * store-carry-and-forward relay pipeline.
 */
@Entity(
        tableName = "relay_queue",
        indices = {
                @Index(value = {"relayStatus"}),
                @Index(value = {"nextAttemptAt"})
        }
)
public class RelayQueueEntity {

    @PrimaryKey
    @NonNull
    private String messageId;
    private String relayStatus;
    private long queuedAt;
    private long nextAttemptAt;
    private int attemptCount;
    private long lastAttemptAt;
    private String lastError;
    private String priority;

    public RelayQueueEntity() {
        this.messageId = "";
    }

    public RelayQueueEntity(@NonNull String messageId, String relayStatus, long queuedAt,
                            long nextAttemptAt, int attemptCount, long lastAttemptAt,
                            String lastError, String priority) {
        this.messageId = messageId;
        this.relayStatus = relayStatus;
        this.queuedAt = queuedAt;
        this.nextAttemptAt = nextAttemptAt;
        this.attemptCount = attemptCount;
        this.lastAttemptAt = lastAttemptAt;
        this.lastError = lastError;
        this.priority = priority;
    }

    @NonNull
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(@NonNull String messageId) {
        this.messageId = messageId;
    }

    public String getRelayStatus() {
        return relayStatus;
    }

    public void setRelayStatus(String relayStatus) {
        this.relayStatus = relayStatus;
    }

    public long getQueuedAt() {
        return queuedAt;
    }

    public void setQueuedAt(long queuedAt) {
        this.queuedAt = queuedAt;
    }

    public long getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(long nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public long getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(long lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }
}
