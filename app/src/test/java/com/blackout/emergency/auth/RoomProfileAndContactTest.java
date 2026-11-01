package com.blackout.emergency.auth;

import com.blackout.emergency.database.EmergencyContactEntity;
import com.blackout.emergency.database.UserProfileEntity;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for Room database Version 5 entities:
 * UserProfileEntity and EmergencyContactEntity.
 */
public class RoomProfileAndContactTest {

    @Test
    public void testUserProfileEntityInitializationAndDefaults() {
        UserProfileEntity profile = new UserProfileEntity("usr-99", "Major John", "responder@blackout.org", "CITIZEN");
        profile.phone = "+919876543210";
        profile.bloodType = "O+";
        profile.country = "India";
        profile.preferredLanguage = "English";
        profile.medicalNotes = "Penicillin allergy, asthmatic";

        assertEquals("usr-99", profile.userId);
        assertEquals("responder@blackout.org", profile.email);
        assertEquals("Major John", profile.fullName);
        assertEquals("CITIZEN", profile.role);
        assertEquals("+919876543210", profile.phone);
        assertEquals("O+", profile.bloodType);
        assertEquals("India", profile.country);
        assertEquals("English", profile.preferredLanguage);
        assertEquals("Penicillin allergy, asthmatic", profile.medicalNotes);
        assertTrue(profile.updatedAt > 0);
    }

    @Test
    public void testEmergencyContactEntityPrimaryFlagAndFields() {
        long now = System.currentTimeMillis();
        EmergencyContactEntity contact = new EmergencyContactEntity(
                "cnt-01", "usr-99", "Sarah Connor", "+15551234567", "Spouse", true, "LOCAL", now
        );

        assertEquals("cnt-01", contact.id);
        assertEquals("usr-99", contact.userId);
        assertEquals("Sarah Connor", contact.contactName);
        assertEquals("+15551234567", contact.phoneNumber);
        assertEquals("Spouse", contact.relationship);
        assertTrue(contact.isPrimary);
        assertTrue(contact.updatedAt >= now);
    }

    @Test
    public void testEmergencyContactNonPrimary() {
        EmergencyContactEntity contact = new EmergencyContactEntity(
                "cnt-02", "usr-99", "Dr. Silberman", "+15559876543", "Doctor", false, "LOCAL", System.currentTimeMillis()
        );

        assertFalse(contact.isPrimary);
        assertEquals("Doctor", contact.relationship);
    }
}
