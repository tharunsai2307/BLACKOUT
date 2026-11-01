package com.blackout.emergency.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

/**
 * Data Access Object for Trusted Signing Keys (Phase 7).
 */
@Dao
public interface TrustedSigningKeyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(TrustedSigningKeyEntity key);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<TrustedSigningKeyEntity> keys);

    @Query("SELECT * FROM trusted_signing_keys WHERE keyId = :keyId LIMIT 1")
    TrustedSigningKeyEntity getKeyById(String keyId);

    @Query("SELECT * FROM trusted_signing_keys ORDER BY validFrom ASC")
    List<TrustedSigningKeyEntity> getAllKeys();

    @Query("UPDATE trusted_signing_keys SET keyStatus = 'REVOKED', revokedAt = :revokedAt, revocationReason = :reason WHERE keyId = :keyId")
    void revokeKey(String keyId, long revokedAt, String reason);
}

