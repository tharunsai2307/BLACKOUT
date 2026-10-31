package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.relay.RelayPolicy;
import com.blackout.emergency.utils.Constants;
import org.junit.Test;

/**
 * Unit tests verifying store-carry-and-forward relay policies:
 * hop limits, expiration checks, priority weights, and backoff calculations.
 */
public class RelayPolicyTest {

    @Test
    public void testIsEligibleForRelayValidMessage() {
        EmergencyMessage msg = new EmergencyMessage(
                "DEV-1", "NODE-1", Constants.CATEGORY_MEDICAL,
                "Patient requires oxygen support.", Constants.PRIORITY_CRITICAL,
                10.0, 20.0, Constants.STATUS_LOCAL_SAVED
        );
        msg.setHopCount(0);
        assertTrue(RelayPolicy.isEligibleForRelay(msg));

        // Up to max hops - 1 is eligible
        msg.setHopCount(RelayPolicy.DEFAULT_MAX_HOPS - 1);
        assertTrue(RelayPolicy.isEligibleForRelay(msg));
    }

    @Test
    public void testIsEligibleForRelayMaxHopsReached() {
        EmergencyMessage msg = new EmergencyMessage(
                "DEV-1", "NODE-1", Constants.CATEGORY_MEDICAL,
                "Patient requires oxygen support.", Constants.PRIORITY_CRITICAL,
                10.0, 20.0, Constants.STATUS_LOCAL_SAVED
        );
        msg.setHopCount(RelayPolicy.DEFAULT_MAX_HOPS);
        assertFalse(RelayPolicy.isEligibleForRelay(msg));

        msg.setHopCount(RelayPolicy.DEFAULT_MAX_HOPS + 3);
        assertFalse(RelayPolicy.isEligibleForRelay(msg));
    }

    @Test
    public void testIsEligibleForRelayExpiredMessage() {
        EmergencyMessage msg = new EmergencyMessage(
                "DEV-1", "NODE-1", Constants.CATEGORY_FIRE,
                "Warehouse blaze.", Constants.PRIORITY_HIGH,
                10.0, 20.0, Constants.STATUS_LOCAL_SAVED
        );
        msg.setHopCount(1);
        // Expired in past
        msg.setExpiresAt(System.currentTimeMillis() - 1000L);
        assertFalse(RelayPolicy.isEligibleForRelay(msg));
    }

    @Test
    public void testIsEligibleForRelayNullMessage() {
        assertFalse(RelayPolicy.isEligibleForRelay(null));
    }

    @Test
    public void testCalculateBackoffBoundedExponential() {
        long b0 = RelayPolicy.calculateBackoffMillis(0);
        assertEquals(30 * 1000L, b0);

        long b1 = RelayPolicy.calculateBackoffMillis(1);
        assertEquals(30 * 1000L, b1);

        long b2 = RelayPolicy.calculateBackoffMillis(2);
        assertEquals(60 * 1000L, b2);

        long b3 = RelayPolicy.calculateBackoffMillis(3);
        assertEquals(120 * 1000L, b3);

        long bMax = RelayPolicy.calculateBackoffMillis(10);
        assertEquals(RelayPolicy.MAX_RETRY_BACKOFF_MILLIS, bMax);
    }

    @Test
    public void testGetPriorityWeight() {
        assertTrue(RelayPolicy.getPriorityWeight(Constants.PRIORITY_CRITICAL) > RelayPolicy.getPriorityWeight(Constants.PRIORITY_HIGH));
        assertTrue(RelayPolicy.getPriorityWeight(Constants.PRIORITY_HIGH) > RelayPolicy.getPriorityWeight(Constants.PRIORITY_MODERATE));
        assertTrue(RelayPolicy.getPriorityWeight(Constants.PRIORITY_MODERATE) > RelayPolicy.getPriorityWeight(Constants.PRIORITY_LOW));
        assertEquals(1, RelayPolicy.getPriorityWeight(null));
    }
}
