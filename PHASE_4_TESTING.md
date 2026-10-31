# BLACKOUT — Phase 4 Three-Device Physical Testing Guide
## Validating Multi-Hop Store-Carry-and-Forward Relay on Physical Android Devices

This guide provides step-by-step instructions for testing the **BLACKOUT** Phase 4 store-carry-and-forward relay engine on three physical Android devices without cellular service, Wi-Fi access points, or internet connectivity.

---

## 📋 Required Equipment & Topology

1. **Three physical Android smartphones**:
   - **Phone A** (Originator): e.g. Samsung / Pixel / Motorola running Android 8.0+.
   - **Phone B** (Carrier / Relay): Android 8.0+.
   - **Phone C** (Final Recipient / Evacuation Center): Android 8.0+.
2. **Topology Constraint**:
   - **Phone A and Phone C must NEVER be directly connected or within radio range during the test.**
   - Physical separation or distance ensure that communication can only succeed if Phone B carries and forwards the SOS.
3. **Debug APK**:
   - `app/build/outputs/apk/debug/app-debug.apk` built via `./gradlew assembleDebug`.

---

## ✈️ Step 1: Radio & Offline Environment Setup

On **all three phones** (Phone A, Phone B, and Phone C):
1. **Disable Mobile Data** (Settings > Cellular > Mobile Data: OFF).
2. **Disconnect from Wi-Fi Routers / Internet** (Forget Wi-Fi networks).
3. **Ensure Bluetooth is ON and Location Services (GPS) are ON**.
4. Install `app-debug.apk` on Phone A, Phone B, and Phone C:
   ```bash
   adb -s <DEVICE_A_SERIAL> install -r app/build/outputs/apk/debug/app-debug.apk
   adb -s <DEVICE_B_SERIAL> install -r app/build/outputs/apk/debug/app-debug.apk
   adb -s <DEVICE_C_SERIAL> install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 🔬 Test 1: SOS Creation on Phone A (Originator)

1. Open **BLACKOUT** on **Phone A**.
2. Open the **Emergency SOS** tab.
3. Select emergency category: **Medical Emergency**.
4. Enter description:
   > *"Elderly resident experiencing severe dehydration and cardiac distress. Needs immediate triage."*
5. Tap **Acquire GPS Location**: Acquire coordinates (e.g. `28.6139° N, 77.2090° E`).
6. Tap **Broadcast Emergency SOS** and confirm.
7. Open **Message History** on Phone A:
   - Notice the status badge reads: `LOCAL_SAVED`.
   - Hop count is `0`.
   - The message is automatically placed into Phone A's Room `relay_queue` as `PENDING`.
8. Open the **Nearby Network** tab on Phone A:
   - The *Multi-Hop Relay Engine* card displays: `1 QUEUED`.

---

## 🔬 Test 2: First Hop (Phone A ↔ Phone B)

1. Keep Phone C powered OFF or placed in another room far out of radio range.
2. On **Phone A**:
   - Navigate to *Nearby Network* and tap **Broadcast Beacon (Start Advertising)**.
3. On **Phone B**:
   - Navigate to *Nearby Network* and tap **Scan for Devices (Start Discovering)**.
4. Phone B discovers Phone A. Tap **Connect** on Phone B.
5. Confirm the 4-digit mutual authentication code matches on both screens and tap **Accept** on both devices.
6. **Automatic Multi-Hop Protocol Execution**:
   - Once connected, Phone A and Phone B automatically exchange `MESSAGE_INVENTORY`.
   - Phone A detects that Phone B does not have the SOS message.
   - Phone A automatically transmits the SOS with `hopCount = 1` and `originalSenderDeviceId = Phone A`.
   - Phone B validates the packet, verifies that hops (1 < 7) and expiry are valid, and inserts the message into its local Room database.
   - Phone B automatically enqueues the message into its own `relay_queue` for future encounters.
   - Phone B emits a `DELIVERY_ACK` payload back to Phone A.
   - Phone A receives the `DELIVERY_ACK`, updates its queue to `RELAYED_TO_PEER`, logs the event in `relay_history`, and displays a toast:
     > *"SOS successfully delivered to peer (Hop: 1)"*
7. **Verify Phone B**:
   - Open **Message History** on Phone B:
     - The SOS appears with status badge: `RELAYED (HOP 1)`.
     - Detail view confirms original sender is Phone A and fix coordinates are preserved.
8. On Phone A, tap **Disconnect Peer**.

---

## 🔬 Test 3: Carry & Persistence (Phone B)

1. Take **Phone B** and walk away from Phone A (or disable Phone A's radio).
2. On **Phone B**:
   - Open *Nearby Network*: The *Multi-Hop Relay Engine* card displays: `1 QUEUED`.
3. **App Kill Test**:
   - Swipe away and force kill the BLACKOUT app on Phone B.
   - Reopen BLACKOUT on Phone B.
   - Open *Nearby Network*: The relay queue survives app restart and still displays `1 QUEUED`.

---

## 🔬 Test 4: Second Hop (Phone B ↔ Phone C)

1. Bring **Phone B** within radio range of **Phone C** (Phone A remains isolated).
2. On **Phone B**:
   - Open *Nearby Network* and tap **Broadcast Beacon (Start Advertising)**.
3. On **Phone C**:
   - Open *Nearby Network* and tap **Scan for Devices (Start Discovering)**.
4. Phone C discovers Phone B. Tap **Connect** on Phone C.
5. Verify matching 4-digit code and tap **Accept** on both devices.
6. **Automatic Multi-Hop Protocol Execution**:
   - Phone B and Phone C exchange `MESSAGE_INVENTORY`.
   - Phone B detects that Phone C lacks the SOS message originally created by Phone A.
   - Phone B automatically transmits the SOS with `hopCount = 2`, `originalSenderDeviceId = Phone A`, and `currentSenderDeviceId = Phone B`.
   - Phone C validates the packet, verifies that hops (2 < 7), and saves the message into its local Room database.
   - Phone C returns a `DELIVERY_ACK` payload back to Phone B.
   - Phone B updates its queue to `RELAYED_TO_PEER` and logs the delivery in `relay_history`.
7. Disconnect Phone B and Phone C.

---

## 🔬 Test 5: End-to-End Multi-Hop Verification (Phone C)

1. On **Phone C** (which never communicated with Phone A):
   - Open **Message History**.
2. **Verify Field Integrity**:
   - **Originating Callsign**: Exact callsign from Phone A (`NODE-XXXX`).
   - **Originating Device ID**: Exact device ID of Phone A (`DEV-XXXX`).
   - **Emergency Category**: `Medical Emergency`.
   - **Description**: Exact text entered on Phone A: *"Elderly resident experiencing severe dehydration..."*
   - **GPS Coordinates**: Exact coordinates captured on Phone A (e.g. `28.6139° N, 77.2090° E`).
   - **Status Badge**: **`RELAYED (HOP 2)`**.
   - **Hop Count**: Confirmed as `2 / 7`.
3. Open the Alert Details dialog on Phone C to review the full forensic audit trail.

---

## 🔬 Test 6: Duplicate Suppression & Loop Prevention

1. Reconnect **Phone B** to **Phone C**.
2. Observe the Relay Engine status:
   - Phone B and Phone C exchange `MESSAGE_INVENTORY`.
   - Phone C's inventory now includes the SOS message ID.
   - The status updates: *"All messages already synchronized with peer"*.
   - **Result**: Zero duplicate packets transmitted. Phone C's database count remains unchanged.

---

## 🔬 Test 7: Interrupted Transfer & Retry Backoff

1. While transmitting a payload between Phone B and Phone C, toggle Bluetooth OFF on Phone C to simulate sudden signal loss.
2. Observe Phone B:
   - The delivery ACK is not received.
   - Phone B's `RelayQueueManager` logs the attempt failure.
   - Queue item transitions to `RETRY_WAIT` with attempt count incremented.
   - Next attempt scheduled via exponential backoff (30s).
   - The message remains safe in `relay_queue` and is not lost or dropped.
3. Reconnect Phone B and Phone C after backoff expires:
   - The message is successfully forwarded upon reconnection.
