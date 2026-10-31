package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.blackout.emergency.services.LocationService;
import com.blackout.emergency.utils.Constants;
import org.junit.Test;

/**
 * Unit tests verifying LocationService.LocationResult state contracts
 * and GPS coordinate integrity without fake coordinate generation.
 */
public class LocationResultTest {

    @Test
    public void testAcquiredFreshGpsLocationResult() {
        long now = System.currentTimeMillis();
        LocationService.LocationResult result = new LocationService.LocationResult(
                37.7749,
                -122.4194,
                5.0f,
                now,
                Constants.LOC_STATUS_ACQUIRED,
                true,
                "gps"
        );

        assertTrue(result.hasCoordinates());
        assertTrue(result.isFreshGps());
        assertEquals(Double.valueOf(37.7749), result.getLatitude());
        assertEquals(Double.valueOf(-122.4194), result.getLongitude());
        assertEquals(Float.valueOf(5.0f), result.getAccuracy());
        assertEquals(Long.valueOf(now), result.getTimestamp());
        assertEquals(Constants.LOC_STATUS_ACQUIRED, result.getStatus());
        assertEquals("gps", result.getProvider());
    }

    @Test
    public void testCachedLocationResult() {
        long past = System.currentTimeMillis() - 60000;
        LocationService.LocationResult result = new LocationService.LocationResult(
                37.7749,
                -122.4194,
                15.0f,
                past,
                Constants.LOC_STATUS_CACHED,
                false,
                "gps"
        );

        assertTrue(result.hasCoordinates());
        assertFalse(result.isFreshGps());
        assertEquals(Constants.LOC_STATUS_CACHED, result.getStatus());
    }

    @Test
    public void testUnavailableLocationResult() {
        LocationService.LocationResult result = LocationService.LocationResult.unavailable(
                Constants.LOC_STATUS_UNAVAILABLE
        );

        assertFalse(result.hasCoordinates());
        assertFalse(result.isFreshGps());
        assertNull(result.getLatitude());
        assertNull(result.getLongitude());
        assertNull(result.getAccuracy());
        assertNull(result.getTimestamp());
        assertEquals(Constants.LOC_STATUS_UNAVAILABLE, result.getStatus());
    }

    @Test
    public void testDeniedLocationResult() {
        LocationService.LocationResult result = LocationService.LocationResult.unavailable(
                Constants.LOC_STATUS_DENIED
        );

        assertFalse(result.hasCoordinates());
        assertEquals(Constants.LOC_STATUS_DENIED, result.getStatus());
    }

    @Test
    public void testDisabledLocationResult() {
        LocationService.LocationResult result = LocationService.LocationResult.unavailable(
                Constants.LOC_STATUS_DISABLED
        );

        assertFalse(result.hasCoordinates());
        assertEquals(Constants.LOC_STATUS_DISABLED, result.getStatus());
    }
}
