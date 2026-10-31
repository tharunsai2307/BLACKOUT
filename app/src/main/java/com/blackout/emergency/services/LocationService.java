package com.blackout.emergency.services;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.blackout.emergency.utils.Constants;

/**
 * Service managing direct hardware GPS location capture for offline emergency SOS alerts.
 * Utilizes LocationManager with GPS_PROVIDER to acquire genuine satellite coordinates
 * without reliance on active internet or external network infrastructure.
 */
public class LocationService {

    private static final long GPS_TIMEOUT_MS = 15000L; // 15-second timeout for GPS fix
    private static volatile LocationService instance;

    private final Context context;
    private final LocationManager locationManager;
    private final Handler mainHandler;

    private LocationListener activeListener;
    private Runnable timeoutRunnable;
    private boolean isCapturing = false;

    /**
     * Immutable value object holding the outcome of a location acquisition attempt.
     */
    public static class LocationResult {
        private final Double latitude;
        private final Double longitude;
        private final Float accuracy;
        private final Long timestamp;
        private final String status;
        private final boolean isFreshGps;
        private final String provider;

        public LocationResult(@Nullable Double latitude, @Nullable Double longitude,
                              @Nullable Float accuracy, @Nullable Long timestamp,
                              @NonNull String status, boolean isFreshGps, @Nullable String provider) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.accuracy = accuracy;
            this.timestamp = timestamp;
            this.status = status;
            this.isFreshGps = isFreshGps;
            this.provider = provider;
        }

        public static LocationResult unavailable(@NonNull String status) {
            return new LocationResult(null, null, null, null, status, false, null);
        }

        public boolean hasCoordinates() {
            return latitude != null && longitude != null;
        }

        @Nullable
        public Double getLatitude() {
            return latitude;
        }

        @Nullable
        public Double getLongitude() {
            return longitude;
        }

        @Nullable
        public Float getAccuracy() {
            return accuracy;
        }

        @Nullable
        public Long getTimestamp() {
            return timestamp;
        }

        @NonNull
        public String getStatus() {
            return status;
        }

        public boolean isFreshGps() {
            return isFreshGps;
        }

        @Nullable
        public String getProvider() {
            return provider;
        }
    }

    public interface LocationCallback {
        void onStatusUpdate(@NonNull String statusMessage);
        void onLocationResolved(@NonNull LocationResult result);
    }

    private LocationService(Context context) {
        this.context = context.getApplicationContext();
        this.locationManager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized LocationService getInstance(Context context) {
        if (instance == null) {
            instance = new LocationService(context);
        }
        return instance;
    }

    /**
     * Checks whether location permissions have been granted.
     */
    public boolean hasLocationPermission() {
        int fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION);
        int coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION);
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Checks if GPS or any location provider is enabled on the device.
     */
    public boolean isLocationServicesEnabled() {
        if (locationManager == null) {
            return false;
        }
        boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        return gps || network;
    }

    /**
     * Checks specifically if the satellite GPS provider is enabled.
     */
    public boolean isGpsEnabled() {
        if (locationManager == null) {
            return false;
        }
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    /**
     * Begins genuine device location capture using GPS hardware.
     * Enforces timeout and falls back to last-known location if fresh GPS fix is delayed.
     */
    public void captureCurrentLocation(@NonNull final LocationCallback callback) {
        cancelLocationCapture();

        // 1. Permission check
        if (!hasLocationPermission()) {
            callback.onStatusUpdate(Constants.LOC_STATUS_DENIED);
            callback.onLocationResolved(LocationResult.unavailable(Constants.LOC_STATUS_DENIED));
            return;
        }

        // 2. Hardware / Provider check
        if (locationManager == null || !isLocationServicesEnabled()) {
            callback.onStatusUpdate(Constants.LOC_STATUS_DISABLED);
            callback.onLocationResolved(LocationResult.unavailable(Constants.LOC_STATUS_DISABLED));
            return;
        }

        // 3. Indicate capture in progress
        callback.onStatusUpdate(Constants.LOC_STATUS_GETTING);
        isCapturing = true;

        // 4. Setup location listener
        activeListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                // Fresh location acquired
                finishCapture();
                LocationResult result = new LocationResult(
                        location.getLatitude(),
                        location.getLongitude(),
                        location.hasAccuracy() ? location.getAccuracy() : null,
                        location.getTime() > 0 ? location.getTime() : System.currentTimeMillis(),
                        Constants.LOC_STATUS_ACQUIRED,
                        true,
                        location.getProvider()
                );
                callback.onStatusUpdate(Constants.LOC_STATUS_ACQUIRED);
                callback.onLocationResolved(result);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(@NonNull String provider) {}

            @Override
            public void onProviderDisabled(@NonNull String provider) {}
        };

        // 5. Register with GPS_PROVIDER (and NETWORK_PROVIDER fallback)
        try {
            boolean requested = false;
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L,
                        0f,
                        activeListener,
                        Looper.getMainLooper()
                );
                requested = true;
            }

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        1000L,
                        0f,
                        activeListener,
                        Looper.getMainLooper()
                );
                requested = true;
            }

            if (!requested) {
                finishCapture();
                callback.onStatusUpdate(Constants.LOC_STATUS_UNAVAILABLE);
                callback.onLocationResolved(LocationResult.unavailable(Constants.LOC_STATUS_UNAVAILABLE));
                return;
            }
        } catch (SecurityException se) {
            finishCapture();
            callback.onStatusUpdate(Constants.LOC_STATUS_DENIED);
            callback.onLocationResolved(LocationResult.unavailable(Constants.LOC_STATUS_DENIED));
            return;
        }

        // 6. Setup timeout handler for offline/poor signal environments
        timeoutRunnable = () -> {
            if (!isCapturing) {
                return;
            }
            finishCapture();

            // Attempt to retrieve last known location honestly
            Location lastKnown = getBestLastKnownLocation();
            if (lastKnown != null) {
                LocationResult cachedResult = new LocationResult(
                        lastKnown.getLatitude(),
                        lastKnown.getLongitude(),
                        lastKnown.hasAccuracy() ? lastKnown.getAccuracy() : null,
                        lastKnown.getTime() > 0 ? lastKnown.getTime() : System.currentTimeMillis(),
                        Constants.LOC_STATUS_CACHED,
                        false,
                        lastKnown.getProvider()
                );
                callback.onStatusUpdate(Constants.LOC_STATUS_CACHED);
                callback.onLocationResolved(cachedResult);
            } else {
                callback.onStatusUpdate(Constants.LOC_STATUS_UNAVAILABLE);
                callback.onLocationResolved(LocationResult.unavailable(Constants.LOC_STATUS_UNAVAILABLE));
            }
        };

        mainHandler.postDelayed(timeoutRunnable, GPS_TIMEOUT_MS);
    }

    /**
     * Retrieves the most recent last-known location across GPS and Network providers.
     */
    @Nullable
    public Location getBestLastKnownLocation() {
        if (!hasLocationPermission() || locationManager == null) {
            return null;
        }

        Location bestLocation = null;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Location gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (gpsLoc != null) {
                    bestLocation = gpsLoc;
                }
            }

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                Location netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (netLoc != null) {
                    if (bestLocation == null || netLoc.getTime() > bestLocation.getTime()) {
                        bestLocation = netLoc;
                    }
                }
            }
        } catch (SecurityException ignored) {
            return null;
        }

        return bestLocation;
    }

    /**
     * Cancels any ongoing location capture and cleans up listeners.
     */
    public void cancelLocationCapture() {
        finishCapture();
    }

    private void finishCapture() {
        isCapturing = false;
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
        if (activeListener != null && locationManager != null) {
            try {
                locationManager.removeUpdates(activeListener);
            } catch (SecurityException ignored) {
            }
            activeListener = null;
        }
    }
}
