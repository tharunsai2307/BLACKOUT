package com.blackout.emergency.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

/**
 * Data Access Object for locally cached user profiles.
 */
@Dao
public interface UserProfileDao {

    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    LiveData<UserProfileEntity> getProfile(String userId);

    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    UserProfileEntity getProfileSync(String userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(UserProfileEntity profile);

    @Update
    void update(UserProfileEntity profile);

    @Query("DELETE FROM user_profiles WHERE userId = :userId")
    void deleteByUserId(String userId);

    @Query("DELETE FROM user_profiles")
    void clearAll();
}
