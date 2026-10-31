package com.blackout.emergency.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

/**
 * Data Access Object for Warning Cloud Sync State (Phase 7).
 */
@Dao
public interface WarningSyncStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void setSyncState(WarningSyncStateEntity state);

    @Query("SELECT * FROM warning_sync_state WHERE syncKey = :syncKey LIMIT 1")
    WarningSyncStateEntity getSyncState(String syncKey);
}
