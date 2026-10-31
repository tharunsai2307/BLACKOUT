package com.blackout.emergency.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

/**
 * Data Access Object (DAO) for emergency SOS message records.
 */
@Dao
public interface EmergencyMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(EmergencyMessageEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<EmergencyMessageEntity> entities);

    @Update
    void update(EmergencyMessageEntity entity);

    @Delete
    void delete(EmergencyMessageEntity entity);

    @Query("DELETE FROM emergency_messages WHERE messageId = :id")
    void deleteById(String id);

    @Query("SELECT * FROM emergency_messages ORDER BY createdAt DESC")
    List<EmergencyMessageEntity> getAllMessages();

    @Query("SELECT * FROM emergency_messages WHERE messageId = :id LIMIT 1")
    EmergencyMessageEntity getMessageById(String id);

    @Query("SELECT * FROM emergency_messages WHERE emergencyCategory = :category ORDER BY createdAt DESC")
    List<EmergencyMessageEntity> getMessagesByCategory(String category);

    @Query("SELECT COUNT(*) FROM emergency_messages")
    int getMessageCount();

    @Query("UPDATE emergency_messages SET status = :status WHERE messageId = :id")
    void updateStatus(String id, String status);

    @Query("SELECT * FROM emergency_messages WHERE status != 'RECEIVED_FROM_NEARBY_DEVICE' ORDER BY createdAt DESC")
    List<EmergencyMessageEntity> getOutgoingMessages();

    @Query("SELECT COUNT(*) FROM emergency_messages WHERE messageId = :id")
    int countById(String id);

    @Query("DELETE FROM emergency_messages")
    void deleteAll();
}
