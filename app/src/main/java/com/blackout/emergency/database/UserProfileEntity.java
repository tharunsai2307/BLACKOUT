package com.blackout.emergency.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity representing locally cached citizen user profile information.
 * Allows instant offline profile rendering and personalization without internet.
 */
@Entity(tableName = "user_profiles")
public class UserProfileEntity {

    @PrimaryKey
    @NonNull
    public String userId; // UUID matching auth.users

    @NonNull
    public String fullName;

    @NonNull
    public String email;

    public String phoneNumber;

    public String countryCode;

    public String countryRegion;

    public String profilePhotoUrl;

    public String preferredLanguage;

    public String medicalNotes;

    public boolean isProfileCompleted;

    @NonNull
    public String role; // CITIZEN, SUPER_ADMIN, etc.

    public long updatedAt;

    @androidx.room.Ignore
    public String phone;

    @androidx.room.Ignore
    public String bloodType;

    @androidx.room.Ignore
    public String country;

    public UserProfileEntity(@NonNull String userId, @NonNull String fullName, @NonNull String email,
                             String phoneNumber, String countryCode, String countryRegion,
                             String profilePhotoUrl, String preferredLanguage, String medicalNotes,
                             boolean isProfileCompleted, @NonNull String role, long updatedAt) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.countryCode = countryCode;
        this.countryRegion = countryRegion;
        this.profilePhotoUrl = profilePhotoUrl;
        this.preferredLanguage = preferredLanguage;
        this.medicalNotes = medicalNotes;
        this.isProfileCompleted = isProfileCompleted;
        this.role = role;
        this.updatedAt = updatedAt;
    }

    @androidx.room.Ignore
    public UserProfileEntity(@NonNull String userId, @NonNull String fullName, @NonNull String email, @NonNull String role) {
        this(userId, fullName, email, null, "+91", null, null, "en", null, false, role, System.currentTimeMillis());
    }

    @NonNull
    public String getUserId() {
        return userId;
    }

    public void setUserId(@NonNull String userId) {
        this.userId = userId;
    }

    @NonNull
    public String getFullName() {
        return fullName;
    }

    public void setFullName(@NonNull String fullName) {
        this.fullName = fullName;
    }

    @NonNull
    public String getEmail() {
        return email;
    }

    public void setEmail(@NonNull String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCountryRegion() {
        return countryRegion;
    }

    public void setCountryRegion(String countryRegion) {
        this.countryRegion = countryRegion;
    }

    public String getProfilePhotoUrl() {
        return profilePhotoUrl;
    }

    public void setProfilePhotoUrl(String profilePhotoUrl) {
        this.profilePhotoUrl = profilePhotoUrl;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public String getMedicalNotes() {
        return medicalNotes;
    }

    public void setMedicalNotes(String medicalNotes) {
        this.medicalNotes = medicalNotes;
    }

    public boolean isProfileCompleted() {
        return isProfileCompleted;
    }

    public void setProfileCompleted(boolean profileCompleted) {
        isProfileCompleted = profileCompleted;
    }

    @NonNull
    public String getRole() {
        return role;
    }

    public void setRole(@NonNull String role) {
        this.role = role;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
