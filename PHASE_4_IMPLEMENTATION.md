# BLACKOUT — Phase 4 Architecture & Implementation Report
## Multi-Hop Store-Carry-and-Forward Relay Engine

This document provides a comprehensive technical overview of the implementation completed for **Phase 4** of the **BLACKOUT** decentralized emergency communication application.

---

## 1. Executive Summary

Phase 4 elevates BLACKOUT from direct point-to-point communication into an autonomous **multi-hop store-carry-and-forward relay network**. 

Because Google's Nearby Connections API establishes direct local peer connections without managing multi-hop topology, Phase 4 implements the entire store-and-forward routing layer natively in Java with Room persistence:
1. **Persistent Relay Queue**: Messages are held in SQLite (`relay_queue`), surviving app kills, reboots, and prolonged disconnections.
2. **Lightweight Inventory Exchange**: Upon connection, peers exchange message ID catalogs (`MESSAGE_INVENTORY`) bounded to 50 items to eliminate duplicate transfers.
3. **Application-Level Acknowledgements**: Senders only mark an SOS as delivered (`RELAYED_TO_PEER`) when an explicit `DELIVERY_ACK` payload is returned confirming Room database persistence on the receiver.
4. **Hop Limit & Expiry Enforcement**: Monotonically increments `hopCount` up to a maximum of 7 (`RelayPolicy.DEFAULT_MAX_HOPS`) and drops packets exceeding the 48-hour Time-to-Live.
5. **Bounded Exponential Retry**: Interrupted or failed transfers are placed in `RETRY_WAIT` with backoff intervals ranging from 30 seconds to 15 minutes.
6. **Strict Architectural Integrity**: 100% Java 11, native XML layouts, zero Kotlin, zero Compose, zero web views, zero cloud/Supabase dependencies, and 53 passing automated unit tests.

---

## 2. Technology Stack & Database Migrations

### 2.1. Room Database Version 2
In `BlackoutDatabase.java`:
```java
@Database(
        entities = {
                EmergencyMessageEntity.class,
                RelayQueueEntity.class,
                RelayHistoryEntity.class
        },
        version = 2,
        exportSchema = false
)
```

### 2.2. New Database Entities
- **`RelayQueueEntity`** (`relay_queue`):
  - `messageId` (String, PK)
  - `relayStatus` (String, indexed: `PENDING`, `IN_PROGRESS`, `RELAYED_TO_PEER`, `RETRY_WAIT`, `EXPIRED`, `MAX_HOPS_REACHED`, `FAILED`)
  - `queuedAt` (long)
  - `nextAttemptAt` (long, indexed)
  - `attemptCount` (int)
  - `lastAttemptAt` (long)
  - `lastError` (String)
  - `priority` (String)
- **`RelayHistoryEntity`** (`relay_history`):
  - `id` (long, PK auto-generated)
  - `messageId` (String, indexed)
  - `fromDeviceId` (String)
  - `toDeviceId` (String, indexed)
  - `hopNumber` (int)
  - `transferStatus` (String: `RELAYED_TO_PEER`)
  - `timestamp` (long)

---

## 3. Architecture & Data Flow

```
+-------------------------------------------------------------------------------------------------+
|                                           PHONE A (Originator)                                  |
|   1. Creates SOS (hopCount = 0)                                                                 |
|   2. Saved in Room 'emergency_messages'                                                         |
|   3. Enqueued in Room 'relay_queue' (Status: PENDING)                                           |
|   4. Connects to Phone B                                                                        |
|   5. Exchanges MESSAGE_INVENTORY with Phone B                                                   |
|   6. Transmits SOS with hopCount = 1                                                            |
|   7. Receives DELIVERY_ACK from Phone B                                                         |
|   8. Updates local queue to RELAYED_TO_PEER; logs in 'relay_history'                            |
+-------------------------------------------------------------------------------------------------+
                                               |
                                     (Hop 1 Link - BLE / P2P)
                                               v
+-------------------------------------------------------------------------------------------------+
|                                           PHONE B (Relay Carrier)                               |
|   1. Receives SOS payload from Phone A                                                          |
|   2. Validates schema, hops (1 < 7), and expiry                                                 |
|   3. Saves into Room 'emergency_messages' as RECEIVED_FROM_NEARBY_DEVICE                         |
|   4. Automatically enqueues in 'relay_queue' as PENDING for future hops                         |
|   5. Emits DELIVERY_ACK to Phone A                                                              |
|   6. Disconnects from Phone A (Carries SOS physically in pocket across hours/distance)         |
|   7. Later encounters Phone C (Nearby connection established)                                   |
|   8. Exchanges MESSAGE_INVENTORY with Phone C                                                   |
|   9. Discovers Phone C lacks this messageId                                                     |
|  10. Transmits SOS with hopCount = 2 (original sender: Phone A, current: Phone B)               |
|  11. Receives DELIVERY_ACK from Phone C                                                         |
|  12. Updates local queue to RELAYED_TO_PEER; logs in 'relay_history'                            |
+-------------------------------------------------------------------------------------------------+
                                               |
                                     (Hop 2 Link - BLE / P2P)
                                               v
+-------------------------------------------------------------------------------------------------+
|                                           PHONE C (Final Recipient / Evac Center)               |
|   1. Receives SOS payload from Phone B                                                          |
|   2. Validates schema, hops (2 < 7), and expiry                                                 |
|   3. Saves into Room as RECEIVED_FROM_NEARBY_DEVICE                                             |
|   4. Displays in Message History with badge: "RELAYED (HOP 2)"                                  |
|   5. Detail dialog displays original Phone A coordinates, timestamp, and Phone A callsign       |
|   6. Emits DELIVERY_ACK back to Phone B                                                         |
+-------------------------------------------------------------------------------------------------+
```

---

## 4. Key Components Implemented

### 4.1. `RelayPolicy.java` (`com.blackout.emergency.relay`)
Centralizes all policy thresholds:
- `DEFAULT_MAX_HOPS = 7`
- `DEFAULT_TTL_MILLIS = 48 hours`
- `MAX_RETRY_ATTEMPTS = 5`
- `BASE_RETRY_BACKOFF_MILLIS = 30 seconds`
- `MAX_RETRY_BACKOFF_MILLIS = 15 minutes`
- `MAX_INVENTORY_SIZE = 50`
- `MAX_TRANSFER_BATCH_SIZE = 10`
- `isEligibleForRelay(EmergencyMessage)`: Validates hop limit and expiry time.
- `calculateBackoffMillis(attemptCount)`: Bounded exponential backoff.

### 4.2. `RelayPayloadModels.java` (`com.blackout.emergency.relay`)
Encapsulates binary JSON serialization for:
- `InventoryPayload`: Protocol version, senderDeviceId, and list of known UUIDs.
- `DeliveryAckPayload`: Protocol version, messageId, receiverDeviceId, acknowledgedAt, and hopNumber.
- `DeliveryRejectPayload`: Protocol version, messageId, receiverDeviceId, and reason.
- `peekMessageType(byte[] bytes)`: Fast detection of packet type without full deserialization.

### 4.3. `RelayQueueManager.java` (`com.blackout.emergency.relay`)
Asynchronous Room manager executing operations off the UI thread:
- `enqueueForRelay(EmergencyMessage, callback)`: Idempotent queue insertion.
- `getEligibleMessagesForPeer(peerDeviceId, peerKnownIds, limit, callback)`: Filters out messages already held by peer or previously delivered to that device.
- `recordDeliverySuccess(...)`: Marks queue as `RELAYED_TO_PEER` and writes record to `relay_history`.
- `recordDeliveryFailure(...)`: Calculates exponential backoff and updates queue to `RETRY_WAIT`.
- `getLocalInventoryIds(callback)`: Retrieves local UUIDs to construct inventories.

### 4.4. `RelayEngine.java` (`com.blackout.emergency.relay`)
Orchestrates multi-hop operations:
- Bound to `NearbyConnectionManager` connection events.
- On peer connected: Immediately sends local `MESSAGE_INVENTORY`.
- On inventory received: Queries `RelayQueueManager` and forwards eligible SOS packets in batches.
- On incoming SOS: Validates and persists in Room, then automatically returns `DELIVERY_ACK` (or `DELIVERY_REJECTED`).
- On delivery ACK received: Records delivery success and fires UI progress notifications.

---

## 5. Automated Unit Test Results

Gradle test execution:
```bash
./gradlew testDebugUnitTest
```
**Results**:
- **53 passed, 0 skipped, 0 failed**.
- New test suites:
  - `RelayPolicyTest` (6 tests): Validates hop ceilings, expiry logic, priority rankings, and backoff progression.
  - `RelayPayloadModelsTest` (6 tests): Validates inventory, ACK, and rejection round-trips and error handling.
  - `RelayQueueManagerTest` (6 tests): Validates enqueueing, inventory filtering, duplicate suppression, delivery updates, and retry scheduling.
  - All Phase 1, Phase 2, and Phase 3 tests (35 tests) continue to pass without modification.

Debug APK build:
```bash
./gradlew assembleDebug
```
**Result**: Build finished successfully.
