package com.blackout.emergency.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room Database entity tracking synchronization state with central backend (Phase 7).
 */
@Entity(tableName = "warning_sync_state")
public class WarningSyncStateEntity {

    @PrimaryKey
    @NonNull
    private String syncKey; // e.g. "WARNINGS_SYNC", "KEYS_SYNC"
    private long lastSyncTimestamp;
    private int lastItemCount;
    private String status;
    private String lastError;

    public WarningSyncStateEntity() {
        this.syncKey = "";
        this.status = "IDLE";
    }

    public WarningSyncStateEntity(@NonNull String syncKey, long lastSyncTimestamp,
                                  int lastItemCount, String status, String lastError) {
        this.syncKey = syncKey;
        this.lastSyncTimestamp = lastSyncTimestamp;
        this.lastItemCount = lastItemCount;
        this.status = status;
        this.lastError = lastError;
    }

    @NonNull
    public String getSyncKey() {
        return syncKey;
    }

    public void setSyncKey(@NonNull String syncKey) {
        this.syncKey = syncKey;
    }

    public long getLastSyncTimestamp() {
        return lastSyncTimestamp;
    }

    public void setLastSyncTimestamp(long lastSyncTimestamp) {
        this.lastSyncTimestamp = lastSyncTimestamp;
    }

    public int getLastItemCount() {
        return lastItemCount;
    }

    public void setLastItemCount(int lastItemCount) {
        this.lastItemCount = lastItemCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}

