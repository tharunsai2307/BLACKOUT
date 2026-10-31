package com.blackout.emergency.services;

import android.content.Context;
import com.blackout.emergency.models.EmergencyMessage;

/**
 * Architectural service skeleton for peer-to-peer mesh communications.
 * <p>
 * NOTE FOR PHASE 1:
 * Hardware peer discovery (Bluetooth Low Energy and Wi-Fi Direct), store-and-forward relay
 * protocols, and asymmetric payload encryption will be integrated in Phase 2 and Phase 3.
 * In Phase 1, this service manages interface state representations honestly without fake network traffic.
 */
public class CommunicationService {

    private static CommunicationService instance;
    private final Context context;
    private boolean isDiscoveryActive = false;

    private CommunicationService(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized CommunicationService getInstance(Context context) {
        if (instance == null) {
            instance = new CommunicationService(context);
        }
        return instance;
    }

    /**
     * Indicates whether mesh radio discovery is active.
     */
    public boolean isDiscoveryActive() {
        return com.blackout.emergency.nearby.NearbyConnectionManager.getInstance(context).isDiscovering();
    }

    public void setDiscoveryActive(boolean active) {
        this.isDiscoveryActive = active;
    }

    public Context getContext() {
        return context;
    }

    /**
     * Returns true if offline communication hardware bindings are active.
     */
    public boolean isMeshConfigured() {
        return com.blackout.emergency.nearby.NearbyConnectionManager.hasRequiredPermissions(context)
                && com.blackout.emergency.nearby.NearbyConnectionManager.isBluetoothEnabled();
    }

    /**
     * Staged method for preparing an emergency SOS packet locally.
     * Persistence and relay queueing will be connected in Phase 2.
     */
    public boolean prepareSosMessage(EmergencyMessage message) {
        if (message == null) {
            return false;
        }
        // Locally validate and stage the packet
        return message.getDescription() != null && !message.getDescription().trim().isEmpty();
    }
}
