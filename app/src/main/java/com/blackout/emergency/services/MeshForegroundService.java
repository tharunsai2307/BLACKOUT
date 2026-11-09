package com.blackout.emergency.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.blackout.emergency.R;
import com.blackout.emergency.activities.MainActivity;
import com.blackout.emergency.gateway.InternetGatewayService;
import com.blackout.emergency.nearby.NearbyConnectionManager;
import com.blackout.emergency.nearby.NearbyDevice;
import com.blackout.emergency.utils.Constants;

/**
 * Autonomous Mesh Foreground Service (Phase 12 BitChat Architecture).
 * <p>
 * Maintains continuous P2P_CLUSTER advertising and discovery in the background,
 * executes adaptive duty-cycling (longer pauses in background to prevent battery drain),
 * presents a low-importance persistent notification detailing active mesh topology,
 * and survives background memory pressure via START_STICKY.
 */
public class MeshForegroundService extends Service {

    private static final String TAG = "MeshForegroundService";

    public static final String ACTION_START = "com.blackout.emergency.action.START_MESH";
    public static final String ACTION_STOP = "com.blackout.emergency.action.STOP_MESH";
    public static final String ACTION_UPDATE_DUTY_CYCLE = "com.blackout.emergency.action.UPDATE_DUTY_CYCLE";

    private static volatile boolean isServiceRunning = false;
    private static volatile boolean isAppInForeground = true;

    private final Handler dutyCycleHandler = new Handler(Looper.getMainLooper());
    private boolean isScanningActive = false;
    private NotificationManager notificationManager;

    private final InternetGatewayService.ConnectivityListener connectivityListener =
            (isConnected, networkType) -> updateNotification();

    private final NearbyConnectionManager.NearbyEventListener nearbyEventListener =
            new NearbyConnectionManager.SimpleNearbyEventListener() {
                @Override
                public void onConnectionResult(@NonNull String endpointId, boolean isConnected, @NonNull String message) {
                    updateNotification();
                }

                @Override
                public void onDisconnected(@NonNull String endpointId) {
                    updateNotification();
                }

                @Override
                public void onDiscoveryStateChanged(boolean isDiscovering) {
                    updateNotification();
                }
            };

    private final Runnable dutyCycleRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isServiceRunning) {
                return;
            }

            NearbyConnectionManager ncm = NearbyConnectionManager.getInstance(getApplicationContext());
            long nextDelay;

            if (isScanningActive) {
                // Pause scanning to conserve radio energy; keep advertising active
                ncm.stopDiscovery();
                isScanningActive = false;
                nextDelay = isAppInForeground
                        ? Constants.DISCOVERY_FOREGROUND_PAUSE_MS
                        : Constants.DISCOVERY_BACKGROUND_PAUSE_MS;
            } else {
                // Resume scanning only if under peer capacity
                if (ncm.getConnectedPeersCount() < Constants.MAX_SIMULTANEOUS_PEERS) {
                    ncm.startDiscovery();
                    isScanningActive = true;
                }
                nextDelay = isAppInForeground
                        ? Constants.DISCOVERY_FOREGROUND_ACTIVE_MS
                        : Constants.DISCOVERY_BACKGROUND_ACTIVE_MS;
            }

            dutyCycleHandler.postDelayed(this, nextDelay);
        }
    };

    public static boolean isRunning() {
        return isServiceRunning;
    }

    public static boolean isAppInForeground() {
        return isAppInForeground;
    }

    public static void setAppInForeground(boolean inForeground) {
        isAppInForeground = inForeground;
    }

    public static void start(@NonNull Context context) {
        Intent intent = new Intent(context, MeshForegroundService.class);
        intent.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(@NonNull Context context) {
        Intent intent = new Intent(context, MeshForegroundService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();

        InternetGatewayService.getInstance(this).registerListener(connectivityListener);
        NearbyConnectionManager.getInstance(this).registerListener(nearbyEventListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopAutonomousService();
            return START_NOT_STICKY;
        }

        Notification notification = buildNotification();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    Constants.NOTIFICATION_ID_MESH,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(Constants.NOTIFICATION_ID_MESH, notification);
        }

        isServiceRunning = true;

        // Start autonomous mesh discovery and advertising
        NearbyConnectionManager ncm = NearbyConnectionManager.getInstance(this);
        ncm.startAutonomousMesh();
        isScanningActive = true;

        // Schedule adaptive duty cycle
        dutyCycleHandler.removeCallbacks(dutyCycleRunnable);
        long activeMs = isAppInForeground
                ? Constants.DISCOVERY_FOREGROUND_ACTIVE_MS
                : Constants.DISCOVERY_BACKGROUND_ACTIVE_MS;
        dutyCycleHandler.postDelayed(dutyCycleRunnable, activeMs);

        updateNotification();
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopAutonomousService();
        super.onDestroy();
    }

    private void stopAutonomousService() {
        isServiceRunning = false;
        dutyCycleHandler.removeCallbacks(dutyCycleRunnable);

        try {
            InternetGatewayService.getInstance(this).unregisterListener(connectivityListener);
        } catch (Exception ignored) {}

        try {
            NearbyConnectionManager.getInstance(this).unregisterListener(nearbyEventListener);
        } catch (Exception ignored) {}

        try {
            NearbyConnectionManager.getInstance(this).stopAutonomousMesh();
        } catch (Exception ignored) {}

        stopForeground(true);
        stopSelf();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_MESH,
                    "BLACKOUT Mesh Operations",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Autonomous offline emergency peer discovery and store-and-forward relay");
            channel.setShowBadge(false);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        NearbyConnectionManager ncm = NearbyConnectionManager.getInstance(this);
        int peerCount = ncm.getConnectedPeersCount();
        boolean hasInternet = InternetGatewayService.getInstance(this).isInternetAvailable();
        String netType = InternetGatewayService.getInstance(this).getCurrentNetworkType();

        String statusSummary;
        if (hasInternet) {
            statusSummary = "Gateway Active (" + netType + ") • " + peerCount + " peer(s) connected";
        } else if (peerCount > 0) {
            statusSummary = "Offline Mesh Active • " + peerCount + " peer(s) connected";
        } else {
            statusSummary = "Mesh Network Active • Scanning for emergency peers...";
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                        : PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_MESH)
                .setSmallIcon(R.drawable.ic_radio_mesh)
                .setContentTitle("BLACKOUT Emergency Mesh")
                .setContentText(statusSummary)
                .setSubText(ncm.getLocalCallsign())
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void updateNotification() {
        if (!isServiceRunning || notificationManager == null) {
            return;
        }
        try {
            notificationManager.notify(Constants.NOTIFICATION_ID_MESH, buildNotification());
        } catch (Exception ignored) {}
    }
}

