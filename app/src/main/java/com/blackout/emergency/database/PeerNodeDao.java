package com.blackout.emergency.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

/**
 * Room Data Access Object for nearby mesh peers and session metadata.
 */
@Dao
public interface PeerNodeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(PeerNodeEntity entity);

    @Query("SELECT * FROM peer_nodes ORDER BY lastSeen DESC")
    List<PeerNodeEntity> getAllPeers();

    @Query("SELECT * FROM peer_nodes WHERE deviceId = :deviceId LIMIT 1")
    PeerNodeEntity getPeer(String deviceId);

    @Query("SELECT * FROM peer_nodes WHERE endpointId = :endpointId LIMIT 1")
    PeerNodeEntity getPeerByEndpoint(String endpointId);

    @Query("SELECT * FROM peer_nodes WHERE isGateway = 1 AND lastSeen >= :activeThreshold ORDER BY batteryLevel DESC")
    List<PeerNodeEntity> getActiveGateways(long activeThreshold);

    @Query("UPDATE peer_nodes SET isGateway = :isGateway, networkType = :networkType, batteryLevel = :battery, lastSeen = :now WHERE deviceId = :deviceId")
    void updateGatewayStatus(String deviceId, boolean isGateway, String networkType, int battery, long now);

    @Query("DELETE FROM peer_nodes WHERE lastSeen < :cutoff")
    void deleteStalePeers(long cutoff);

    @Query("SELECT COUNT(*) FROM peer_nodes WHERE lastSeen >= :activeThreshold")
    int getActivePeerCount(long activeThreshold);
}

