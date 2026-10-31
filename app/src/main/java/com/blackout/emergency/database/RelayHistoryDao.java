package com.blackout.emergency.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

/**
 * Data Access Object (DAO) for peer relay history and duplicate suppression logs.
 */
@Dao
public interface RelayHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(RelayHistoryEntity entity);

    @Query("SELECT * FROM relay_history WHERE messageId = :messageId ORDER BY timestamp DESC")
    List<RelayHistoryEntity> getHistoryForMessage(String messageId);

    @Query("SELECT * FROM relay_history ORDER BY timestamp DESC LIMIT :limit")
    List<RelayHistoryEntity> getRecentHistory(int limit);

    @Query("SELECT COUNT(*) FROM relay_history WHERE messageId = :messageId AND toDeviceId = :peerDeviceId " +
            "AND transferStatus = 'RELAYED_TO_PEER'")
    int countDeliveredToPeer(String messageId, String peerDeviceId);

    @Query("SELECT COUNT(*) FROM relay_history")
    int getTotalRelayCount();

    @Query("DELETE FROM relay_history")
    void deleteAll();
}
