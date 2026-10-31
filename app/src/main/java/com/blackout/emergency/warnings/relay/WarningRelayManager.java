package com.blackout.emergency.warnings.relay;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.blackout.emergency.database.PublicWarningDao;
import com.blackout.emergency.nearby.NearbyConnectionManager;
import com.blackout.emergency.utils.Constants;
import com.blackout.emergency.warnings.database.WarningRepository;
import com.blackout.emergency.warnings.models.PublicWarning;
import com.blackout.emergency.warnings.notifications.WarningNotificationManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-hop store-carry-and-forward offline warning distribution manager (Phase 7).
 * Coordinates warning inventory exchange, cryptographic signature verification before forwarding,
 * duplicate suppression, revision propagation, and delivery acknowledgements over Nearby Connections.
 */
public class WarningRelayManager {

    public interface WarningRelayListener {
        void onWarningRelayStatus(@NonNull String statusMessage);
        void onWarningTransferred(@NonNull String warningId, int revision, @NonNull String peerDeviceId);
        void onWarningReceivedVerified(@NonNull PublicWarning warning, @NonNull String peerDeviceId);
    }

    private static volatile WarningRelayManager instance;
    private final Context context;
    private final NearbyConnectionManager connectionManager;
    private final WarningRepository repository;
    private final WarningNotificationManager notificationManager;

    // Track peer warning inventories: endpointId -> Map of warningId to revision
    private final ConcurrentHashMap<String, Map<String, Integer>> peerWarningInventories = new ConcurrentHashMap<>();
    private final Set<WarningRelayListener> listeners = new HashSet<>();

    public WarningRelayManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.connectionManager = NearbyConnectionManager.getInstance(this.context);
        this.repository = WarningRepository.getInstance(this.context);
        this.notificationManager = WarningNotificationManager.getInstance(this.context);
    }

    public static synchronized WarningRelayManager getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new WarningRelayManager(context);
        }
        return instance;
    }

    public void addListener(WarningRelayListener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(WarningRelayListener listener) {
        if (listener != null) listeners.remove(listener);
    }

    private void notifyStatus(String msg) {
        for (WarningRelayListener l : listeners) {
            l.onWarningRelayStatus(msg);
        }
    }

    // =========================================================================
    // Peer Connection Lifecycle
    // =========================================================================

    /**
     * Triggered when a new physical peer connection is established.
     * Gathers and transmits local warning inventory to peer.
     */
    public void onPeerConnected(@NonNull final String endpointId) {
        peerWarningInventories.put(endpointId, new HashMap<>());
        notifyStatus("Connected to peer " + endpointId + ". Gathering warning inventory...");

        repository.getEligibleRelayInventory(items -> {
            List<WarningRelayModels.InventoryItem> modelItems = new ArrayList<>();
            for (PublicWarningDao.InventoryItem item : items) {
                modelItems.add(new WarningRelayModels.InventoryItem(
                        item.warningId,
                        item.revision,
                        item.payloadHash,
                        item.expiresAt,
                        item.verificationStatus
                ));
            }

            try {
                WarningRelayModels.WarningInventoryPayload payload = new WarningRelayModels.WarningInventoryPayload(
                        connectionManager.getLocalDeviceId(),
                        modelItems
                );
                byte[] bytes = payload.toBytes();
                connectionManager.sendRawPayload(endpointId, bytes, new NearbyConnectionManager.NearbyPayloadCallback() {
                    @Override
                    public void onSuccess(long payloadId) {
                        notifyStatus("Sent warning inventory (" + modelItems.size() + " items) to " + endpointId);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        notifyStatus("Failed to send warning inventory to " + endpointId + ": " + errorMessage);
                    }
                });
            } catch (Exception e) {
                notifyStatus("Error creating warning inventory payload: " + e.getMessage());
            }
        });
    }

    public void onPeerDisconnected(@NonNull final String endpointId) {
        peerWarningInventories.remove(endpointId);
        notifyStatus("Peer " + endpointId + " disconnected from warning relay.");
    }

    // =========================================================================
    // Inbound Payload Dispatching
    // =========================================================================

    public boolean canHandleMessageType(@Nullable String messageType) {
        return Constants.MESSAGE_TYPE_WARNING_INVENTORY.equals(messageType)
                || Constants.MESSAGE_TYPE_WARNING_PAYLOAD.equals(messageType)
                || Constants.MESSAGE_TYPE_WARNING_ACK.equals(messageType)
                || Constants.MESSAGE_TYPE_WARNING_REJECTED.equals(messageType);
    }

    public void handleIncomingWarningPayload(@NonNull final String endpointId, @NonNull final byte[] bytes) {
        String type = WarningRelayModels.peekMessageType(bytes);

        switch (type) {
            case Constants.MESSAGE_TYPE_WARNING_INVENTORY:
                handleIncomingInventory(endpointId, bytes);
                break;
            case Constants.MESSAGE_TYPE_WARNING_PAYLOAD:
                handleIncomingWarning(endpointId, bytes);
                break;
            case Constants.MESSAGE_TYPE_WARNING_ACK:
                handleIncomingAck(endpointId, bytes);
                break;
            case Constants.MESSAGE_TYPE_WARNING_REJECTED:
                handleIncomingReject(endpointId, bytes);
                break;
            default:
                break;
        }
    }

    // 1. Process Peer Warning Inventory
    private void handleIncomingInventory(@NonNull final String endpointId, @NonNull final byte[] bytes) {
        try {
            WarningRelayModels.WarningInventoryPayload inventory = WarningRelayModels.WarningInventoryPayload.fromBytes(bytes);
            Map<String, Integer> peerMap = peerWarningInventories.get(endpointId);
            if (peerMap == null) {
                peerMap = new HashMap<>();
                peerWarningInventories.put(endpointId, peerMap);
            }

            for (WarningRelayModels.InventoryItem item : inventory.getItems()) {
                peerMap.put(item.warningId, item.revision);
            }

            notifyStatus("Received inventory from " + endpointId + " (" + inventory.getItems().size() + " warnings).");
            syncMissingWarningsToPeer(endpointId, inventory.getSenderDeviceId(), peerMap);

        } catch (Exception e) {
            notifyStatus("Error processing peer warning inventory: " + e.getMessage());
        }
    }

    // Identifies warnings we have that the peer is missing or has older revisions of, and forwards them
    private void syncMissingWarningsToPeer(@NonNull final String endpointId,
                                          @NonNull final String peerDeviceId,
                                          @NonNull final Map<String, Integer> peerKnownMap) {
        repository.getActiveVerifiedWarnings(ourWarnings -> {
            List<PublicWarning> toSend = new ArrayList<>();
            for (PublicWarning w : ourWarnings) {
                Integer peerRev = peerKnownMap.get(w.getWarningId());
                // If peer does not have it, or has an older revision than ours
                if (peerRev == null || w.getRevision() > peerRev) {
                    toSend.add(w);
                }
            }

            if (toSend.isEmpty()) {
                notifyStatus("Warning database already up to date with peer " + endpointId);
                return;
            }

            notifyStatus("Forwarding " + toSend.size() + " missing/updated warnings to " + endpointId + "...");
            sendNextWarningInBatch(endpointId, peerDeviceId, toSend, 0);
        });
    }

    private void sendNextWarningInBatch(@NonNull final String endpointId,
                                        @NonNull final String peerDeviceId,
                                        @NonNull final List<PublicWarning> batch,
                                        final int index) {
        if (index >= batch.size()) {
            notifyStatus("Completed warning relay batch to " + endpointId);
            return;
        }

        PublicWarning warning = batch.get(index);
        try {
            WarningRelayModels.WarningTransferPayload payload = new WarningRelayModels.WarningTransferPayload(
                    connectionManager.getLocalDeviceId(),
                    warning
            );
            byte[] bytes = payload.toBytes();

            connectionManager.sendRawPayload(endpointId, bytes, new NearbyConnectionManager.NearbyPayloadCallback() {
                @Override
                public void onSuccess(long payloadId) {
                    repository.recordRelayEvent(warning.getWarningId(), warning.getRevision(), peerDeviceId, "SENT", "Forwarded to peer");
                    for (WarningRelayListener l : listeners) {
                        l.onWarningTransferred(warning.getWarningId(), warning.getRevision(), peerDeviceId);
                    }
                    sendNextWarningInBatch(endpointId, peerDeviceId, batch, index + 1);
                }

                @Override
                public void onFailure(String errorMessage) {
                    repository.recordRelayEvent(warning.getWarningId(), warning.getRevision(), peerDeviceId, "FAILED", errorMessage);
                    sendNextWarningInBatch(endpointId, peerDeviceId, batch, index + 1);
                }
            });
        } catch (Exception e) {
            sendNextWarningInBatch(endpointId, peerDeviceId, batch, index + 1);
        }
    }

    // 2. Process Inbound Warning (Verify -> Persist -> Return ACK)
    private void handleIncomingWarning(@NonNull final String endpointId, @NonNull final byte[] bytes) {
        try {
            WarningRelayModels.WarningTransferPayload transfer = WarningRelayModels.WarningTransferPayload.fromBytes(bytes);
            PublicWarning warning = transfer.getWarning();
            warning.setSourceDeviceId(transfer.getSenderDeviceId());

            // Process, verify and store
            repository.processAndStoreWarning(warning, (result, storedWarning, message) -> {
                if (result == WarningRepository.WarningProcessResult.ACCEPTED_NEW
                        || result == WarningRepository.WarningProcessResult.ACCEPTED_REVISION_UPDATE) {
                    notifyStatus("Accepted and verified incoming warning [" + warning.getTitle() + "] Rev " + warning.getRevision());
                    sendAck(endpointId, warning.getWarningId(), warning.getRevision(), "ACCEPTED");

                    // Notify user
                    if (storedWarning != null && storedWarning.isAuthenticAndVerified()) {
                        notificationManager.notifyWarning(storedWarning);
                        for (WarningRelayListener l : listeners) {
                            l.onWarningReceivedVerified(storedWarning, transfer.getSenderDeviceId());
                        }
                    }

                } else if (result == WarningRepository.WarningProcessResult.DUPLICATE_IGNORED) {
                    // Send duplicate ACK so peer knows we already hold this revision
                    sendAck(endpointId, warning.getWarningId(), warning.getRevision(), "DUPLICATE");

                } else {
                    notifyStatus("Rejected incoming warning from " + endpointId + ": " + message);
                    sendReject(endpointId, warning.getWarningId(), warning.getRevision(), message);
                }
            });

        } catch (Exception e) {
            sendReject(endpointId, "UNKNOWN", 1, "Malformed payload: " + e.getMessage());
        }
    }

    private void sendAck(@NonNull String endpointId, @NonNull String warningId, int revision, @NonNull String status) {
        try {
            WarningRelayModels.WarningAckPayload ack = new WarningRelayModels.WarningAckPayload(
                    warningId, revision, connectionManager.getLocalDeviceId(), status
            );
            connectionManager.sendRawPayload(endpointId, ack.toBytes(), null);
        } catch (Exception ignored) {}
    }

    private void sendReject(@NonNull String endpointId, @NonNull String warningId, int revision, @NonNull String reason) {
        try {
            WarningRelayModels.WarningRejectPayload rej = new WarningRelayModels.WarningRejectPayload(
                    warningId, revision, connectionManager.getLocalDeviceId(), reason
            );
            connectionManager.sendRawPayload(endpointId, rej.toBytes(), null);
        } catch (Exception ignored) {}
    }

    private void handleIncomingAck(@NonNull String endpointId, @NonNull byte[] bytes) {
        try {
            WarningRelayModels.WarningAckPayload ack = WarningRelayModels.WarningAckPayload.fromBytes(bytes);
            repository.recordRelayEvent(ack.getWarningId(), ack.getRevision(), ack.getSenderDeviceId(), "ACK_RECEIVED", "Status: " + ack.getStatus());
            notifyStatus("Received delivery ACK for warning [" + ack.getWarningId() + "] from " + endpointId);
        } catch (Exception ignored) {}
    }

    private void handleIncomingReject(@NonNull String endpointId, @NonNull byte[] bytes) {
        try {
            WarningRelayModels.WarningRejectPayload rej = WarningRelayModels.WarningRejectPayload.fromBytes(bytes);
            repository.recordRelayEvent(rej.getWarningId(), rej.getRevision(), rej.getSenderDeviceId(), "REJECTED", rej.getReason());
            notifyStatus("Peer " + endpointId + " rejected warning [" + rej.getWarningId() + "]: " + rej.getReason());
        } catch (Exception ignored) {}
    }
}
