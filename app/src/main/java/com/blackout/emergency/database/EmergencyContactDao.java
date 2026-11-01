package com.blackout.emergency.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

/**
 * Data Access Object for local emergency contact management.
 */
@Dao
public interface EmergencyContactDao {

    @Query("SELECT * FROM emergency_contacts WHERE userId = :userId AND syncStatus != 'PENDING_DELETE' ORDER BY isPrimary DESC, contactName ASC")
    LiveData<List<EmergencyContactEntity>> getContactsForUser(String userId);

    @Query("SELECT * FROM emergency_contacts WHERE userId = :userId AND syncStatus != 'PENDING_DELETE' ORDER BY isPrimary DESC, contactName ASC")
    List<EmergencyContactEntity> getContactsForUserSync(String userId);

    @Query("SELECT * FROM emergency_contacts WHERE userId = :userId AND isPrimary = 1 AND syncStatus != 'PENDING_DELETE' LIMIT 1")
    EmergencyContactEntity getPrimaryContactSync(String userId);

    @Query("SELECT * FROM emergency_contacts WHERE id = :id LIMIT 1")
    EmergencyContactEntity getContactById(String id);

    @Query("SELECT * FROM emergency_contacts WHERE syncStatus != 'SYNCED'")
    List<EmergencyContactEntity> getPendingSyncContacts();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(EmergencyContactEntity contact);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<EmergencyContactEntity> contacts);

    @Update
    void update(EmergencyContactEntity contact);

    @Delete
    void delete(EmergencyContactEntity contact);

    @Query("DELETE FROM emergency_contacts WHERE id = :id")
    void deleteById(String id);

    @Query("DELETE FROM emergency_contacts WHERE userId = :userId")
    void clearContactsForUser(String userId);

    @Query("UPDATE emergency_contacts SET isPrimary = 0 WHERE userId = :userId")
    void clearPrimaryStatusForUser(String userId);
}

