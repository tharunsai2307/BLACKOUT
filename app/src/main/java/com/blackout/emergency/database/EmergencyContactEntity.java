package com.blackout.emergency.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity representing a locally cached emergency contact.
 * Enables offline viewing and queuing of contact modifications during disasters.
 */
@Entity(
        tableName = "emergency_contacts",
        indices = {
                @Index(value = "userId"),
                @Index(value = "isPrimary")
        }
)
public class EmergencyContactEntity {

    @PrimaryKey
    @NonNull
    public String id; // UUID

    @NonNull
    public String userId;

    @NonNull
    public String contactName;

    @NonNull
    public String phoneNumber;

    public String relationship;

    public boolean isPrimary;

    @NonNull
    public String syncStatus; // SYNCED, PENDING_UPLOAD, PENDING_DELETE

    public long updatedAt;

    @androidx.room.Ignore
    public String contactPhone;

    public EmergencyContactEntity(@NonNull String id, @NonNull String userId, @NonNull String contactName,
                                  @NonNull String phoneNumber, String relationship, boolean isPrimary,
                                  @NonNull String syncStatus, long updatedAt) {
        this.id = id;
        this.userId = userId;
        this.contactName = contactName;
        this.phoneNumber = phoneNumber;
        this.relationship = relationship;
        this.isPrimary = isPrimary;
        this.syncStatus = syncStatus;
        this.updatedAt = updatedAt;
        this.contactPhone = phoneNumber;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    @NonNull
    public String getUserId() {
        return userId;
    }

    public void setUserId(@NonNull String userId) {
        this.userId = userId;
    }

    @NonNull
    public String getContactName() {
        return contactName;
    }

    public void setContactName(@NonNull String contactName) {
        this.contactName = contactName;
    }

    @NonNull
    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(@NonNull String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public void setPrimary(boolean primary) {
        isPrimary = primary;
    }

    @NonNull
    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(@NonNull String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
