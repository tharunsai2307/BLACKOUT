package com.blackout.emergency.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

/**
 * Data Access Object (DAO) for the persistent store-carry-and-forward relay queue.
 */
@Dao
public interface RelayQueueDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(RelayQueueEntity entity);

    @Update
    void update(RelayQueueEntity entity);

    @Query("SELECT * FROM relay_queue WHERE messageId = :messageId LIMIT 1")
    RelayQueueEntity getByMessageId(String messageId);

    @Query("SELECT * FROM relay_queue WHERE relayStatus IN ('PENDING', 'RETRY_WAIT') AND nextAttemptAt <= :now ORDER BY " +
            "CASE priority " +
            "WHEN 'CRITICAL' THEN 1 " +
            "WHEN 'HIGH' THEN 2 " +
            "WHEN 'MODERATE' THEN 3 " +
            "ELSE 4 END ASC, queuedAt ASC LIMIT :limit")
    List<RelayQueueEntity> getEligibleQueueItems(long now, int limit);

    @Query("SELECT * FROM relay_queue ORDER BY queuedAt DESC")
    List<RelayQueueEntity> getAllQueueItems();

    @Query("UPDATE relay_queue SET relayStatus = :status, lastAttemptAt = :now WHERE messageId = :messageId")
    void updateStatus(String messageId, String status, long now);

    @Query("UPDATE relay_queue SET relayStatus = :status, attemptCount = :attemptCount, nextAttemptAt = :nextAttemptAt, " +
            "lastAttemptAt = :lastAttemptAt, lastError = :error WHERE messageId = :messageId")
    void updateRetry(String messageId, String status, int attemptCount, long nextAttemptAt, long lastAttemptAt, String error);

    @Query("DELETE FROM relay_queue WHERE messageId = :messageId")
    void deleteByMessageId(String messageId);

    @Query("SELECT COUNT(*) FROM relay_queue WHERE relayStatus IN ('PENDING', 'RETRY_WAIT', 'IN_PROGRESS')")
    int getPendingRelayCount();

    @Query("DELETE FROM relay_queue")
    void deleteAll();
}
