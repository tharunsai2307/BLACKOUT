package com.blackout.emergency.models;

import com.blackout.emergency.utils.Constants;
import java.io.Serializable;
import java.util.UUID;

/**
 * Domain model representing an Emergency SOS alert in BLACKOUT.
 */
public class EmergencyMessage implements Serializable {

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

    public EmergencyMessage() {
        this.messageId = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = this.createdAt + Constants.PACKET_TTL_MILLIS;
        this.status = Constants.STATUS_LOCAL_SAVED;
        this.locationStatus = Constants.LOC_STATUS_UNAVAILABLE;
        this.hopCount = 0;
    }

    public EmergencyMessage(String senderDeviceId, String senderCallsign, String emergencyCategory,
                            String description, String priority, Double latitude, Double longitude,
                            Float locationAccuracy, Long locationTimestamp, String status, String locationStatus) {
        this.messageId = UUID.randomUUID().toString();
        this.senderDeviceId = senderDeviceId;
        this.senderCallsign = senderCallsign;
        this.emergencyCategory = emergencyCategory;
        this.description = description;
        this.priority = priority;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationAccuracy = locationAccuracy;
        this.locationTimestamp = locationTimestamp;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = this.createdAt + Constants.PACKET_TTL_MILLIS;
        this.status = status;
        this.locationStatus = locationStatus;
        this.hopCount = 0;
    }

    // Convenience constructor for legacy/testing compatibility
    public EmergencyMessage(String senderDeviceId, String senderCallsign, String emergencyCategory,
                            String description, String priority, Double latitude, Double longitude, String status) {
        this(senderDeviceId, senderCallsign, emergencyCategory, description, priority, latitude, longitude,
                null, latitude != null ? System.currentTimeMillis() : null, status,
                latitude != null ? Constants.LOC_STATUS_ACQUIRED : Constants.LOC_STATUS_UNAVAILABLE);
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    // Alias for getMessageId()
    public String getId() {
        return messageId;
    }

    public void setId(String id) {
        this.messageId = id;
    }

    public String getSenderDeviceId() {
        return senderDeviceId;
    }

    public void setSenderDeviceId(String senderDeviceId) {
        this.senderDeviceId = senderDeviceId;
    }

    // Alias for getSenderDeviceId()
    public String getSenderId() {
        return senderDeviceId;
    }

    public void setSenderId(String senderId) {
        this.senderDeviceId = senderId;
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

    // Alias for getEmergencyCategory()
    public String getCategory() {
        return emergencyCategory;
    }

    public void setCategory(String category) {
        this.emergencyCategory = category;
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

    // Alias for getCreatedAt()
    public long getTimestamp() {
        return createdAt != null ? createdAt : 0L;
    }

    public void setTimestamp(long timestamp) {
        this.createdAt = timestamp;
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

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }
}
