package com.blackout.emergency.relay;

import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.utils.Constants;

/**
 * Central configuration and policy rules for the store-carry-and-forward relay engine.
 * Governs hop limits, packet TTL, retry intervals, bounded inventories, and priority rules.
 */
public final class RelayPolicy {

    private RelayPolicy() {
        // Prevent instantiation
    }

    /** Maximum number of hops an SOS packet can be relayed before being dropped. */
    public static final int DEFAULT_MAX_HOPS = 7;

    /** Default packet Time-To-Live: 48 hours. */
    public static final long DEFAULT_TTL_MILLIS = Constants.PACKET_TTL_MILLIS;

    /** Maximum retry attempts per message before marking as permanently failed for that cycle. */
    public static final int MAX_RETRY_ATTEMPTS = 5;

    /** Base backoff interval for retries: 30 seconds. */
    public static final long BASE_RETRY_BACKOFF_MILLIS = 30 * 1000L;

    /** Maximum backoff interval for retries: 15 minutes. */
    public static final long MAX_RETRY_BACKOFF_MILLIS = 15 * 60 * 1000L;

    /** Maximum number of message IDs to exchange in a single inventory payload. */
    public static final int MAX_INVENTORY_SIZE = 50;

    /** Maximum number of SOS messages to forward in a single peer connection batch. */
    public static final int MAX_TRANSFER_BATCH_SIZE = 10;

    /**
     * Determines whether an emergency message is eligible for relaying.
     * Rejects expired messages, messages that reached the hop limit, or null messages.
     */
    public static boolean isEligibleForRelay(EmergencyMessage message) {
        if (message == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        // Check expiry
        if (message.getExpiresAt() != null && message.getExpiresAt() <= now) {
            return false;
        }

        // Check hop limit
        if (message.getHopCount() >= DEFAULT_MAX_HOPS) {
            return false;
        }

        return true;
    }

    /**
     * Calculates the exponential backoff interval in milliseconds based on attempt count.
     */
    public static long calculateBackoffMillis(int attemptCount) {
        if (attemptCount <= 0) {
            return BASE_RETRY_BACKOFF_MILLIS;
        }
        long backoff = BASE_RETRY_BACKOFF_MILLIS * (1L << Math.min(attemptCount - 1, 6));
        return Math.min(backoff, MAX_RETRY_BACKOFF_MILLIS);
    }

    /**
     * Maps priority string to numeric weight (higher number = higher priority).
     */
    public static int getPriorityWeight(String priority) {
        if (priority == null) return 1;
        switch (priority.toUpperCase()) {
            case Constants.PRIORITY_CRITICAL:
                return 4;
            case Constants.PRIORITY_HIGH:
                return 3;
            case Constants.PRIORITY_MODERATE:
                return 2;
            case Constants.PRIORITY_LOW:
            default:
                return 1;
        }
    }
}
