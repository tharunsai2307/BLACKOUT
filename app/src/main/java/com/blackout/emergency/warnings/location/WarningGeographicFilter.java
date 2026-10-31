package com.blackout.emergency.warnings.location;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.blackout.emergency.utils.Constants;
import com.blackout.emergency.warnings.models.PublicWarning;

/**
 * Geographical relevance evaluator for public emergency warnings (Phase 7).
 * Uses real hardware coordinates (when permitted) and optional manual region preferences
 * without fabricating coordinates or blocking reception if location is disabled.
 */
public class WarningGeographicFilter {

    public enum RelevanceLevel {
        IN_AFFECTED_ZONE,
        NEARBY_ZONE,
        GENERAL_BROADCAST,
        LOCATION_UNKNOWN
    }

    public static final String PREF_MANUAL_REGION = "pref_manual_region";

    private final Context context;
    private final SharedPreferences prefs;

    public WarningGeographicFilter(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Evaluates whether a warning is geographically relevant to the device.
     */
    @NonNull
    public RelevanceLevel evaluateRelevance(@NonNull PublicWarning warning,
                                           @Nullable Double deviceLat,
                                           @Nullable Double deviceLon) {
        String manualRegion = prefs != null ? prefs.getString(PREF_MANUAL_REGION, null) : null;
        return evaluateRelevance(warning, deviceLat, deviceLon, manualRegion);
    }

    /**
     * Pure static evaluation of warning relevance based on location, circular boundary and region preferences.
     */
    @NonNull
    public static RelevanceLevel evaluateRelevance(@NonNull PublicWarning warning,
                                                   @Nullable Double deviceLat,
                                                   @Nullable Double deviceLon,
                                                   @Nullable String manualRegion) {

        // 1. Check affected regions matching manual selection
        if (manualRegion != null && !manualRegion.trim().isEmpty() && warning.getAffectedRegions() != null) {
            for (String r : warning.getAffectedRegions()) {
                if (r != null && r.equalsIgnoreCase(manualRegion.trim())) {
                    return RelevanceLevel.IN_AFFECTED_ZONE;
                }
            }
        }

        // 2. Check circular coordinate distance if both device and warning coordinates exist
        if (deviceLat != null && deviceLon != null && warning.getLatitude() != null && warning.getLongitude() != null) {
            double distanceMeters = computeHaversineDistanceMeters(
                    deviceLat, deviceLon,
                    warning.getLatitude(), warning.getLongitude()
            );

            double radius = warning.getRadiusMeters() != null ? warning.getRadiusMeters() : 10000.0; // default 10km

            if (distanceMeters <= radius) {
                return RelevanceLevel.IN_AFFECTED_ZONE;
            } else if (distanceMeters <= radius * 1.5) {
                return RelevanceLevel.NEARBY_ZONE;
            }
        }

        // 3. If warning has no coordinates or regions, it is a general broadcast for all users
        if ((warning.getLatitude() == null || warning.getLongitude() == null)
                && (warning.getAffectedRegions() == null || warning.getAffectedRegions().isEmpty())) {
            return RelevanceLevel.GENERAL_BROADCAST;
        }

        // 4. If device location is unknown
        if (deviceLat == null || deviceLon == null) {
            return RelevanceLevel.LOCATION_UNKNOWN;
        }

        return RelevanceLevel.GENERAL_BROADCAST;
    }

    /**
     * Haversine formula for calculating distance in meters between two coordinates.
     */
    public static double computeHaversineDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000.0; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public void setManualRegion(@Nullable String region) {
        prefs.edit().putString(PREF_MANUAL_REGION, region).apply();
    }

    @Nullable
    public String getManualRegion() {
        return prefs.getString(PREF_MANUAL_REGION, null);
    }
}
