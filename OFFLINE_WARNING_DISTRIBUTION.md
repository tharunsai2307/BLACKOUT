# BLACKOUT — OFFLINE WARNING DISTRIBUTION
## Multi-Hop Store-Carry-and-Forward Mesh Relay Mechanics (Phase 7)

### Executive Overview
During large-scale natural disasters (cyclones, earthquakes, tsunamis), cellular base stations, submarine cables, and power grids often suffer catastrophic failure. The BLACKOUT Offline Warning Distribution system leverages physical human mobility and short-range peer-to-peer radio meshes to propagate digitally signed warnings from distribution gateways deep into isolated offline zones.

---

## 1. Store-Carry-and-Forward Architecture

Instead of requiring continuous end-to-end network paths, devices use opportunistic encounters:

```
[Internet Gateway Node] (e.g. at relief center or high altitude)
           |
           | Downloads active signed warnings from cloud
           v
[Physical Mobility: User travels towards isolated sector]
           |
           | Encounters Device B via Bluetooth / Wi-Fi Direct
           v
[Hop 1: Device B (Offline)]
           |
           | Independent cryptographic signature verification
           | Stored in local Room database
           v
[Physical Mobility: User B travels across river or valley]
           |
           | Encounters Device C via Nearby Connections
           v
[Hop 2: Device C (Deep Offline Zone)]
           |
           | Independent cryptographic signature verification
           | Local user alerted immediately
```

---

## 2. Peer-to-Peer Relay Exchange Protocol

When two devices connect over Google Nearby Connections (`P2P_CLUSTER`), `WarningRelayManager` coordinates a four-phase synchronization:

```
Device A                                                      Device B
   |                                                             |
   |==================== 1. CONNECTION ESTABLISHED ==============|
   |                                                             |
   |--- WARNING_INVENTORY (List of warning IDs, revs, hashes) -->|
   |<-- WARNING_INVENTORY (List of warning IDs, revs, hashes) ---|
   |                                                             |
   |================== 2. DELTA CATALOG ANALYSIS ================|
   | (Identifies warnings B lacks or has older revisions of)     |
   |                                                             |
   |--- WARNING_PAYLOAD (Complete PublicWarning + Signature) --->|
   |                                                             |
   |                                               [Verifies Ed25519]
   |                                               [Persists to Room]
   |                                               [Fires Notifications]
   |                                                             |
   |<-- WARNING_DELIVERY_ACK (status: ACCEPTED) -----------------|
   |                                                             |
   | (Logs successful relay event in warning_relay_history)      |
```

---

## 3. Key Algorithmic Mechanics

### 3.1 Inventory Differential Synchronization
To avoid saturating radio spectrum and draining device batteries, devices exchange lightweight inventory digests:
- Payload consists only of: `(warningId, revision, payloadHash, expiresAt, verificationStatus)`.
- If peer already holds the identical revision or a newer revision, transfer of the heavy payload body is completely skipped.

### 3.2 Independent Verification on Receive
A receiving device **never assumes a warning is valid simply because it was received from a known peer**.
Before insertion into Room or alerting the user, the receiving device executes `WarningSignatureVerifier.verify(incoming)`:
- Reconstructs canonical string deterministically.
- Verifies Ed25519 signature against local trusted key store.
- Evaluates temporal expiration (`System.currentTimeMillis() <= expiresAt`).
- Checks geofence relevance against hardware GPS coordinates.

### 3.3 Duplicate Suppression & Delivery ACKs
- If a device receives a warning revision it already holds, it returns `WARNING_DELIVERY_ACK` with status `DUPLICATE`.
- It does **not** trigger redundant vibrations, sounds, or UI re-renders.
- It updates peer inventory tracking to prevent subsequent redundant send attempts.

### 3.4 In-Transit Tampering Rejection
If a malicious node in the mesh alters the warning headline, instructions, or coordinates:
1. Canonical string reconstruction produces bytes that do not match the signed Ed25519 digest.
2. `WarningSignatureVerifier` fails with `INVALID_SIGNATURE`.
3. The receiving device responds with `WARNING_DELIVERY_REJECTED`.
4. The forged payload is discarded; local database and user UI remain protected.

### 3.5 Revision Rollback Prevention
If an attacker replays an older revision (e.g. attempting to restore a revoked warning or revert an expanded evacuation area):
- The receiving node detects `incoming.revision <= existing.revision`.
- The stale payload is rejected immediately (`ROLLBACK_ATTEMPT`).
- The active, higher revision remains active.
