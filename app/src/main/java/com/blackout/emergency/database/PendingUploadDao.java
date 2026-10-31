package com.blackout.emergency.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

/**
 * Data Access Object (DAO) for managing pending cloud uploads in Room.
 */
@Dao
public interface PendingUploadDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(PendingUploadEntity entity);

    @Update
    void update(PendingUploadEntity entity);

    @Query("SELECT * FROM pending_uploads WHERE messageId = :messageId LIMIT 1")
    PendingUploadEntity getByMessageId(String messageId);

    @Query("SELECT * FROM pending_uploads WHERE uploadStatus IN ('UPLOAD_PENDING', 'RETRY_WAIT') " +
            "AND nextAttemptAt <= :now ORDER BY nextAttemptAt ASC LIMIT :limit")
    List<PendingUploadEntity> getEligibleUploads(long now, int limit);

    @Query("SELECT * FROM pending_uploads ORDER BY nextAttemptAt DESC")
    List<PendingUploadEntity> getAllUploads();

    @Query("UPDATE pending_uploads SET uploadStatus = :status, uploadedAt = :uploadedAt, " +
            "backendReference = :backendRef WHERE messageId = :messageId")
    void markAccepted(String messageId, String status, long uploadedAt, String backendRef);

    @Query("UPDATE pending_uploads SET uploadStatus = :status, attemptCount = :attemptCount, " +
            "nextAttemptAt = :nextAttemptAt, lastAttemptAt = :lastAttemptAt, lastErrorCode = :error WHERE messageId = :messageId")
    void updateRetry(String messageId, String status, int attemptCount, long nextAttemptAt, long lastAttemptAt, String error);

    @Query("SELECT COUNT(*) FROM pending_uploads WHERE uploadStatus IN ('UPLOAD_PENDING', 'RETRY_WAIT', 'UPLOADING')")
    int getPendingUploadCount();

    @Query("DELETE FROM pending_uploads WHERE messageId = :messageId")
    void deleteByMessageId(String messageId);

    @Query("DELETE FROM pending_uploads")
    void deleteAll();
}
