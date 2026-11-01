package com.blackout.emergency.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

/**
 * Data Access Object for Public Warnings (Phase 7).
 */
@Dao
public interface PublicWarningDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(PublicWarningEntity warning);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<PublicWarningEntity> warnings);

    @Query("SELECT * FROM public_warnings WHERE warningId = :warningId LIMIT 1")
    PublicWarningEntity getWarningById(String warningId);

    @Query("SELECT * FROM public_warnings ORDER BY issuedAt DESC")
    List<PublicWarningEntity> getAllWarnings();

    @Query("SELECT * FROM public_warnings WHERE verificationStatus = 'VERIFIED' AND status = 'PUBLISHED' AND expiresAt > :currentTimeMillis ORDER BY severity = 'CRITICAL' DESC, issuedAt DESC")
    List<PublicWarningEntity> getActiveVerifiedWarnings(long currentTimeMillis);

    @Query("SELECT * FROM public_warnings WHERE status = :status ORDER BY issuedAt DESC")
    List<PublicWarningEntity> getWarningsByStatus(String status);

    @Query("UPDATE public_warnings SET verificationStatus = :status WHERE warningId = :warningId")
    void updateVerificationStatus(String warningId, String status);

    @Query("UPDATE public_warnings SET lastRelayedAt = :timestamp WHERE warningId = :warningId")
    void updateRelayedTimestamp(String warningId, long timestamp);

    @Query("DELETE FROM public_warnings WHERE warningId = :warningId")
    void deleteWarning(String warningId);

    @Query("DELETE FROM public_warnings")
    void deleteAll();

    public static class InventoryItem {
        public String warningId;
        public int revision;
        public String payloadHash;
        public long expiresAt;
        public String verificationStatus;
    }

    @Query("SELECT warningId, revision, payloadHash, expiresAt, verificationStatus FROM public_warnings WHERE expiresAt > :now AND verificationStatus = 'VERIFIED'")
    List<InventoryItem> getEligibleWarningInventory(long now);

    @Query("SELECT warningId, revision, payloadHash, expiresAt, verificationStatus FROM public_warnings")
    List<InventoryItem> getAllWarningInventory();
}

