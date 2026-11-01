package com.blackout.emergency.relay;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.blackout.emergency.database.EmergencyRepository;
import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.nearby.NearbyConnectionManager;
import com.blackout.emergency.nearby.NearbyDevice;
import com.blackout.emergency.nearby.ReceivedMessageHandler;
import com.blackout.emergency.nearby.SosPayloadSerializer;
import com.blackout.emergency.utils.Constants;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core engine for store-carry-and-forward multi-hop SOS relay.
 * Coordinates inventory exchange, duplicate suppression, prioritized queue forwarding,
 * and application-level delivery acknowledgements over Nearby Connections.
 */
public class RelayEngine {

    public interface RelayEventListener {
        void onRelayStatusChanged(@NonNull String statusMessage);
        void onRelayProgress(@NonNull String endpointId, int completed, int total);
        void onMessageRelayedSuccess(@NonNull String messageId, @NonNull String peerDeviceId, int hopNumber);
        void onMessageRelayFailed(@NonNull String messageId, @Nullable String reason);
    }

    private static volatile RelayEngine instance;
    private final Context context;
    private final NearbyConnectionManager connectionManager;
    private final RelayQueueManager queueManager;
    private final EmergencyRepository repository;
    private final ReceivedMessageHandler messageHandler;
    private final Handler mainHandler;

    // Track active peer inventories and in-flight message transfers
    private final ConcurrentHashMap<String, Set<String>> peerInventories = new ConcurrentHashMap<>();
    private final Set<RelayEventListener> listeners = Collections.synchronizedSet(new HashSet<>());

    private static Handler createSafeMainHandler() {
        try {
            Looper looper = Looper.getMainLooper();
            if (looper != null) {
                return new Handler(looper);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void postToMain(Runnable r) {
        if (r == null) return;
        if (mainHandler != null) {
            mainHandler.post(r);
        } else {
            r.run();
        }
    }

    private RelayEngine(Context context) {
        this.context = context.getApplicationContext();
        this.connectionManager = NearbyConnectionManager.getInstance(this.context);
        this.queueManager = RelayQueueManager.getInstance(this.context);
        this.repository = EmergencyRepository.getInstance(this.context);
        this.messageHandler = new ReceivedMessageHandler(this.context);
        this.mainHandler = createSafeMainHandler();
    }

    public static synchronized RelayEngine getInstance(Context context) {
        if (instance == null) {
            instance = new RelayEngine(context);
        }
        return instance;
    }

    public void addListener(RelayEventListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(RelayEventListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public RelayQueueManager getQueueManager() {
        return queueManager;
    }

    // =========================================================================
    // Peer Connection Event Handling
    // =========================================================================

    /**
     * Triggered when a new physical peer connection is successfully established.
     * Initiates the store-carry-and-forward protocol by sending local message inventory.
     */
    public void onPeerConnected(@NonNull final String endpointId) {
        peerInventories.put(endpointId, new HashSet<>());
        postStatus("Connected to peer " + endpointId + ". Exchanging message inventories...");

        // Phase 7: Trigger warning inventory exchange in parallel with SOS inventory
        com.blackout.emergency.warnings.relay.WarningRelayManager.getInstance(context).onPeerConnected(endpointId);

        // 1. Gather our local message inventory and transmit to peer
        queueManager.getLocalInventoryIds(localIds -> {
            try {
                RelayPayloadModels.InventoryPayload inventory = new RelayPayloadModels.InventoryPayload(
                        connectionManager.getLocalDeviceId(),
                        localIds
                );
                byte[] inventoryBytes = inventory.toBytes();
                connectionManager.sendRawPayload(endpointId, inventoryBytes, new NearbyConnectionManager.NearbyPayloadCallback() {
                    @Override
                    public void onSuccess(long payloadId) {
                        postStatus("Sent inventory (" + localIds.size() + " messages) to " + endpointId);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        postStatus("Failed to send inventory to " + endpointId + ": " + errorMessage);
                    }
                });
            } catch (Exception e) {
                postStatus("Error serializing inventory: " + e.getMessage());
            }
        });
    }

    /**
     * Triggered when a peer disconnects. Cleans up peer inventory cache.
     */
    public void onPeerDisconnected(@NonNull final String endpointId) {
        peerInventories.remove(endpointId);
        com.blackout.emergency.warnings.relay.WarningRelayManager.getInstance(context).onPeerDisconnected(endpointId);
        postStatus("Peer " + endpointId + " disconnected. Preserving queued relays for next peer.");
    }

    // =========================================================================
    // Inbound Payload Dispatcher
    // =========================================================================

    /**
     * Inspects and processes incoming raw byte payloads from connected peers.
     */
    public void handleIncomingPayload(@NonNull final String endpointId, @NonNull final byte[] payloadBytes) {
        String messageType = RelayPayloadModels.peekMessageType(payloadBytes);

        // Phase 7: Check if payload is a warning distribution protocol packet
        if (com.blackout.emergency.warnings.relay.WarningRelayManager.getInstance(context).canHandleMessageType(messageType)) {
            com.blackout.emergency.warnings.relay.WarningRelayManager.getInstance(context).handleIncomingWarningPayload(endpointId, payloadBytes);
            return;
        }

        switch (messageType) {
            case Constants.MESSAGE_TYPE_INVENTORY:
                handleIncomingInventory(endpointId, payloadBytes);
                break;
            case Constants.MESSAGE_TYPE_DELIVERY_ACK:
                handleIncomingDeliveryAck(endpointId, payloadBytes);
                break;
            case Constants.MESSAGE_TYPE_DELIVERY_REJECTED:
                handleIncomingDeliveryReject(endpointId, payloadBytes);
                break;
            case Constants.MESSAGE_TYPE_SOS:
            default:
                handleIncomingSos(endpointId, payloadBytes);
                break;
        }
    }

    // =========================================================================
    // 1. Handle Peer Message Inventory
    // =========================================================================

    private void handleIncomingInventory(@NonNull final String endpointId, @NonNull final byte[] bytes) {
        try {
            RelayPayloadModels.InventoryPayload inventory = RelayPayloadModels.InventoryPayload.fromBytes(bytes);
            Set<String> known = peerInventories.get(endpointId);
            if (known == null) {
                known = new HashSet<>();
                peerInventories.put(endpointId, known);
            }
            known.addAll(inventory.getKnownMessageIds());

            postStatus("Received inventory from " + endpointId + " (" + inventory.getKnownMessageIds().size() + " messages). Starting relay...");

            // Query our queue for eligible messages that this peer does NOT possess
            startRelayingToPeer(endpointId, inventory.getSenderDeviceId(), inventory.getKnownMessageIds());

        } catch (Exception e) {
            postStatus("Invalid inventory payload from " + endpointId + ": " + e.getMessage());
        }
    }

    /**
     * Selects and forwards eligible queued messages to a connected peer.
     */
    public void startRelayingToPeer(@NonNull final String endpointId,
                                   @NonNull final String peerDeviceId,
                                   @NonNull final List<String> peerKnownIds) {
        queueManager.getEligibleMessagesForPeer(
                peerDeviceId,
                peerKnownIds,
                RelayPolicy.MAX_TRANSFER_BATCH_SIZE,
                messagesToRelay -> {
                    if (messagesToRelay.isEmpty()) {
                        postStatus("All messages already synchronized with peer " + endpointId);
                        return;
                    }

                    postStatus("Forwarding " + messagesToRelay.size() + " eligible SOS messages to " + endpointId + "...");
                    forwardNextMessageInBatch(endpointId, peerDeviceId, messagesToRelay, 0);
                }
        );
    }

    private void forwardNextMessageInBatch(@NonNull final String endpointId,
                                           @NonNull final String peerDeviceId,
                                           @NonNull final List<EmergencyMessage> batch,
                                           final int index) {
        if (index >= batch.size()) {
            postStatus("Completed relay batch for peer " + endpointId);
            return;
        }

        EmergencyMessage originalMsg = batch.get(index);
        // Create relay forwarding copy: increment hopCount by 1
        int nextHopCount = originalMsg.getHopCount() + 1;
        EmergencyMessage relayCopy = new EmergencyMessage(
                originalMsg.getSenderDeviceId(),
                originalMsg.getSenderCallsign(),
                originalMsg.getEmergencyCategory(),
                originalMsg.getDescription(),
                originalMsg.getPriority(),
                originalMsg.getLatitude(),
                originalMsg.getLongitude(),
                originalMsg.getLocationAccuracy(),
                originalMsg.getLocationTimestamp(),
                Constants.STATUS_TRANSFER_IN_PROGRESS,
                originalMsg.getLocationStatus()
        );
        relayCopy.setMessageId(originalMsg.getMessageId());
        relayCopy.setCreatedAt(originalMsg.getCreatedAt());
        relayCopy.setExpiresAt(originalMsg.getExpiresAt());
        relayCopy.setHopCount(nextHopCount);

        try {
            byte[] payloadBytes = SosPayloadSerializer.serialize(relayCopy, connectionManager.getLocalDeviceId());

            connectionManager.sendRawPayload(endpointId, payloadBytes, new NearbyConnectionManager.NearbyPayloadCallback() {
                @Override
                public void onSuccess(long payloadId) {
                    postStatus("Sent SOS [" + originalMsg.getMessageId().substring(0, 8) + "] to " + endpointId + ". Awaiting ACK...");
                    // Proceed to next message in batch
                    forwardNextMessageInBatch(endpointId, peerDeviceId, batch, index + 1);
                }

                @Override
                public void onFailure(String errorMessage) {
                    postStatus("Failed sending SOS [" + originalMsg.getMessageId().substring(0, 8) + "] to " + endpointId + ": " + errorMessage);
                    queueManager.recordDeliveryFailure(originalMsg.getMessageId(), errorMessage, null);
                    // Continue batch despite failure
                    forwardNextMessageInBatch(endpointId, peerDeviceId, batch, index + 1);
                }
            });

        } catch (Exception e) {
            queueManager.recordDeliveryFailure(originalMsg.getMessageId(), e.getMessage(), null);
            forwardNextMessageInBatch(endpointId, peerDeviceId, batch, index + 1);
        }
    }

    // =========================================================================
    // 2. Handle Inbound SOS Message (Validate -> Persist -> Return ACK)
    // =========================================================================

    private void handleIncomingSos(@NonNull final String endpointId, @NonNull final byte[] bytes) {

        messageHandler.processIncomingPayload(bytes, (outcome, message, errorMessage) -> {
            if (outcome == ReceivedMessageHandler.ProcessingOutcome.SUCCESS && message != null) {
                postStatus("Persisted incoming SOS [" + message.getMessageId().substring(0, 8) + "] (Hop: " + message.getHopCount() + "). Sending ACK...");
                sendDeliveryAck(endpointId, message.getMessageId(), message.getHopCount());

            } else if (outcome == ReceivedMessageHandler.ProcessingOutcome.DUPLICATE_IGNORED && message != null) {
                postStatus("Duplicate SOS [" + message.getMessageId().substring(0, 8) + "] already stored. Returning ACK...");
                // Acknowledge duplicate so sender stops repeating
                sendDeliveryAck(endpointId, message.getMessageId(), message.getHopCount());

            } else {
                // Reject invalid or unpersisted payload
                postStatus("Rejected incoming payload from " + endpointId + ": " + errorMessage);
                String msgId = message != null ? message.getMessageId() : "UNKNOWN";
                sendDeliveryReject(endpointId, msgId, errorMessage != null ? errorMessage : "Validation failed");
            }
        });
    }

    private void sendDeliveryAck(@NonNull final String endpointId, @NonNull final String messageId, final int hopNumber) {
        try {
            RelayPayloadModels.DeliveryAckPayload ack = new RelayPayloadModels.DeliveryAckPayload(
                    messageId,
                    connectionManager.getLocalDeviceId(),
                    hopNumber
            );
            byte[] ackBytes = ack.toBytes();
            connectionManager.sendRawPayload(endpointId, ackBytes, null);
        } catch (Exception e) {
            postStatus("Failed to send delivery ACK: " + e.getMessage());
        }
    }

    private void sendDeliveryReject(@NonNull final String endpointId, @NonNull final String messageId, @NonNull final String reason) {
        try {
            RelayPayloadModels.DeliveryRejectPayload reject = new RelayPayloadModels.DeliveryRejectPayload(
                    messageId,
                    connectionManager.getLocalDeviceId(),
                    reason
            );
            byte[] rejectBytes = reject.toBytes();
            connectionManager.sendRawPayload(endpointId, rejectBytes, null);
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // 3. Handle Inbound Delivery Acknowledgement
    // =========================================================================

    private void handleIncomingDeliveryAck(@NonNull final String endpointId, @NonNull final byte[] bytes) {
        try {
            RelayPayloadModels.DeliveryAckPayload ack = RelayPayloadModels.DeliveryAckPayload.fromBytes(bytes);
            postStatus("Received delivery ACK for [" + ack.getMessageId().substring(0, 8) + "] from " + endpointId);

            queueManager.recordDeliverySuccess(
                    ack.getMessageId(),
                    connectionManager.getLocalDeviceId(),
                    ack.getReceiverDeviceId(),
                    ack.getHopNumber(),
                    success -> {
                        for (RelayEventListener l : listeners) {
                            l.onMessageRelayedSuccess(ack.getMessageId(), ack.getReceiverDeviceId(), ack.getHopNumber());
                        }
                    }
            );

        } catch (Exception e) {
            postStatus("Malformed delivery ACK from " + endpointId + ": " + e.getMessage());
        }
    }

    // =========================================================================
    // 4. Handle Inbound Delivery Rejection
    // =========================================================================

    private void handleIncomingDeliveryReject(@NonNull final String endpointId, @NonNull final byte[] bytes) {
        try {
            RelayPayloadModels.DeliveryRejectPayload reject = RelayPayloadModels.DeliveryRejectPayload.fromBytes(bytes);
            postStatus("Peer " + endpointId + " rejected message [" + reject.getMessageId() + "]: " + reject.getReason());

            queueManager.recordDeliveryFailure(reject.getMessageId(), reject.getReason(), success -> {
                for (RelayEventListener l : listeners) {
                    l.onMessageRelayFailed(reject.getMessageId(), reject.getReason());
                }
            });

        } catch (Exception e) {
            postStatus("Malformed delivery rejection from " + endpointId);
        }
    }

    private void postStatus(@NonNull final String status) {
        postToMain(() -> {
            for (RelayEventListener l : listeners) {
                l.onRelayStatusChanged(status);
            }
        });
    }
}
