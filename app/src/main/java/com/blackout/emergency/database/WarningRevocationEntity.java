package com.blackout.emergency.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room Database entity recording formal warning revocations (Phase 7).
 */
@Entity(tableName = "warning_revocations")
public class WarningRevocationEntity {

    @PrimaryKey
    @NonNull
    private String warningId;
    private int revision;
    private String revocationReason;
    private String revocationSignature;
    private long revokedAt;

    public WarningRevocationEntity() {
        this.warningId = "";
        this.revokedAt = System.currentTimeMillis();
    }

    public WarningRevocationEntity(@NonNull String warningId, int revision,
                                   String revocationReason, String revocationSignature,
                                   long revokedAt) {
        this.warningId = warningId;
        this.revision = revision;
        this.revocationReason = revocationReason;
        this.revocationSignature = revocationSignature;
        this.revokedAt = revokedAt;
    }

    @NonNull
    public String getWarningId() {
        return warningId;
    }

    public void setWarningId(@NonNull String warningId) {
        this.warningId = warningId;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public void setRevocationReason(String revocationReason) {
        this.revocationReason = revocationReason;
    }

    public String getRevocationSignature() {
        return revocationSignature;
    }

    public void setRevocationSignature(String revocationSignature) {
        this.revocationSignature = revocationSignature;
    }

    public long getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(long revokedAt) {
        this.revokedAt = revokedAt;
    }
}

