package com.blackout.emergency.utils;

/**
 * Global constant definitions for BLACKOUT decentralized emergency platform.
 */
public final class Constants {

    private Constants() {
        // Prevent instantiation
    }

    // 8 Core Emergency Categories (Phase 2)
    public static final String CATEGORY_MEDICAL = "Medical Emergency";
    public static final String CATEGORY_ROAD_ACCIDENT = "Road Accident";
    public static final String CATEGORY_TRAPPED = "Trapped / Stranded";
    public static final String CATEGORY_FLOOD = "Flood";
    public static final String CATEGORY_FIRE = "Fire";
    public static final String CATEGORY_DISASTER = "Natural Disaster";
    public static final String CATEGORY_PERSONAL_SAFETY = "Personal Safety";
    public static final String CATEGORY_OTHER = "Other";

    // Emergency Priorities
    public static final String PRIORITY_CRITICAL = "CRITICAL";
    public static final String PRIORITY_HIGH = "HIGH";
    public static final String PRIORITY_MODERATE = "MODERATE";
    public static final String PRIORITY_LOW = "LOW";

    // Valid Message Statuses (Phase 2, Phase 3 & Phase 4)
    public static final String STATUS_LOCAL_SAVED = "LOCAL_SAVED";
    public static final String STATUS_LOCAL_ONLY = "LOCAL_ONLY";
    public static final String STATUS_LOCATION_UNAVAILABLE = "LOCATION_UNAVAILABLE";
    public static final String STATUS_TRANSFER_IN_PROGRESS = "TRANSFER_IN_PROGRESS";
    public static final String STATUS_TRANSFERRED = "TRANSFERRED_TO_NEARBY_DEVICE";
    public static final String STATUS_RECEIVED = "RECEIVED_FROM_NEARBY_DEVICE";
    public static final String STATUS_RELAYED = "RELAYED_TO_PEER";
    public static final String STATUS_TRANSFER_FAILED = "TRANSFER_FAILED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_MAX_HOPS_REACHED = "MAX_HOPS_REACHED";

    // Nearby Connections Protocol Metadata (Phase 3 & Phase 4)
    public static final String NEARBY_SERVICE_ID = "com.blackout.emergency.nearby.SERVICE_ID";
    public static final int PROTOCOL_VERSION = 1;

    // Message Types (Phase 4 & Phase 12 Protocol)
    public static final String MESSAGE_TYPE_SOS = "SOS";
    public static final String MESSAGE_TYPE_INVENTORY = "MESSAGE_INVENTORY";
    public static final String MESSAGE_TYPE_DELIVERY_ACK = "DELIVERY_ACK";
    public static final String MESSAGE_TYPE_DELIVERY_REJECTED = "DELIVERY_REJECTED";
    public static final String MESSAGE_TYPE_RELAY_STATUS = "RELAY_STATUS";
    public static final String MESSAGE_TYPE_GATEWAY_CAPABILITY = "GATEWAY_CAPABILITY";
    public static final String MESSAGE_TYPE_PEER_HANDSHAKE = "PEER_HANDSHAKE";
    public static final String MESSAGE_TYPE_PEER_HANDSHAKE_ACK = "PEER_HANDSHAKE_ACK";
    public static final String MESSAGE_TYPE_TRANSFER_REQUEST = "TRANSFER_REQUEST";

    // Phase 12 Autonomous Mesh & Notification Constants
    public static final String NOTIFICATION_CHANNEL_MESH = "blackout_mesh_channel";
    public static final int NOTIFICATION_ID_MESH = 2001;
    public static final int MAX_SIMULTANEOUS_PEERS = 4;
    public static final long DISCOVERY_FOREGROUND_ACTIVE_MS = 30000L;
    public static final long DISCOVERY_FOREGROUND_PAUSE_MS = 5000L;
    public static final long DISCOVERY_BACKGROUND_ACTIVE_MS = 15000L;
    public static final long DISCOVERY_BACKGROUND_PAUSE_MS = 45000L;
    public static final long PEER_CONNECTION_COOLDOWN_MS = 10000L;

    // Phase 7 Warning Relay Message Types
    public static final String MESSAGE_TYPE_WARNING_INVENTORY = "WARNING_INVENTORY";
    public static final String MESSAGE_TYPE_WARNING_PAYLOAD = "WARNING_PAYLOAD";
    public static final String MESSAGE_TYPE_WARNING_ACK = "WARNING_DELIVERY_ACK";
    public static final String MESSAGE_TYPE_WARNING_REJECTED = "WARNING_DELIVERY_REJECTED";

    // Relay Queue Statuses (Phase 4)
    public static final String RELAY_STATUS_PENDING = "PENDING";
    public static final String RELAY_STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String RELAY_STATUS_RELAYED_TO_PEER = "RELAYED_TO_PEER";
    public static final String RELAY_STATUS_RETRY_WAIT = "RETRY_WAIT";
    public static final String RELAY_STATUS_EXPIRED = "EXPIRED";
    public static final String RELAY_STATUS_MAX_HOPS_REACHED = "MAX_HOPS_REACHED";
    public static final String RELAY_STATUS_FAILED = "FAILED";

    // Cloud Gateway & Upload Statuses (Phase 5)
    public static final String UPLOAD_STATUS_NOT_UPLOADED = "NOT_UPLOADED";
    public static final String UPLOAD_STATUS_PENDING = "UPLOAD_PENDING";
    public static final String UPLOAD_STATUS_UPLOADING = "UPLOADING";
    public static final String UPLOAD_STATUS_BACKEND_ACCEPTED = "BACKEND_ACCEPTED";
    public static final String UPLOAD_STATUS_DUPLICATE_ACCEPTED = "DUPLICATE_ACCEPTED";
    public static final String UPLOAD_STATUS_RETRY_WAIT = "RETRY_WAIT";
    public static final String UPLOAD_STATUS_REJECTED = "REJECTED";
    public static final String UPLOAD_STATUS_EXPIRED = "EXPIRED";

    // Default Supabase Configuration (Phase 5)
    public static final String DEFAULT_SUPABASE_URL = "https://cogmzcchrhdfeqokfwua.supabase.co";
    public static final String DEFAULT_SUPABASE_JWKS_URL = "https://cogmzcchrhdfeqokfwua.supabase.co/auth/v1/.well-known/jwks.json";
    public static final String DEFAULT_SUPABASE_ANON_KEY = "sb_publishable_i4TteJdAqTwsjaoohkC18g_fmW7EW2E";
    public static final String PREF_SUPABASE_URL = "pref_supabase_url";
    public static final String PREF_SUPABASE_ANON_KEY = "pref_supabase_anon_key";

    public static final int MAX_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_PAYLOAD_SIZE_BYTES = 32 * 1024; // 32 KB limit for byte payloads

    // Location Statuses
    public static final String LOC_STATUS_GETTING = "Getting location...";
    public static final String LOC_STATUS_ACQUIRED = "Location acquired";
    public static final String LOC_STATUS_CACHED = "Using previously recorded location";
    public static final String LOC_STATUS_UNAVAILABLE = "GPS signal unavailable";
    public static final String LOC_STATUS_DENIED = "Location permission denied";
    public static final String LOC_STATUS_DISABLED = "Location services disabled";

    // Alert Severities
    public static final String SEVERITY_IMMEDIATE = "IMMEDIATE THREAT";
    public static final String SEVERITY_SEVERE = "SEVERE";
    public static final String SEVERITY_MODERATE = "MODERATE";
    public static final String SEVERITY_ADVISORY = "ADVISORY";

    // Preferences & Identification
    public static final String PREFS_NAME = "blackout_prefs";
    public static final String PREF_NODE_CALLSIGN = "pref_node_callsign";
    public static final String DEFAULT_NODE_CALLSIGN = "NODE-7F84";

    // Protocol Metadata
    public static final int MAX_MESH_HOPS = 7;
    public static final long PACKET_TTL_MILLIS = 48 * 60 * 60 * 1000L; // 48 Hours
}
