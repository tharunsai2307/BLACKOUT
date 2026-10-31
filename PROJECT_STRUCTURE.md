# BLACKOUT — Project Structure & Architectural Specification

This document details the modular package hierarchy, code responsibilities, database schema, and resource organization across Phases 1, 2, 3, 4, and 5 for the **BLACKOUT** decentralized emergency communication platform.

---

## 🏛️ Java Package Architecture

The codebase adheres strictly to clean separation of concerns under `com.blackout.emergency`:

```
com.blackout.emergency
│
├── activities
│   └── MainActivity.java
│       ├── Coordinates root window, system bars, and bottom navigation
│       ├── Initializes InternetGatewayService on application launch
│       ├── Coordinates fragment transactions with smooth animated transitions
│       ├── Handles Android back navigation backstack & returns to Home tab
│       └── Preserves active tab state across configuration changes
│
├── gateway
│   ├── BackendApiClient.java
│   │   ├── Pure Java HttpURLConnection / HttpsURLConnection client for Supabase
│   │   ├── Zero third-party HTTP dependencies (no OkHttp or Retrofit)
│   │   ├── Encodes canonical JSON payload matching Supabase Edge Function API
│   │   ├── Client-side validation: UUID, coordinates, category, hop count
│   │   ├── Handles HTTP 201 (SUCCESS), 200 (DUPLICATE_ACCEPTED), 400 (REJECTED),
│   │   │   409 (CONFLICT), and 5xx / IOException (RETRYABLE)
│   │   └── Exposes checkHealth() and uploadAlert()
│   │
│   ├── PendingUploadManager.java
│   │   ├── Asynchronous Room coordinator for pending_uploads table
│   │   ├── Enqueues local and relayed SOS alerts for cloud synchronization
│   │   ├── Bounded batch execution with thread-safe single-runner guard
│   │   ├── Exponential backoff algorithm for retryable errors (5s to 5m)
│   │   ├── Rejection and expiry lifecycle state transitions
│   │   └── Dispatches sync progress and completion events to UI listeners
│   │
│   └── InternetGatewayService.java
│       ├── Coordinates device internet gateway state via ConnectivityManager
│       ├── Detects live Wi-Fi, Cellular, and Ethernet network capabilities
│       ├── Automatically flushes PendingUploadManager when internet connects
│       └── Provides thread-safe isInternetAvailable() and getCurrentNetworkType()
│
├── database
│   ├── BlackoutDatabase.java
│   │   ├── RoomDatabase singleton configuration (version 3)
│   │   ├── Entities: EmergencyMessageEntity, RelayQueueEntity, RelayHistoryEntity,
│   │   │   PendingUploadEntity
│   │   └── Exposes emergencyMessageDao(), relayQueueDao(), relayHistoryDao(),
│   │       and pendingUploadDao()
│   │
│   ├── PendingUploadEntity.java
│   │   ├── Room @Entity mapped to table "pending_uploads" (indexed on uploadStatus & nextAttemptAt)
│   │   └── Fields: messageId (PK), uploadStatus, gatewayDeviceId, attemptCount,
│   │       nextAttemptAt, lastAttemptAt, uploadedAt, backendReference, lastErrorCode
│   │
│   ├── PendingUploadDao.java
│   │   ├── Data Access Object for cloud upload queue
│   │   └── Queries: insert, update, getByMessageId, getEligibleUploads, getAllUploads,
│   │       markAccepted, updateRetry, getPendingUploadCount, deleteByMessageId, deleteAll
│   │
│   ├── EmergencyMessageDao.java
│   │   ├── Data Access Object for SQLite table emergency_messages
│   │   └── Queries: insert, insertAll, update, updateStatus, delete, deleteById,
│   │       getAllMessages, getMessageById, getMessagesByCategory,
│   │       getOutgoingMessages, getMessageCount, countById, deleteAll
│   │
│   ├── EmergencyMessageEntity.java
│   │   ├── Room @Entity mapped to table "emergency_messages"
│   │   ├── Bidirectional mapping methods: fromModel() and toModel()
│   │   └── Fields: messageId (PK), senderDeviceId, senderCallsign,
│   │       emergencyCategory, description, latitude, longitude, locationAccuracy,
│   │       locationTimestamp, createdAt, expiresAt, priority, status,
│   │       locationStatus, hopCount
│   │
│   ├── RelayQueueEntity.java
│   │   ├── Room @Entity mapped to table "relay_queue" (indexed on relayStatus & nextAttemptAt)
│   │   └── Fields: messageId (PK), relayStatus, queuedAt, nextAttemptAt, attemptCount,
│   │       lastAttemptAt, lastError, priority
│   │
│   ├── RelayQueueDao.java
│   │   ├── Data Access Object for store-carry-and-forward queue
│   │   └── Queries: insert, update, getByMessageId, getEligibleQueueItems (prioritized),
│   │       getAllQueueItems, updateStatus, updateRetry, deleteByMessageId,
│   │       getPendingRelayCount, deleteAll
│   │
│   ├── RelayHistoryEntity.java
│   │   ├── Room @Entity mapped to table "relay_history" (indexed on messageId & toDeviceId)
│   │   └── Fields: id (PK auto), messageId, fromDeviceId, toDeviceId, hopNumber,
│   │       transferStatus, timestamp
│   │
│   ├── RelayHistoryDao.java
│   │   ├── Data Access Object for relay delivery logs and duplicate suppression
│   │   └── Queries: insert, getHistoryForMessage, getRecentHistory, countDeliveredToPeer,
│   │       getTotalRelayCount, deleteAll
│   │
│   └── EmergencyRepository.java
│       ├── Central repository managing asynchronous database access
│       ├── Background ExecutorService executes SQLite queries off main thread
│       ├── Safe main-thread Handler dispatches results back to UI listeners
│       ├── Automatically enqueues new/received SOS messages into RelayQueueManager
│       └── Methods: insertMessage, getAllMessages, getMessageById,
│           getMessagesByCategory, updateMessageStatus, getOutgoingMessages,
│           insertReceivedMessage, deleteMessage, getMessageCount, deleteAllMessages
│
├── fragments
│   ├── HomeFragment.java
│   │   ├── Mission dashboard displaying radio interface status & transceivers state
│   │   ├── Dynamic Nearby Connection status indicator and peer counter
│   │   ├── Prominent hero card launching Emergency SOS preparation
│   │   ├── Live Recent Activity card bound to Room DB (displays newest SOS record or empty state)
│   │   └── Overview cards with quick navigation to Nearby, History, and Alerts
│   │
│   ├── SosFragment.java
│   │   ├── Dedicated emergency alert creation interface
│   │   ├── Multi-line situation description & required supplies input
│   │   ├── 8 Category chips (Medical, Accident, Trapped, Flood, Fire, Disaster, Safety, Other)
│   │   ├── Dynamic priority computation (Critical, High, Moderate)
│   │   ├── Live GPS capture via LocationService with accuracy & timestamp telemetry
│   │   ├── Pre-creation review confirmation dialog with full summary
│   │   └── Permanent Room database persistence with honest feedback dialog
│   │
│   ├── NearbyFragment.java
│   │   ├── Live offline peer discovery, advertising, and connection dashboard
│   │   ├── Local node identity card (Callsign e.g. NODE-7F84 and Device ID e.g. DEV-4A1B)
│   │   ├── Radio controls: Broadcast Beacon (Advertising) and Scan for Devices (Discovery)
│   │   ├── Connection Request Card with 4-digit mutual authentication verification code
│   │   ├── Active Connected Peer Card with peer details and Disconnect action
│   │   ├── Multi-Hop Relay Engine Status Card with live queued counter badge
│   │   ├── Transmit Emergency SOS card: selector of unsent local alerts and Transmit action
│   │   ├── Dynamic permission requester (API 26-30 vs API 31-32 vs API 33+)
│   │   └── RecyclerView bound to NearbyDeviceAdapter for discovered endpoints
│   │
│   ├── AlertsFragment.java
│   │   ├── Public safety alerts board with filter chips (All, Critical, Advisories, Weather)
│   │   ├── RecyclerView bound to AlertAdapter for future and cached broadcasts
│   │   └── Informative empty state card when no verified warnings are present
│   │
│   ├── HistoryFragment.java
│   │   ├── Emergency message audit log bound directly to Room database
│   │   ├── Dynamic category filter chips (All, Medical, Accident, Trapped, Flood, Fire, etc.)
│   │   ├── RecyclerView bound to MessageAdapter for structured packet inspection
│   │   ├── Status pill indicators: LOCAL SAVED, RECEIVED (P2P), RELAYED (HOP X), TRANSFERRED, FAILED
│   │   ├── Detailed Alert Inspection Dialog with full telemetry, hop audit, and deletion option
│   │   └── Clean empty state card when no messages exist in local storage
│   │
│   └── SettingsFragment.java
│       ├── Local node identifier inspection ("NODE-7F84 (Local Node)")
│       ├── Live system location permission verification via ActivityResultLauncher
│       ├── Emergency notification toggles (Audible Siren, Haptic Vibrate, Background Relay)
│       ├── Privacy & security model (Zero Cloud Storage, End-to-End Cryptography)
│       └── Protocol specifications (7 Max Hops, 48h TTL, Nearby Connections transport)
│
├── models
│   ├── EmergencyMessage.java
│   │   ├── Core domain model for SOS signals and relayed peer messages
│   │   └── Fields: messageId, senderDeviceId, senderCallsign, emergencyCategory,
│   │       description, latitude, longitude, locationAccuracy, locationTimestamp,
│   │       createdAt, expiresAt, priority, status, locationStatus, hopCount
│   │
│   ├── EmergencyAlert.java
│   │   ├── Model for civil defense broadcasts and weather advisories
│   │   └── Fields: id, title, category, severity, issuingAuthority, publishedTime,
│   │       expiryTime, affectedArea, instructions, isVerified
│   │
│   └── DeviceInfo.java
│       ├── Model for nearby mesh radio peer nodes
│       └── Fields: deviceId, callsign, rssi, isConnected, distanceEstimate,
│           supportedProtocols, lastSeenTimestamp
│
├── nearby
│   ├── NearbyDevice.java
│   │   ├── Domain model representing a discovered or connected peer endpoint
│   │   ├── ConnectionState enum: DISCOVERED, CONNECTING, WAITING_APPROVAL, CONNECTED, DISCONNECTED
│   │   └── Fields: endpointId, endpointName, state, authDigits, lastSeenTimestamp
│   │
│   ├── NearbyConnectionManager.java
│   │   ├── Core singleton wrapping Google Play Services ConnectionsClient
│   │   ├── Strategy: Strategy.P2P_CLUSTER (M-to-N cluster topology)
│   │   ├── Service ID: com.blackout.emergency.NEARBY_SERVICE
│   │   ├── Manages Advertising, Discovery, Connection Requests, and Approvals
│   │   ├── Exposes sendRawPayload() and sendEmergencySos()
│   │   └── Dispatches lifecycle callbacks to registered UI listeners
│   │
│   ├── SosPayloadSerializer.java
│   │   ├── Encodes EmergencyMessage to compact UTF-8 JSON byte array with relay metadata
│   │   ├── Deserializes incoming bytes into validated EmergencyMessage
│   │   ├── Strict validation: protocol version, payload max size (32KB),
│   │   │   mandatory fields, description length (1000 chars), coordinate bounds,
│   │   │   hop ceilings (max 7), and expiration checks
│   │   └── Custom exception: PayloadValidationException
│   │
│   └── ReceivedMessageHandler.java
│       ├── Asynchronous processor for incoming byte payloads
│       ├── Invokes SosPayloadSerializer and Room duplicate verification
│       ├── Discards duplicate messages safely without overwriting
│       └── Reports ProcessingOutcome (SUCCESS, DUPLICATE_IGNORED, INVALID_PAYLOAD, DATABASE_ERROR)
│
├── relay
│   ├── RelayPolicy.java
│   │   ├── Central policy configuration for store-carry-and-forward relay
│   │   ├── Max hops (7), TTL (48 hours), max retries (5), backoff intervals (30s..15m)
│   │   └── Inventory batch limits (50 items) and transfer batch limits (10 items)
│   │
│   ├── RelayPayloadModels.java
│   │   ├── Protocol models for MESSAGE_INVENTORY, DELIVERY_ACK, and DELIVERY_REJECTED
│   │   ├── Binary UTF-8 JSON serialization and deserialization
│   │   └── Fast peekMessageType() utility
│   │
│   ├── RelayQueueManager.java
│   │   ├── Asynchronous Room coordinator for relay_queue and relay_history
│   │   ├── Prioritized message selection filtered against peer inventories
│   │   ├── Delivery success logging and exponential backoff retry scheduling
│   │   └── Local inventory aggregation
│   │
│   └── RelayEngine.java
│       ├── Master orchestrator coordinating multi-hop relay operations
│       ├── Handles peer connection events, inventory exchange, and batch forwarding
│       ├── Validates incoming SOS messages, persists to Room, and returns DELIVERY_ACK
│       └── Dispatches progress and delivery events to UI listeners
│
├── adapters
│   ├── MessageAdapter.java
│   │   ├── RecyclerView.Adapter for EmergencyMessage items
│   │   ├── Priority color-coding (Red for Critical, Amber for High, Cyan for Moderate)
│   │   ├── Status badge styling (LOCAL SAVED, RECEIVED (P2P), RELAYED (HOP X), TRANSFERRED, FAILED)
│   │   ├── Location display (Coordinates + accuracy or honest "Not Available")
│   │   └── Item click listener for detailed inspection dialog
│   │
│   ├── NearbyDeviceAdapter.java
│   │   ├── RecyclerView.Adapter for discovered NearbyDevice peers
│   │   ├── Status indicator dot (Amber connecting, Green connected, Slate discovered)
│   │   └── Contextual action buttons: Connect, Waiting..., Disconnect
│   │
│   └── AlertAdapter.java
│       ├── RecyclerView.Adapter for EmergencyAlert items
│       ├── Severity badge styling & authority verification badge
│       └── Structured safety instruction container and time formatting
│
├── services
│   ├── LocationService.java
│   │   ├── Direct GPS hardware satellite positioning via LocationManager & GPS_PROVIDER
│   │   ├── 15-second timeout handler for GPS fix with last-known fallback
│   │   ├── Live status reporting: Getting location..., Location acquired,
│   │   │   Using previously recorded location, GPS signal unavailable,
│   │   │   Location permission denied, Location services disabled
│   │   └── Zero mock / fake coordinates policy
│   │
│   └── CommunicationService.java
│       ├── Bridges architectural state with NearbyConnectionManager
│       ├── Reports live discovery and mesh connection states
│       └── Validates offline EmergencyMessage packets
│
└── utils
    ├── Constants.java
    │   ├── 8 Core Emergency Categories (Medical, Road Accident, Trapped, Flood, Fire,
    │   │   Natural Disaster, Personal Safety, Other)
    │   ├── Priority levels (CRITICAL, HIGH, MODERATE, LOW)
    │   ├── Phase 2, 3 & 4 Statuses (LOCAL_SAVED, LOCATION_UNAVAILABLE, STATUS_LOCAL_ONLY,
    │   │   STATUS_TRANSFER_IN_PROGRESS, STATUS_TRANSFERRED, STATUS_RECEIVED, STATUS_RELAYED,
    │   │   STATUS_TRANSFER_FAILED, STATUS_EXPIRED, STATUS_MAX_HOPS_REACHED)
    │   ├── Location Statuses (GETTING, ACQUIRED, CACHED, UNAVAILABLE, DENIED, DISABLED)
    │   ├── Nearby Connections constants: NEARBY_SERVICE_ID, PROTOCOL_VERSION = 1,
    │   │   MESSAGE_TYPE_SOS, MESSAGE_TYPE_INVENTORY, MESSAGE_TYPE_DELIVERY_ACK,
    │   │   MESSAGE_TYPE_DELIVERY_REJECTED, MESSAGE_TYPE_RELAY_STATUS
    │   └── Relay queue statuses: PENDING, IN_PROGRESS, RELAYED_TO_PEER, RETRY_WAIT, EXPIRED, FAILED
    │
    └── DateTimeUtils.java
        ├── Relative human-friendly timestamp formatting ("Just now", "X mins ago", "X hours ago")
        ├── Standardized full date-time formatters ("MMM dd, yyyy HH:mm")
        └── Time of day formatter ("HH:mm:ss")
```

---

## 🧪 Automated Unit Test Suite (`app/src/test/java`) — 77 Passing Tests

- `BackendApiClientTest.java` (13 tests): Pure Java HTTP client, JSON serialization, client validation (UUID, 8 categories, 1000 char description, coordinate bounds, hop limit), response handling (201 Created, 200 Duplicate Accepted, 400 Bad Request, 409 Conflict, 500 Server Error, Network Exception), and health check endpoint.
- `PendingUploadManagerTest.java` (6 tests): Queue coordinator, batch execution, state transitions (`BACKEND_ACCEPTED`, `DUPLICATE_ACCEPTED`, `RETRY_WAIT`, `REJECTED`, `EXPIRED`), and exponential backoff retry calculations.
- `PendingUploadEntityTest.java` (2 tests): Room entity model getters, setters, and constructors.
- `RelayPolicyTest.java` (4 tests): Hop ceiling (7), TTL validation, priority ordering, exponential backoff.
- `RelayPayloadModelsTest.java` (6 tests): Inventory serialization, ACK serialization, rejection serialization, error handling.
- `RelayQueueManagerTest.java` (9 tests): Enqueueing, duplicate suppression, inventory filtering, delivery success logging, retry scheduling.
- `SosPayloadSerializerTest.java` (7 tests): Round-trip encoding, coordinate validation, size/string limits, null guards, status overrides, hop limit checks.
- `ReceivedMessageHandlerTest.java` (7 tests): Ingestion, duplicate rejection, corrupt payload handling, transfer state updates.
- `NearbyDeviceTest.java` (5 tests): State mutations, endpoint mapping, equality.
- `EmergencyMessageTest.java` (6 tests): Domain model rules, TTL, 8 emergency categories.
- `RoomEntityMappingTest.java` (2 tests): Bidirectional Room entity conversions.
- `LocationResultTest.java` (6 tests): Hardware GPS states.
- `EmergencyAlertTest.java` & `DateTimeUtilsTest.java` (4 tests): Civil advisories model and relative timestamp formatters.
