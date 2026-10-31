package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.blackout.emergency.nearby.NearbyDevice;
import org.junit.Test;

/**
 * Unit tests verifying NearbyDevice model, state transitions, and equivalence.
 */
public class NearbyDeviceTest {

    @Test
    public void testNearbyDeviceInitialState() {
        NearbyDevice device = new NearbyDevice("endpoint-123", "NODE-ALPHA");

        assertEquals("endpoint-123", device.getEndpointId());
        assertEquals("NODE-ALPHA", device.getEndpointName());
        assertEquals(NearbyDevice.ConnectionState.DISCOVERED, device.getState());
        assertFalse(device.isConnected());
        assertFalse(device.isPendingApproval());
        assertTrue(device.getLastSeenTimestamp() > 0);
    }

    @Test
    public void testNearbyDeviceDefaultNameFallback() {
        NearbyDevice device = new NearbyDevice("endpoint-456", "   ");
        assertNotNull(device.getEndpointName());
        assertEquals("BLACKOUT-NODE", device.getEndpointName());
    }

    @Test
    public void testNearbyDeviceStateTransitions() {
        NearbyDevice device = new NearbyDevice("endpoint-789", "NODE-BETA");

        device.setState(NearbyDevice.ConnectionState.CONNECTING);
        assertEquals(NearbyDevice.ConnectionState.CONNECTING, device.getState());
        assertFalse(device.isConnected());

        device.setState(NearbyDevice.ConnectionState.REQUESTED_INCOMING);
        device.setAuthDigits("9876");
        assertTrue(device.isPendingApproval());
        assertEquals("9876", device.getAuthDigits());

        device.setState(NearbyDevice.ConnectionState.CONNECTED);
        assertTrue(device.isConnected());
        assertFalse(device.isPendingApproval());

        device.setState(NearbyDevice.ConnectionState.DISCONNECTED);
        assertFalse(device.isConnected());
    }

    @Test
    public void testNearbyDeviceEquality() {
        NearbyDevice dev1 = new NearbyDevice("same-endpoint", "NODE-1");
        NearbyDevice dev2 = new NearbyDevice("same-endpoint", "NODE-2");
        NearbyDevice dev3 = new NearbyDevice("different-endpoint", "NODE-1");

        assertEquals(dev1, dev2);
        assertFalse(dev1.equals(dev3));
        assertEquals(dev1.hashCode(), dev2.hashCode());
    }
}
