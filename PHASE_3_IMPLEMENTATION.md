# BLACKOUT — Phase 3 Architecture & Implementation Report
## Real Offline Device-to-Device Communication via Google Nearby Connections

This document provides a comprehensive technical overview of the implementation completed for **Phase 3** of the **BLACKOUT** decentralized emergency communication application.

---

## 1. Executive Summary

Phase 3 introduces **real, offline device-to-device peer communication** between physical Android devices using the **Google Nearby Connections API** (`com.google.android.gms:play-services-nearby:19.3.0`).

Key capabilities delivered in Phase 3:
1. **Zero Internet Requirement**: Communication functions over local physical radios (Bluetooth Low Energy, Bluetooth Classic, Wi-Fi Direct / Local Hotspot) managed automatically by the Nearby Connections engine.
2. **Standardized P2P Topology**: Uses Google's `Strategy.P2P_CLUSTER`, an M-to-N cluster topology ideal for ad-hoc emergency networks.
3. **Secure Two-Sided Handshake**: Implements explicit user-controlled connection requests and acceptance with matching 4-digit verification authentication codes (`authDigits`) to prevent unauthorized interception.
4. **Binary Payload Serialization**: Compact, secure UTF-8 JSON byte encoding (`SosPayloadSerializer`) with schema enforcement, size guards (maximum 32 KB), and character sanitization.
5. **Duplicate-Proof Local Room Persistence**: Incoming SOS packets are parsed, validated, and stored permanently in the receiving phone's Room database as `RECEIVED_FROM_NEARBY_DEVICE`. If a message with the same `messageId` was already received, it is discarded safely without overwriting.
6. **Transmission Status Tracking**: The transmitting phone tracks payload progress, updating the local database record to `TRANSFER_IN_PROGRESS` and subsequently to `TRANSFERRED_TO_NEARBY_DEVICE` upon transfer confirmation, or `TRANSFER_FAILED` upon payload failure or unexpected peer disconnect.
7. **Strict Architectural Integrity**: 100% Java 11, native XML layouts, zero Kotlin, zero Compose, zero web views, zero mock/simulated connections, and complete test coverage with 35 passing unit tests.

---

## 2. Technology Stack & Dependencies

### 2.1. Dependency Updates
In `gradle/libs.versions.toml`:
```toml
[versions]
playServicesNearby = "19.3.0"

[libraries]
play-services-nearby = { group = "com.google.android.gms", name = "play-services-nearby", version.ref = "playServicesNearby" }
```

In `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.play.services.nearby)
    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20231013")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
```

*Note*: Version `19.3.0` was selected over `19.5.1` to maintain 100% pure Java toolchain compatibility and avoid Kotlin compiler work action metadata issues during build execution.

---

## 3. Permissions & Device Compatibility

Nearby Connections requires distinct runtime permissions depending on the Android API level running on each physical device.

### 3.1. Manifest Configuration (`AndroidManifest.xml`)
```xml
<!-- Base permissions (API 26 - API 30) -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />

<!-- Modern Nearby permissions (API 31+ Android 12+) -->
<!-- NOTE: Do NOT use neverForLocation because Nearby Connections requires location derivation -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Nearby Wi-Fi Devices (API 33+ Android 13+) -->
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />
```

### 3.2. Runtime Permission Requester
`NearbyFragment.java` dynamically checks and requests the exact permissions needed for the host device:
- On **Android 13+ (API 33+)**: Requests `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `NEARBY_WIFI_DEVICES`, and `ACCESS_FINE_LOCATION`.
- On **Android 12 (API 31-32)**: Requests `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, and `ACCESS_FINE_LOCATION`.
- On **Android 8.0 - 11 (API 26-30)**: Requests `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`.

---

## 4. Architecture & Data Flow

```
+-------------------------------------------------------------------------------------------------+
|                                           PHONE A (Sender)                                      |
|                                                                                                 |
|   +------------------------------------+               +------------------------------------+   |
|   |            NearbyFragment          | <-----------> |         NearbyConnectionManager    |   |
|   |  - Start Advertising               |               |  - Strategy: P2P_CLUSTER           |   |
|   |  - Approve incoming request        |               |  - Service ID: com.blackout...     |   |
|   |  - Select outgoing SOS from Room   |               |  - Send Payload.fromBytes()        |   |
|   +-----------------+------------------+               +------------------+-----------------+   |
|                     |                                                     |                     |
|                     v                                                     | (Nearby Radio       |
|   +------------------------------------+                                  |  BLE / Wi-Fi Direct)|
|   |         EmergencyRepository        |                                  |                     |
|   |  - Status: TRANSFER_IN_PROGRESS    |                                  |                     |
|   |  - Status: TRANSFERRED             |                                  |                     |
|   +------------------------------------+                                  |                     |
+---------------------------------------------------------------------------|---------------------+
                                                                            |
                                            OFFLINE RADIO LINK              |
                                            (Zero Internet)                 |
                                                                            v
+-------------------------------------------------------------------------------------------------+
|                                           PHONE B (Receiver)                                    |
|                                                                                                 |
|   +------------------------------------+               +------------------------------------+   |
|   |            NearbyFragment          | <-----------> |         NearbyConnectionManager    |   |
|   |  - Start Discovery                 |               |  - Strategy: P2P_CLUSTER           |   |
|   |  - Discovers Phone A endpoint      |               |  - PayloadCallback: onPayloadRecv  |   |
|   |  - Send Connection Request         |               +------------------+-----------------+   |
|   |  - Approve connection code         |                                  |                     |
|   +------------------------------------+                                  |                     |
|                                                                           v                     |
|                                                        +------------------------------------+   |
|                                                        |        ReceivedMessageHandler      |   |
|                                                        |  - SosPayloadSerializer.deser()    |   |
|                                                        |  - Strict schema & size validation |   |
|                                                        +------------------+-----------------+   |
|                                                                           |                     |
|                                                                           v                     |
|                                                        +------------------------------------+   |
|                                                        |        EmergencyRepository         |   |
|                                                        |  - Check duplicate messageId       |   |
|                                                        |  - Insert if unique (Room DB)      |   |
|                                                        |  - Status: RECEIVED_P2P            |   |
|                                                        +------------------------------------+   |
+-------------------------------------------------------------------------------------------------+
```

---

## 5. Detailed Component Implementation

### 5.1. `NearbyConnectionManager.java` (`com.blackout.emergency.nearby`)
Central singleton managing the `ConnectionsClient` provided by Google Play Services:
- **Strategy**: `Strategy.P2P_CLUSTER` — optimizes for multiple connections without internet access.
- **Service ID**: `com.blackout.emergency.NEARBY_SERVICE` — ensures only BLACKOUT installations discover one another.
- **Endpoint Name Format**: Combines hardware device ID and tactical node callsign: e.g., `DEV-4A1B|NODE-7F84`.
- **Advertising**:
  ```java
  AdvertisingOptions options = new AdvertisingOptions.Builder()
          .setStrategy(Strategy.P2P_CLUSTER)
          .build();
  connectionsClient.startAdvertising(localEndpointName, Constants.NEARBY_SERVICE_ID, connectionLifecycleCallback, options);
  ```
- **Discovery**:
  ```java
  DiscoveryOptions options = new DiscoveryOptions.Builder()
          .setStrategy(Strategy.P2P_CLUSTER)
          .build();
  connectionsClient.startDiscovery(Constants.NEARBY_SERVICE_ID, endpointDiscoveryCallback, options);
  ```
- **Authentication Handshake**:
  When `onConnectionInitiated(endpointId, connectionInfo)` is invoked:
  - Generates a 4-digit human-readable verification token (`connectionInfo.getAuthenticationDigits()`).
  - Stores the candidate peer in state `WAITING_APPROVAL`.
  - Notifies UI listeners so both users can inspect and compare the 4-digit code.
  - User can tap **Accept** (`connectionsClient.acceptConnection(...)`) or **Reject** (`connectionsClient.rejectConnection(...)`).
- **Payload Transmission & Feedback**:
  ```java
  Payload payload = Payload.fromBytes(payloadBytes);
  connectionsClient.sendPayload(endpointId, payload);
  ```
  Tracks `PayloadCallback.onPayloadTransferUpdate` to verify complete byte delivery (`PayloadTransferUpdate.Status.SUCCESS`) or handle errors (`FAILURE`).

### 5.2. `SosPayloadSerializer.java`
Responsible for serialization, deserialization, and defensive validation of SOS alerts across the wireless link:
- **Encoding**: Converts `EmergencyMessage` fields into a UTF-8 JSON byte array.
- **Protocol Enveloping**:
  ```json
  {
    "proto": 1,
    "type": "SOS",
    "id": "e4b2d1c0-...",
    "senderDev": "DEV-PHONE-A",
    "senderCall": "NODE-7F84",
    "cat": "MEDICAL",
    "desc": "Injured civilian requiring first aid",
    "prio": "CRITICAL",
    "lat": 28.6139,
    "lon": 77.2090,
    "acc": 4.5,
    "locTime": 1740000000000,
    "locStatus": "ACQUIRED",
    "created": 1740000000000,
    "ttl": 1740172800000,
    "hops": 0
  }
  ```
- **Defensive Safeguards**:
  - Rejects payloads exceeding `Constants.MAX_PAYLOAD_SIZE_BYTES` (32 KB) to prevent memory exhaustion attacks.
  - Rejects empty, null, or corrupted byte arrays.
  - Enforces mandatory protocol fields (`proto == 1`, `type == "SOS"`, valid UUID `id`, non-empty `senderDev`, `cat`, and `desc`).
  - Enforces maximum description length (`Constants.MAX_DESCRIPTION_LENGTH = 1000`).
  - Enforces coordinate validity (-90 <= lat <= 90, -180 <= lon <= 180).
  - Automatically resets incoming status to `STATUS_RECEIVED` (`RECEIVED_FROM_NEARBY_DEVICE`).

### 5.3. `ReceivedMessageHandler.java`
Processes incoming bytes asynchronously off the main thread:
1. Calls `SosPayloadSerializer.deserialize(bytes)`.
2. Interacts with `EmergencyRepository.insertReceivedMessage(message, callback)`.
3. Verifies if the message already exists in the local Room database (`messageDao.getMessageById(messageId)`).
4. If unique: Inserts record with status `RECEIVED_FROM_NEARBY_DEVICE` and fires `ProcessingOutcome.SUCCESS`.
5. If duplicate: Discards incoming message and fires `ProcessingOutcome.DUPLICATE_IGNORED`.
6. Dispatches callbacks safely back to UI handlers.

### 5.4. Database & DAO Updates (`EmergencyMessageDao.java` & `EmergencyRepository.java`)
- `updateStatus(String id, String status)`: Modifies the status of a message.
- `getOutgoingMessages()`: Retrieves all locally-created SOS alerts eligible for sending (`status != 'RECEIVED_FROM_NEARBY_DEVICE'`).
- `insertReceivedMessage(EmergencyMessage message, Callback<Boolean> callback)`: Atomic duplicate-check and persistence for incoming packets.

### 5.5. Message Status Lifecycle
- `LOCAL_SAVED`: Created locally in Phase 2.
- `TRANSFER_IN_PROGRESS`: Transmission initiated to a connected peer.
- `TRANSFERRED_TO_NEARBY_DEVICE`: Successfully transmitted and acknowledged by the Nearby Connections radio layer.
- `TRANSFER_FAILED`: Transmission failed or peer disconnected mid-stream.
- `RECEIVED_FROM_NEARBY_DEVICE`: Received from a peer and stored permanently in the receiver's Room database.

### 5.6. UI / UX Updates (`NearbyFragment.java` & `fragment_nearby.xml`)
The Nearby Network tab was redesigned into a live mission control interface:
- **Local Identity Card**: Displays current node callsign (e.g. `NODE-7F84`) and device identifier (e.g. `DEV-4A1B`).
- **Radio Controls**: Independent toggles for **Advertising** ("Broadcast Beacon") and **Discovery** ("Scan for Devices").
- **Live Discovered Endpoints**: RecyclerView displaying detected nearby devices with endpoint ID, callsign, and an action button to initiate connection.
- **Connection Approval Card**: When a connection request arrives, presents the peer's name and a highlighted 4-digit verification code with **Accept** and **Reject** buttons.
- **Active Connected Peer Card**: Shows current connected peer details, connection quality indicator, and a **Disconnect** button.
- **SOS Message Transfer Card**: Dropdown/selector listing unsent emergency alerts stored locally in Room. Clicking **Transmit Alert** sends the binary payload to the connected peer with live progress feedback.

---

## 6. Verification and Test Results

### 6.1. Automated Unit Test Suite
Run command:
```bash
./gradlew testDebugUnitTest
```
**Results**:
- Total tests executed: **35**
- Passed: **35**
- Skipped: **0**
- Failed: **0**

Key test cases verified:
- `SosPayloadSerializerTest`:
  - `testSerializeAndDeserializeCompleteMessageWithLocation`: Round-trip fidelity of all fields.
  - `testSerializeAndDeserializeWithoutLocation`: Handles null coordinates safely.
  - `testDeserializeRejectsNullOrEmptyPayload`: Validates empty byte rejection.
  - `testDeserializeRejectsUnsupportedProtocolVersion`: Guards against incompatible versions.
  - `testDeserializeRejectsMissingMandatoryFields`: Missing ID, category, or description throws `PayloadValidationException`.
  - `testDeserializeRejectsExceededPayloadSize`: Payload > 32 KB safely rejected.
  - `testDeserializeRejectsOversizedDescription`: Description > 1000 characters safely rejected.
  - `testDeserializeRejectsInvalidCoordinates`: Coordinates out of range rejected.
  - `testDeserializeForcesReceivedStatus`: Status always set to `RECEIVED_FROM_NEARBY_DEVICE`.
- `ReceivedMessageHandlerTest`:
  - `testProcessValidIncomingPayload`: Successful parsing and insertion into Room.
  - `testDuplicateMessageIsIgnored`: Duplicate UUID rejected and not overwritten.
  - `testCorruptPayloadReturnsInvalidPayload`: Invalid JSON returns `INVALID_PAYLOAD`.
  - `testTransferStateUpdates`: Validates status transitions (`LOCAL_SAVED` -> `TRANSFER_IN_PROGRESS` -> `TRANSFERRED` -> `TRANSFER_FAILED`).
- `NearbyDeviceTest`:
  - State machine transitions, equality checks, and connection state verification.
- `EmergencyMessageTest` & `DatabaseMappingTest`:
  - Domain models, priority calculation, and Room entity conversions.

### 6.2. Build Verification
Run command:
```bash
./gradlew assembleDebug
```
**Result**: Build finished successfully without warnings or errors. Debug APK generated.
