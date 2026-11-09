package com.blackout.emergency.nearby;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.utils.Constants;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Core manager for Google's Nearby Connections API (P2P_CLUSTER strategy).
 * Handles advertising, discovery, connection negotiation with explicit user approval
 * or autonomous peering (Phase 12), disconnection management, and raw byte payload transfers.
 */
public class NearbyConnectionManager {

    public interface NearbyEventListener {
        void onAdvertisingStateChanged(boolean isAdvertising);

        void onDiscoveryStateChanged(boolean isDiscovering);

        void onEndpointDiscovered(@NonNull NearbyDevice device);

        void onEndpointLost(@NonNull String endpointId);

        void onConnectionInitiated(@NonNull NearbyDevice device, @NonNull String authDigits);

        void onConnectionResult(@NonNull String endpointId, boolean isConnected, @NonNull String message);

        void onDisconnected(@NonNull String endpointId);

        void onPayloadReceived(@NonNull String endpointId, @NonNull byte[] payloadBytes);

        void onPayloadTransferUpdate(@NonNull String endpointId, long payloadId, int status);

        void onError(@NonNull String errorMessage);
    }

    /**
     * Empty-body adapter class for {@link NearbyEventListener} allowing observers
     * to selectively override only the callbacks they care about.
     */
    public static class SimpleNearbyEventListener implements NearbyEventListener {
        @Override public void onAdvertisingStateChanged(boolean isAdvertising) {}
        @Override public void onDiscoveryStateChanged(boolean isDiscovering) {}
        @Override public void onEndpointDiscovered(@NonNull NearbyDevice device) {}
        @Override public void onEndpointLost(@NonNull String endpointId) {}
        @Override public void onConnectionInitiated(@NonNull NearbyDevice device, @NonNull String authDigits) {}
        @Override public void onConnectionResult(@NonNull String endpointId, boolean isConnected, @NonNull String message) {}
        @Override public void onDisconnected(@NonNull String endpointId) {}
        @Override public void onPayloadReceived(@NonNull String endpointId, @NonNull byte[] payloadBytes) {}
        @Override public void onPayloadTransferUpdate(@NonNull String endpointId, long payloadId, int status) {}
        @Override public void onError(@NonNull String errorMessage) {}
    }

    @FunctionalInterface
    private interface ListenerAction {
        void run(@NonNull NearbyEventListener listener);
    }

    private static volatile NearbyConnectionManager instance;
    private final Context context;
    private final ConnectionsClient connectionsClient;
    private final Handler mainHandler;
    private final SharedPreferences prefs;

    private boolean isAdvertising = false;
    private boolean isDiscovering = false;
    private boolean isAutonomousMode = true; // Phase 12 BitChat Autonomous Mode
    private String localDeviceId;
    private String localCallsign;

    // Discovered and connected endpoints
    private final Map<String, NearbyDevice> discoveredEndpoints = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, NearbyDevice> connectedEndpoints = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, Long> endpointCooldowns = Collections.synchronizedMap(new LinkedHashMap<>());

    // Multi-listener support
    private final Set<NearbyEventListener> listeners = Collections.synchronizedSet(new HashSet<>());
    private NearbyEventListener legacyListener;

    private NearbyConnectionManager(Context context) {
        this.context = context.getApplicationContext();
        this.connectionsClient = Nearby.getConnectionsClient(this.context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.prefs = this.context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        initLocalIdentity();
    }

    public static synchronized NearbyConnectionManager getInstance(Context context) {
        if (instance == null) {
            instance = new NearbyConnectionManager(context);
        }
        return instance;
    }

    private void initLocalIdentity() {
        localDeviceId = prefs.getString("pref_device_id", null);
        if (localDeviceId == null) {
            localDeviceId = "DEV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
            prefs.edit().putString("pref_device_id", localDeviceId).apply();
        }

        localCallsign = prefs.getString(Constants.PREF_NODE_CALLSIGN, null);
        if (localCallsign == null) {
            String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
            localCallsign = "NODE-" + suffix;
            prefs.edit().putString(Constants.PREF_NODE_CALLSIGN, localCallsign).apply();
        }
    }

    public String getLocalDeviceId() {
        return localDeviceId;
    }

    public String getLocalCallsign() {
        return localCallsign;
    }

    public void setListener(NearbyEventListener listener) {
        if (this.legacyListener != null) {
            listeners.remove(this.legacyListener);
        }
        this.legacyListener = listener;
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void registerListener(@NonNull NearbyEventListener listener) {
        listeners.add(listener);
    }

    public void unregisterListener(@NonNull NearbyEventListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(@NonNull ListenerAction action) {
        mainHandler.post(() -> {
            synchronized (listeners) {
                for (NearbyEventListener l : listeners) {
                    if (l != null) {
                        try {
                            action.run(l);
                        } catch (Exception ignored) {}
                    }
                }
            }
        });
    }

    public boolean isAdvertising() {
        return isAdvertising;
    }

    public boolean isDiscovering() {
        return isDiscovering;
    }

    public List<NearbyDevice> getDiscoveredDevices() {
        synchronized (discoveredEndpoints) {
            return new ArrayList<>(discoveredEndpoints.values());
        }
    }

    public List<NearbyDevice> getConnectedDevices() {
        synchronized (connectedEndpoints) {
            return new ArrayList<>(connectedEndpoints.values());
        }
    }

    public int getConnectedPeersCount() {
        synchronized (connectedEndpoints) {
            return connectedEndpoints.size();
        }
    }

    @Nullable
    public NearbyDevice getConnectedDevice() {
        synchronized (connectedEndpoints) {
            if (!connectedEndpoints.isEmpty()) {
                return connectedEndpoints.values().iterator().next();
            }
        }
        return null;
    }

    // =========================================================================
    // Advertising (P2P_CLUSTER)
    // =========================================================================

    public void startAdvertising() {
        if (isAdvertising) {
            return;
        }

        AdvertisingOptions options = new AdvertisingOptions.Builder()
                .setStrategy(Strategy.P2P_CLUSTER)
                .build();

        connectionsClient.startAdvertising(
                localCallsign,
                Constants.NEARBY_SERVICE_ID,
                connectionLifecycleCallback,
                options).addOnSuccessListener(unused -> {
                    isAdvertising = true;
                    notifyListeners(l -> l.onAdvertisingStateChanged(true));
                }).addOnFailureListener(e -> {
                    isAdvertising = false;
                    notifyListeners(l -> {
                        l.onAdvertisingStateChanged(false);
                        l.onError("Advertising failed: " + e.getMessage());
                    });
                });
    }

    public void stopAdvertising() {
        if (!isAdvertising) {
            return;
        }
        try {
            connectionsClient.stopAdvertising();
        } catch (Exception ignored) {
        }
        isAdvertising = false;
        notifyListeners(l -> l.onAdvertisingStateChanged(false));
    }

    // =========================================================================
    // Discovery (P2P_CLUSTER)
    // =========================================================================

    public void startDiscovery() {
        if (isDiscovering) {
            return;
        }

        DiscoveryOptions options = new DiscoveryOptions.Builder()
                .setStrategy(Strategy.P2P_CLUSTER)
                .build();

        connectionsClient.startDiscovery(
                Constants.NEARBY_SERVICE_ID,
                endpointDiscoveryCallback,
                options).addOnSuccessListener(unused -> {
                    isDiscovering = true;
                    discoveredEndpoints.clear();
                    notifyListeners(l -> l.onDiscoveryStateChanged(true));
                }).addOnFailureListener(e -> {
                    isDiscovering = false;
                    notifyListeners(l -> {
                        l.onDiscoveryStateChanged(false);
                        l.onError("Discovery failed: " + e.getMessage());
                    });
                });
    }

    public void stopDiscovery() {
        if (!isDiscovering) {
            return;
        }
        try {
            connectionsClient.stopDiscovery();
        } catch (Exception ignored) {
        }
        isDiscovering = false;
        notifyListeners(l -> l.onDiscoveryStateChanged(false));
    }

    // =========================================================================
    // Connection Management & Explicit Approval
    // =========================================================================

    public void requestConnection(@NonNull final String endpointId) {
        NearbyDevice device = discoveredEndpoints.get(endpointId);
        String name = device != null ? device.getEndpointName() : endpointId;

        connectionsClient.requestConnection(
                localCallsign,
                endpointId,
                connectionLifecycleCallback).addOnSuccessListener(unused -> {
                    if (device != null) {
                        device.setState(NearbyDevice.ConnectionState.CONNECTING);
                    }
                }).addOnFailureListener(e -> {
                    notifyListeners(l -> l.onError("Connection request failed to " + name + ": " + e.getMessage()));
                });
    }

    public void acceptConnection(@NonNull final String endpointId) {
        connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener(e -> {
                    notifyListeners(l -> l.onError("Failed to accept connection: " + e.getMessage()));
                });
    }

    public void rejectConnection(@NonNull final String endpointId) {
        connectionsClient.rejectConnection(endpointId)
                .addOnFailureListener(e -> {
                    notifyListeners(l -> l.onError("Failed to reject connection: " + e.getMessage()));
                });
        NearbyDevice dev = discoveredEndpoints.get(endpointId);
        if (dev != null) {
            dev.setState(NearbyDevice.ConnectionState.DISCOVERED);
        }
    }

    public void disconnectFromEndpoint(@NonNull final String endpointId) {
        try {
            connectionsClient.disconnectFromEndpoint(endpointId);
        } catch (Exception ignored) {
        }
        connectedEndpoints.remove(endpointId);
        NearbyDevice dev = discoveredEndpoints.get(endpointId);
        if (dev != null) {
            dev.setState(NearbyDevice.ConnectionState.DISCONNECTED);
        }
        notifyListeners(l -> l.onDisconnected(endpointId));
    }

    public void stopAll() {
        stopAdvertising();
        stopDiscovery();
        try {
            connectionsClient.stopAllEndpoints();
        } catch (Exception ignored) {
        }
        discoveredEndpoints.clear();
        connectedEndpoints.clear();
    }

    public boolean isAutonomousMode() {
        return isAutonomousMode;
    }

    public void setAutonomousMode(boolean autonomous) {
        this.isAutonomousMode = autonomous;
    }

    public void startAutonomousMesh() {
        this.isAutonomousMode = true;
        if (hasRequiredPermissions(context) && isBluetoothEnabled()) {
            startAdvertising();
            startDiscovery();
        }
    }

    public void stopAutonomousMesh() {
        this.isAutonomousMode = false;
        stopAll();
    }

    public void broadcastToAllConnectedPeers(@NonNull byte[] bytes) {
        synchronized (connectedEndpoints) {
            for (String endpointId : connectedEndpoints.keySet()) {
                sendRawPayload(endpointId, bytes, null);
            }
        }
    }

    public void broadcastToGateways(@NonNull byte[] bytes) {
        synchronized (connectedEndpoints) {
            for (Map.Entry<String, NearbyDevice> entry : connectedEndpoints.entrySet()) {
                if (entry.getValue().isGateway()) {
                    sendRawPayload(entry.getKey(), bytes, null);
                }
            }
        }
    }

    public void updatePeerGatewayCapability(@NonNull String endpointId, boolean isGateway,
                                            @NonNull String networkType, int batteryPct, int pendingUploadCount) {
        NearbyDevice dev = connectedEndpoints.get(endpointId);
        if (dev != null) {
            dev.setGateway(isGateway);
            dev.setNetworkType(networkType);
            dev.setBatteryPct(batteryPct);
            dev.setPendingUploadCount(pendingUploadCount);
        }
        NearbyDevice disc = discoveredEndpoints.get(endpointId);
        if (disc != null) {
            disc.setGateway(isGateway);
            disc.setNetworkType(networkType);
            disc.setBatteryPct(batteryPct);
            disc.setPendingUploadCount(pendingUploadCount);
        }
    }

    // =========================================================================
    // Payload Transmission
    // =========================================================================

    public void sendRawPayload(@NonNull final String endpointId,
            @NonNull final byte[] bytes,
            @Nullable final NearbyPayloadCallback callback) {
        try {
            Payload payload = Payload.fromBytes(bytes);
            connectionsClient.sendPayload(endpointId, payload)
                    .addOnSuccessListener(unused -> {
                        if (callback != null) {
                            mainHandler.post(() -> callback.onSuccess(payload.getId()));
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (callback != null) {
                            mainHandler.post(() -> callback.onFailure(e.getMessage()));
                        }
                    });
        } catch (Exception e) {
            if (callback != null) {
                mainHandler.post(() -> callback.onFailure("Failed to send payload: " + e.getMessage()));
            }
        }
    }

    public void sendEmergencySos(@NonNull final String endpointId,
            @NonNull final EmergencyMessage message,
            @Nullable final NearbyPayloadCallback callback) {
        try {
            byte[] bytes = SosPayloadSerializer.serialize(message);
            sendRawPayload(endpointId, bytes, callback);
        } catch (SosPayloadSerializer.PayloadValidationException pve) {
            if (callback != null) {
                mainHandler.post(() -> callback.onFailure("Payload validation failed: " + pve.getMessage()));
            }
        } catch (Exception e) {
            if (callback != null) {
                mainHandler.post(() -> callback.onFailure("Failed to send payload: " + e.getMessage()));
            }
        }
    }

    public interface NearbyPayloadCallback {
        void onSuccess(long payloadId);

        void onFailure(String errorMessage);
    }

    // =========================================================================
    // Callbacks Implementation
    // =========================================================================

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
            NearbyDevice device = new NearbyDevice(endpointId, info.getEndpointName());
            device.setState(NearbyDevice.ConnectionState.DISCOVERED);
            discoveredEndpoints.put(endpointId, device);
            notifyListeners(l -> l.onEndpointDiscovered(device));

            // Phase 12 BitChat Autonomous Peering: Deterministic tie-breaker
            if (isAutonomousMode && !connectedEndpoints.containsKey(endpointId)
                    && connectedEndpoints.size() < Constants.MAX_SIMULTANEOUS_PEERS) {
                long lastAttempt = endpointCooldowns.getOrDefault(endpointId, 0L);
                long now = System.currentTimeMillis();
                if (now - lastAttempt > Constants.PEER_CONNECTION_COOLDOWN_MS) {
                    endpointCooldowns.put(endpointId, now);
                    String remoteName = info.getEndpointName() != null ? info.getEndpointName() : endpointId;
                    if (localCallsign.compareTo(remoteName) > 0) {
                        requestConnection(endpointId);
                    }
                }
            }
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {
            discoveredEndpoints.remove(endpointId);
            notifyListeners(l -> l.onEndpointLost(endpointId));
        }
    };

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo connectionInfo) {
            NearbyDevice device = discoveredEndpoints.get(endpointId);
            if (device == null) {
                device = new NearbyDevice(endpointId, connectionInfo.getEndpointName());
                discoveredEndpoints.put(endpointId, device);
            } else {
                device.setEndpointName(connectionInfo.getEndpointName());
            }

            device.setState(connectionInfo.isIncomingConnection()
                    ? NearbyDevice.ConnectionState.REQUESTED_INCOMING
                    : NearbyDevice.ConnectionState.REQUESTED_OUTGOING);
            device.setAuthDigits(connectionInfo.getAuthenticationDigits());

            final NearbyDevice finalDev = device;
            final String authDigits = connectionInfo.getAuthenticationDigits();

            notifyListeners(l -> l.onConnectionInitiated(finalDev, authDigits));

            // Phase 12: Automatically accept connections in autonomous mesh mode
            if (isAutonomousMode) {
                acceptConnection(endpointId);
            }
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution resolution) {
            NearbyDevice device = discoveredEndpoints.get(endpointId);
            if (resolution.getStatus().getStatusCode() == ConnectionsStatusCodes.STATUS_OK) {
                if (device != null) {
                    device.setState(NearbyDevice.ConnectionState.CONNECTED);
                    connectedEndpoints.put(endpointId, device);
                } else {
                    device = new NearbyDevice(endpointId, "PEER-" + endpointId);
                    device.setState(NearbyDevice.ConnectionState.CONNECTED);
                    connectedEndpoints.put(endpointId, device);
                }
                final String msg = "Connected to " + device.getEndpointName();
                notifyListeners(l -> l.onConnectionResult(endpointId, true, msg));

                // Phase 12: Trigger autonomous store-and-forward and capability exchange
                try {
                    com.blackout.emergency.relay.RelayEngine.getInstance(context).onPeerConnected(endpointId);
                } catch (Exception ignored) {}
            } else {
                if (device != null) {
                    device.setState(NearbyDevice.ConnectionState.DISCONNECTED);
                }
                connectedEndpoints.remove(endpointId);
                endpointCooldowns.put(endpointId, System.currentTimeMillis());
                String reason;
                switch (resolution.getStatus().getStatusCode()) {
                    case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                        reason = "Connection rejected by peer";
                        break;
                    case ConnectionsStatusCodes.STATUS_NETWORK_NOT_CONNECTED:
                        reason = "Network connection failed";
                        break;
                    default:
                        reason = "Connection failed (Code: " + resolution.getStatus().getStatusCode() + ")";
                        break;
                }
                final String finalReason = reason;
                notifyListeners(l -> l.onConnectionResult(endpointId, false, finalReason));
            }
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
            connectedEndpoints.remove(endpointId);
            endpointCooldowns.put(endpointId, System.currentTimeMillis());
            NearbyDevice device = discoveredEndpoints.get(endpointId);
            if (device != null) {
                device.setState(NearbyDevice.ConnectionState.DISCONNECTED);
            }
            notifyListeners(l -> l.onDisconnected(endpointId));
            try {
                com.blackout.emergency.relay.RelayEngine.getInstance(context).onPeerDisconnected(endpointId);
            } catch (Exception ignored) {}
        }
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            if (payload.getType() == Payload.Type.BYTES) {
                byte[] bytes = payload.asBytes();
                if (bytes != null && bytes.length > 0 && bytes.length <= Constants.MAX_PAYLOAD_SIZE_BYTES) {
                    notifyListeners(l -> l.onPayloadReceived(endpointId, bytes));
                    // Phase 12: Deliver directly to RelayEngine
                    try {
                        com.blackout.emergency.relay.RelayEngine.getInstance(context).handleIncomingPayload(endpointId, bytes);
                    } catch (Exception ignored) {}
                }
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
            notifyListeners(l -> l.onPayloadTransferUpdate(
                    endpointId,
                    update.getPayloadId(),
                    update.getStatus()));
        }
    };

    // =========================================================================
    // Permission & Hardware Availability Helpers
    // =========================================================================

    public static String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+ (API 33+)
            return new String[] {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12 / 12L (API 31-32)
            return new String[] {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else { // Android 8.0 - 11 (API 26-30)
            return new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }
    }

    public static boolean hasRequiredPermissions(@NonNull Context context) {
        for (String perm : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static boolean isBluetoothEnabled() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter != null && adapter.isEnabled();
    }

    public static boolean isLocationEnabled(@NonNull Context context) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null)
            return false;
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }
}
