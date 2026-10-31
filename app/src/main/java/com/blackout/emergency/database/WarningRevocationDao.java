package com.blackout.emergency.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

/**
 * Data Access Object for Warning Revocations (Phase 7).
 */
@Dao
public interface WarningRevocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(WarningRevocationEntity revocation);

    @Query("SELECT * FROM warning_revocations WHERE warningId = :warningId LIMIT 1")
    WarningRevocationEntity getRevocation(String warningId);

    @Query("SELECT * FROM warning_revocations ORDER BY revokedAt DESC")
    List<WarningRevocationEntity> getAllRevocations();
}
