package com.blackout.emergency.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.blackout.emergency.models.EmergencyMessage;

/**
 * Room Database entity representing an emergency SOS message record.
 */
@Entity(tableName = "emergency_messages")
public class EmergencyMessageEntity {

    @PrimaryKey
    @NonNull
    private String messageId;
    private String senderDeviceId;
    private String senderCallsign;
    private String emergencyCategory;
    private String description;
    private Double latitude;
    private Double longitude;
    private Float locationAccuracy;
    private Long locationTimestamp;
    private Long createdAt;
    private Long expiresAt;
    private String priority;
    private String status;
    private String locationStatus;
    private int hopCount;

    public EmergencyMessageEntity() {
        this.messageId = "";
    }

    public EmergencyMessageEntity(@NonNull String messageId, String senderDeviceId, String senderCallsign,
                                  String emergencyCategory, String description, Double latitude,
                                  Double longitude, Float locationAccuracy, Long locationTimestamp,
                                  Long createdAt, Long expiresAt, String priority, String status,
                                  String locationStatus, int hopCount) {
        this.messageId = messageId;
        this.senderDeviceId = senderDeviceId;
        this.senderCallsign = senderCallsign;
        this.emergencyCategory = emergencyCategory;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationAccuracy = locationAccuracy;
        this.locationTimestamp = locationTimestamp;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.priority = priority;
        this.status = status;
        this.locationStatus = locationStatus;
        this.hopCount = hopCount;
    }

    public static EmergencyMessageEntity fromModel(EmergencyMessage model) {
        if (model == null) {
            return null;
        }
        return new EmergencyMessageEntity(
                model.getMessageId(),
                model.getSenderDeviceId(),
                model.getSenderCallsign(),
                model.getEmergencyCategory(),
                model.getDescription(),
                model.getLatitude(),
                model.getLongitude(),
                model.getLocationAccuracy(),
                model.getLocationTimestamp(),
                model.getCreatedAt(),
                model.getExpiresAt(),
                model.getPriority(),
                model.getStatus(),
                model.getLocationStatus(),
                model.getHopCount()
        );
    }

    public EmergencyMessage toModel() {
        EmergencyMessage model = new EmergencyMessage(
                this.senderDeviceId,
                this.senderCallsign,
                this.emergencyCategory,
                this.description,
                this.priority,
                this.latitude,
                this.longitude,
                this.locationAccuracy,
                this.locationTimestamp,
                this.status,
                this.locationStatus
        );
        model.setMessageId(this.messageId);
        model.setCreatedAt(this.createdAt);
        model.setExpiresAt(this.expiresAt);
        model.setHopCount(this.hopCount);
        return model;
    }

    @NonNull
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(@NonNull String messageId) {
        this.messageId = messageId;
    }

    public String getSenderDeviceId() {
        return senderDeviceId;
    }

    public void setSenderDeviceId(String senderDeviceId) {
        this.senderDeviceId = senderDeviceId;
    }

    public String getSenderCallsign() {
        return senderCallsign;
    }

    public void setSenderCallsign(String senderCallsign) {
        this.senderCallsign = senderCallsign;
    }

    public String getEmergencyCategory() {
        return emergencyCategory;
    }

    public void setEmergencyCategory(String emergencyCategory) {
        this.emergencyCategory = emergencyCategory;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Float getLocationAccuracy() {
        return locationAccuracy;
    }

    public void setLocationAccuracy(Float locationAccuracy) {
        this.locationAccuracy = locationAccuracy;
    }

    public Long getLocationTimestamp() {
        return locationTimestamp;
    }

    public void setLocationTimestamp(Long locationTimestamp) {
        this.locationTimestamp = locationTimestamp;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLocationStatus() {
        return locationStatus;
    }

    public void setLocationStatus(String locationStatus) {
        this.locationStatus = locationStatus;
    }

    public int getHopCount() {
        return hopCount;
    }

    public void setHopCount(int hopCount) {
        this.hopCount = hopCount;
    }
}
