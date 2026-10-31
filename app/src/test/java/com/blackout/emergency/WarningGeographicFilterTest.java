package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.blackout.emergency.warnings.location.WarningGeographicFilter;
import com.blackout.emergency.warnings.location.WarningGeographicFilter.RelevanceLevel;
import com.blackout.emergency.warnings.models.PublicWarning;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/**
 * Unit tests for WarningGeographicFilter:
 * - Haversine distance calculations
 * - Circular geofence boundary checks (affected zone vs nearby zone vs outside)
 * - Administrative region text matching
 * - Graceful fallback when device coordinates are unavailable
 * - General broadcast warnings
 */
public class WarningGeographicFilterTest {

    private static final double EPSILON_METERS = 50.0; // Distance tolerance

    @Test
    public void testHaversineDistanceKnownCoordinates() {
        // Test identical points
        double distZero = WarningGeographicFilter.computeHaversineDistanceMeters(13.0827, 80.2707, 13.0827, 80.2707);
        assertEquals(0.0, distZero, 0.001);

        // London (51.5074, -0.1278) to Paris (48.8566, 2.3522) ~ 343.5 km
        double distLondonParis = WarningGeographicFilter.computeHaversineDistanceMeters(
                51.5074, -0.1278,
                48.8566, 2.3522
        );
        assertTrue("Distance should be approx 343-344 km, got " + distLondonParis,
                distLondonParis > 340000 && distLondonParis < 346000);

        // Close proximity: ~3.8 km in Chennai
        double distLocal = WarningGeographicFilter.computeHaversineDistanceMeters(
                13.0827, 80.2707,
                13.0500, 80.2824
        );
        assertTrue("Local distance should be ~3.8 km, got " + distLocal,
                distLocal > 3500 && distLocal < 4100);
    }

    private PublicWarning buildWarningWithGeo(Double lat, Double lon, Double radiusMeters, String... regions) {
        return new PublicWarning(
                "warn-geo-1",
                1,
                1,
                "Geo Alert",
                "Description",
                PublicWarning.CATEGORY_CYCLONE,
                PublicWarning.SEVERITY_WARNING,
                "Emergency Authority",
                1700000000000L,
                1700000000000L,
                1800000000000L,
                regions != null && regions.length > 0 ? Arrays.asList(regions) : Collections.emptyList(),
                lat,
                lon,
                radiusMeters,
                "en",
                PublicWarning.STATUS_PUBLISHED,
                "KEY-ROOT",
                "Ed25519",
                "sig",
                null
        );
    }

    @Test
    public void testDeviceInsideRadiusIsAffectedZone() {
        // Warning center: 13.0827, 80.2707 with 5000m radius
        PublicWarning warning = buildWarningWithGeo(13.0827, 80.2707, 5000.0, "Coastal Zone");

        // Device located ~50 meters away
        RelevanceLevel level = WarningGeographicFilter.evaluateRelevance(
                warning,
                13.0830,
                80.2710,
                null
        );
        assertEquals(RelevanceLevel.IN_AFFECTED_ZONE, level);
    }

    @Test
    public void testDeviceInNearbyZone() {
        // Warning center: 13.0827, 80.2707 with 3000m radius (1.5x = 4500m)
        PublicWarning warning = buildWarningWithGeo(13.0827, 80.2707, 3000.0, "Coastal Zone");

        // Point roughly 3.8 km away (between 3000m and 4500m)
        RelevanceLevel level = WarningGeographicFilter.evaluateRelevance(
                warning,
                13.0500,
                80.2824,
                null
        );
        assertEquals(RelevanceLevel.NEARBY_ZONE, level);
    }

    @Test
    public void testDeviceOutsideRadiusIsGeneralBroadcast() {
        // Warning center: 13.0827, 80.2707 with 1000m radius
        PublicWarning warning = buildWarningWithGeo(13.0827, 80.2707, 1000.0, "Sector 1");

        // Point ~3.8 km away (> 1500m)
        RelevanceLevel level = WarningGeographicFilter.evaluateRelevance(
                warning,
                13.0500,
                80.2824,
                null
        );
        assertEquals(RelevanceLevel.GENERAL_BROADCAST, level);
    }

    @Test
    public void testManualRegionMatchTakesPrecedence() {
        // Device is geographically outside or coordinates unknown
        PublicWarning warning = buildWarningWithGeo(13.0827, 80.2707, 1000.0, "Riverside", "East Delta");

        RelevanceLevel level = WarningGeographicFilter.evaluateRelevance(
                warning,
                20.0, // Far away
                30.0,
                "East Delta"
        );
        assertEquals(RelevanceLevel.IN_AFFECTED_ZONE, level);
    }

    @Test
    public void testLocationUnknownWhenNoDeviceCoordinatesAndNoRegionMatch() {
        PublicWarning warning = buildWarningWithGeo(13.0827, 80.2707, 5000.0, "North Basin");

        // Device coordinates null, no manual region
        RelevanceLevel level = WarningGeographicFilter.evaluateRelevance(
                warning,
                null,
                null,
                null
        );
        assertEquals(RelevanceLevel.LOCATION_UNKNOWN, level);
    }

    @Test
    public void testGeneralBroadcastWarningWithoutCoordsOrRegions() {
        // Nationwide broadcast with no geometry
        PublicWarning warning = buildWarningWithGeo(null, null, null);

        RelevanceLevel level = WarningGeographicFilter.evaluateRelevance(
                warning,
                13.0827,
                80.2707,
                "Any Region"
        );
        assertEquals(RelevanceLevel.GENERAL_BROADCAST, level);
    }
}
