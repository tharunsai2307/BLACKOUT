package com.blackout.emergency.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

/**
 * Data Access Object for Warning Revision History (Phase 7).
 */
@Dao
public interface WarningRevisionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRevision(WarningRevisionEntity revision);

    @Query("SELECT * FROM warning_revisions WHERE warningId = :warningId ORDER BY revision DESC")
    List<WarningRevisionEntity> getRevisionsForWarning(String warningId);

    @Query("SELECT * FROM warning_revisions WHERE warningId = :warningId ORDER BY revision DESC LIMIT 1")
    WarningRevisionEntity getLatestRevision(String warningId);

    @Query("SELECT MAX(revision) FROM warning_revisions WHERE warningId = :warningId")
    Integer getHighestRevisionNumber(String warningId);
}
