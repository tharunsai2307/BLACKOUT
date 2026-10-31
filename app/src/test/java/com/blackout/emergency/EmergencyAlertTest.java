package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.blackout.emergency.models.EmergencyAlert;
import com.blackout.emergency.utils.Constants;
import org.junit.Test;

public class EmergencyAlertTest {

    @Test
    public void testEmergencyAlertCreation() {
        long now = System.currentTimeMillis();
        long expiry = now + 3600000;

        EmergencyAlert alert = new EmergencyAlert(
                "Tsunami Warning",
                Constants.CATEGORY_DISASTER,
                Constants.SEVERITY_IMMEDIATE,
                "National Weather Service",
                now,
                expiry,
                "Coastal Zone A",
                "Evacuate to elevated ground immediately.",
                true
        );

        assertNotNull(alert.getId());
        assertEquals("Tsunami Warning", alert.getTitle());
        assertEquals(Constants.CATEGORY_DISASTER, alert.getCategory());
        assertEquals(Constants.SEVERITY_IMMEDIATE, alert.getSeverity());
        assertEquals("National Weather Service", alert.getIssuingAuthority());
        assertEquals(now, alert.getPublishedTime());
        assertEquals(expiry, alert.getExpiryTime());
        assertEquals("Coastal Zone A", alert.getAffectedArea());
        assertTrue(alert.isVerified());
    }
}
