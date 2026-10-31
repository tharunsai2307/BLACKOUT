package com.blackout.emergency.models;

import java.io.Serializable;
import java.util.UUID;

/**
 * Represents a public safety alert or official civil warning broadcast over the mesh network.
 */
public class EmergencyAlert implements Serializable {

    private String id;
    private String title;
    private String category;
    private String severity;
    private String issuingAuthority;
    private long publishedTime;
    private long expiryTime;
    private String affectedArea;
    private String instructions;
    private boolean isVerified;

    public EmergencyAlert() {
        this.id = UUID.randomUUID().toString();
        this.publishedTime = System.currentTimeMillis();
        this.expiryTime = this.publishedTime + (24 * 60 * 60 * 1000L);
        this.isVerified = true;
    }

    public EmergencyAlert(String title, String category, String severity,
                          String issuingAuthority, long publishedTime, long expiryTime,
                          String affectedArea, String instructions, boolean isVerified) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.category = category;
        this.severity = severity;
        this.issuingAuthority = issuingAuthority;
        this.publishedTime = publishedTime;
        this.expiryTime = expiryTime;
        this.affectedArea = affectedArea;
        this.instructions = instructions;
        this.isVerified = isVerified;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getIssuingAuthority() {
        return issuingAuthority;
    }

    public void setIssuingAuthority(String issuingAuthority) {
        this.issuingAuthority = issuingAuthority;
    }

    public long getPublishedTime() {
        return publishedTime;
    }

    public void setPublishedTime(long publishedTime) {
        this.publishedTime = publishedTime;
    }

    public long getExpiryTime() {
        return expiryTime;
    }

    public void setExpiryTime(long expiryTime) {
        this.expiryTime = expiryTime;
    }

    public String getAffectedArea() {
        return affectedArea;
    }

    public void setAffectedArea(String affectedArea) {
        this.affectedArea = affectedArea;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
    }
}
