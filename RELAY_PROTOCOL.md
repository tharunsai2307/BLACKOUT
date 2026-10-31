# BLACKOUT — Store-Carry-and-Forward Relay Protocol Specification (Phase 4)
## Version 1.0 — Ad-Hoc Disaster Communications Protocol

---

## 1. Overview & Problem Definition

In a widespread infrastructure failure or blackout:
- Cellular towers, internet routing, and electrical power grids are non-functional.
- Direct peer-to-peer communication (Phase 3) is limited to the physical radio range of Bluetooth Low Energy (BLE) and Wi-Fi Direct (~30 to 100 metres).
- Emergency responders, victims, and evacuation coordinators may never be simultaneously in radio range of the original sender.

The **BLACKOUT Store-Carry-and-Forward Relay Engine** solves this by treating every participating Android device as an autonomous mobile data carrier:
1. When Device A connects to Device B, Device B receives and persistently stores the SOS in Room.
2. Device B moves through physical space (carrying the message in persistent SQLite storage).
3. Hours later, when Device B discovers and connects to Device C, Device B forwards the stored SOS to Device C.
4. Messages traverse successive physical connections across the field until they reach an incident command post, relief shelter, or medical triage center.

---

## 2. Protocol Enveloping & Message Types

All messages exchanged over the Google Nearby Connections byte stream are versioned JSON payloads governed by `Constants.PROTOCOL_VERSION = 1`.

### 2.1. Message Types
1. **`SOS`**: Primary emergency distress alert.
2. **`MESSAGE_INVENTORY`**: Lightweight catalog of message IDs held in local storage, exchanged immediately upon peer connection.
3. **`DELIVERY_ACK`**: Explicit application-level acknowledgement emitted by a receiver only **after** successful Room database validation and persistence.
4. **`DELIVERY_REJECTED`**: Explicit rejection payload returned when a received message is malformed, expired, or exceeds hop limits.

---

## 3. Detailed Payload Formats

### 3.1. Emergency SOS Payload (`type: "SOS"`)
```json
{
  "protocolVersion": 1,
  "messageType": "SOS",
  "messageId": "7c93e4d8-7f84-48a3-889b-fa37d2096178",
  "originalSenderDeviceId": "DEV-PHONE-A",
  "currentSenderDeviceId": "DEV-PHONE-B",
  "senderDeviceId": "DEV-PHONE-A",
  "senderCallsign": "NODE-7F84",
  "category": "Medical Emergency",
  "description": "Elderly civilian suffering from heatstroke. Requires immediate IV fluids and shade.",
  "priority": "CRITICAL",
  "latitude": 28.6139,
  "longitude": 77.2090,
  "accuracyMeters": 4.2,
  "locationTimestamp": 1740000000000,
  "locationStatus": "Location acquired",
  "createdAt": 1740000000000,
  "expiresAt": 1740172800000,
  "hopCount": 1,
  "maximumHopCount": 7,
  "lastForwardedAt": 1740003600000
}
```

**Invariants**:
- `messageId` (UUID) remains immutable across all hops.
- `originalSenderDeviceId` remains immutable across all hops.
- `currentSenderDeviceId` identifies the immediate upstream relay node.
- `hopCount` starts at `0` upon initial creation and increments by `1` for each validated forwarding hop.
- `createdAt`, `expiresAt`, `latitude`, `longitude`, `accuracyMeters`, and `description` are never modified by relay nodes.

### 3.2. Message Inventory Payload (`type: "MESSAGE_INVENTORY"`)
Immediately upon mutual connection approval, both devices exchange bounded inventories:
```json
{
  "protocolVersion": 1,
  "messageType": "MESSAGE_INVENTORY",
  "senderDeviceId": "DEV-PHONE-B",
  "knownMessageIds": [
    "7c93e4d8-7f84-48a3-889b-fa37d2096178",
    "9f1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d"
  ]
}
```
**Limits**: Bounded to a maximum of `50` message IDs (`RelayPolicy.MAX_INVENTORY_SIZE`) to prevent buffer bloat.

### 3.3. Delivery Acknowledgement Payload (`type: "DELIVERY_ACK"`)
Emitted by the receiver back to the sender only after successful SQLite insertion:
```json
{
  "protocolVersion": 1,
  "messageType": "DELIVERY_ACK",
  "messageId": "7c93e4d8-7f84-48a3-889b-fa37d2096178",
  "receiverDeviceId": "DEV-PHONE-C",
  "acknowledgedAt": 1740003610000,
  "hopNumber": 2
}
```

### 3.4. Delivery Rejection Payload (`type: "DELIVERY_REJECTED"`)
```json
{
  "protocolVersion": 1,
  "messageType": "DELIVERY_REJECTED",
  "messageId": "7c93e4d8-7f84-48a3-889b-fa37d2096178",
  "receiverDeviceId": "DEV-PHONE-C",
  "reason": "MAX_HOPS_EXCEEDED"
}
```

---

## 4. Relay Lifecycle State Machine

```
+---------------------------------------------------------------------------------------------+
|                                    MESSAGE RELAY LIFECYCLE                                  |
+---------------------------------------------------------------------------------------------+

    [New SOS Created] OR [Incoming SOS Persisted]
              |
              v
       (Validate Eligibility)
         - ExpiresAt > Now?
         - HopCount < 7?
              |
              +---> [NO] ----> Reject / Status: EXPIRED or MAX_HOPS_REACHED
              |
             [YES]
              v
     Enqueue in Room: 'relay_queue'
     Relay Status: PENDING
              |
              v
     [Wait for Peer Encounter]
              |
              v
     (Nearby Connection Established & Mutual Code Approved)
              |
              v
     Exchange MESSAGE_INVENTORY
              |
              +---> Peer already has messageId? -----> Skip sending to this peer
              |
             [NO]
              v
     Has peer received this from us before (relay_history)?
              |
              +---> Already delivered to this peer? --> Skip sending
              |
             [NO]
              v
     Select Batch (Max 10, Prioritized: CRITICAL > HIGH > MODERATE > LOW)
              |
              v
     Transmit Payload: type = "SOS", hopCount = hopCount + 1
     Relay Status: IN_PROGRESS
              |
              +--------------------------+---------------------------+
              |                                                      |
              v (Delivery ACK Received)                              v (Timeout / Disconnect / Reject)
     Relay Status: RELAYED_TO_PEER                         Relay Status: RETRY_WAIT
     EmergencyMessage Status: TRANSFERRED                  AttemptCount++
     Log in 'relay_history' Table                          Compute Exponential Backoff (30s..15m)
     Retain in Room for Future Encounters                  NextAttemptAt = Now + Backoff
                                                           (If Attempts >= 5 -> Status: FAILED)
```

---

## 5. Duplicate Suppression Rules

To avoid infinite broadcast storms and bandwidth exhaustion across ad-hoc hops:
1. **Inventory Suppression**: Devices exchange known IDs before payload transfer; identical IDs are excluded from transmission batches.
2. **Delivery History Suppression**: The `relay_history` table records `(messageId, toDeviceId)`. A device will never send the same SOS to the same physical phone more than once per session.
3. **Database Duplicate Idempotence**: If an SOS is received that already exists in Room (`messageDao.getMessageById(messageId) != null`), the database insert is skipped, and a `DELIVERY_ACK` is immediately returned to prevent the sender from repeating.

---

## 6. Security & Integrity Considerations

- **Size Guards**: Hard limit of 32 KB per byte payload (`Constants.MAX_PAYLOAD_SIZE_BYTES`).
- **Input Sanitization**: Maximum 1,000 characters for emergency descriptions; coordinates verified strictly between `[-90, 90]` latitude and `[-180, 180]` longitude.
- **Hop Ceiling**: Hard cutoff at 7 hops to prevent phantom packets from circulating indefinitely.
- **TTL Enforcement**: 48-hour Time-to-Live (`Constants.PACKET_TTL_MILLIS`); expired packets are automatically purged from candidate batches.
