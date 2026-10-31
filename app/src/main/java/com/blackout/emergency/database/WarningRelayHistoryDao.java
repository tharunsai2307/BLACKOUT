package com.blackout.emergency.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

/**
 * Data Access Object for Warning Relay Events (Phase 7).
 */
@Dao
public interface WarningRelayHistoryDao {

    @Insert
    long insert(WarningRelayHistoryEntity history);

    @Query("SELECT * FROM warning_relay_history WHERE warningId = :warningId ORDER BY timestamp DESC")
    List<WarningRelayHistoryEntity> getHistoryForWarning(String warningId);

    @Query("SELECT COUNT(*) FROM warning_relay_history WHERE warningId = :warningId AND revision = :revision AND peerDeviceId = :peerDeviceId AND relayAction = 'SENT'")
    int countRelaysToPeer(String warningId, int revision, String peerDeviceId);
}
