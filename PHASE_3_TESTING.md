# BLACKOUT — Phase 3 Two-Device Offline Testing Guide
## Validating Peer-to-Peer SOS Communication on Physical Android Devices

This guide provides step-by-step instructions for testing the **BLACKOUT** Phase 3 offline device-to-device communication system on two physical Android devices without cellular service or internet access.

---

## 📋 Required Equipment & Prerequisites

1. **Two physical Android smartphones** (Phone A and Phone B):
   - Running Android 8.0 (API level 26) or newer (Android 8, 9, 10, 11, 12, 13, 14, 15, or 16).
   - Google Play Services installed and updated.
   - Physical Bluetooth and Wi-Fi hardware enabled.
2. **Debug APK**:
   - Built via `./gradlew assembleDebug`.
   - Located at: `app/build/outputs/apk/debug/app-debug.apk`.

---

## ✈️ Step 1: Physical Radio & Offline Environment Setup

To strictly prove that BLACKOUT functions completely offline without cellular towers, Wi-Fi routers, or internet connections:

1. On **both** Phone A and Phone B:
   - **Disable Cellular Mobile Data** (Settings > Mobile Network > Mobile Data: OFF).
   - **Disconnect from any Wi-Fi Access Point / Router** (Forget networks or toggle Wi-Fi OFF).
   - Alternatively: Enable **Airplane Mode**, then explicitly toggle **Bluetooth: ON** and **Wi-Fi: ON** (Wi-Fi must be enabled for Wi-Fi Direct and Local Hotspot, but must NOT be connected to an internet access point).
   - Ensure **Location Services (GPS)** are enabled (required for hardware Bluetooth Low Energy scanning and satellite coordinates).

---

## 📲 Step 2: Install and Grant Runtime Permissions

1. Install `app-debug.apk` on both devices:
   ```bash
   adb -s <DEVICE_A_SERIAL> install -r app/build/outputs/apk/debug/app-debug.apk
   adb -s <DEVICE_B_SERIAL> install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. Open **BLACKOUT** on both phones.
3. When prompted, grant the necessary permissions:
   - **Location Permission**: Select *"While using the app"* and *"Precise"*.
   - **Nearby Devices / Bluetooth Permission** (Android 12+): Tap *"Allow"*.
4. Verify on both phones:
   - Go to the **Settings** tab.
   - Confirm that the node callsign is displayed (e.g., `NODE-7F84` on Phone A, `NODE-3E2A` on Phone B).
   - Confirm Location Status reads: `Location Permission: Granted`.

---

## 📝 Step 3: Create an Emergency SOS Beacon on Phone A

1. On **Phone A**, open the **Emergency SOS** tab.
2. Select an emergency category (e.g., **Medical** or **Trapped**).
3. Type an emergency description:
   > *"Civilian trapped on 2nd floor after staircase collapse. Medical supplies and extraction tools needed."*
4. Tap **Acquire GPS Location**:
   - Wait for satellite fix.
   - Coordinates (latitude, longitude) and accuracy in metres will appear.
5. Tap **Broadcast Emergency SOS**:
   - Review the pre-save confirmation dialog showing Priority, Category, Coordinates, and Accuracy.
   - Tap **Confirm & Broadcast**.
6. A success dialog will confirm the SOS has been saved to the local Room database.
7. Open the **History** tab on Phone A:
   - Verify the alert appears with badge `LOCAL ONLY` (or `LOCAL SAVED`).

---

## 📡 Step 4: Discover and Connect the Devices

1. On **Phone A** (Broadcaster):
   - Navigate to the **Nearby Network** tab.
   - Under *Radio Controls*, tap **Broadcast Beacon (Advertise)**.
   - The status banner will display: `Broadcasting beacon... Waiting for nearby devices to discover this node.`

2. On **Phone B** (Receiver / Explorer):
   - Navigate to the **Nearby Network** tab.
   - Under *Radio Controls*, tap **Scan for Devices (Discover)**.
   - The status banner will display: `Scanning for nearby BLACKOUT nodes...`

3. **Endpoint Discovery**:
   - Within 5–15 seconds, Phone A will appear in Phone B's *Discovered Devices* list.
   - The item card will display Phone A's node callsign (e.g. `NODE-7F84`).

4. **Initiate Connection**:
   - On Phone B, tap **Connect** next to Phone A's device entry.
   - Both phones will transition to the connection handshake.

5. **Mutual Verification Code Approval**:
   - An *Incoming Connection Request* card will appear simultaneously on both screens.
   - Verify that the **4-digit verification code** (e.g. `4829`) matches exactly on both Phone A and Phone B.
   - Tap **Accept** on Phone A, then tap **Accept** on Phone B.

6. **Connected State**:
   - Both devices will display the **Connected Peer** card showing:
     - Peer node callsign and endpoint identifier.
     - Connection status: `CONNECTED`.
     - Action button: `Disconnect`.

---

## 🚀 Step 5: Transmit the SOS Message from Phone A to Phone B

1. On **Phone A** (Nearby Network tab):
   - Locate the **Transmit Emergency SOS** card at the top of the screen.
   - Tap the message dropdown selector.
   - Select the SOS alert created in Step 3.
   - Tap **Transmit Alert to Peer**.

2. **Transmission Feedback on Phone A**:
   - The status will immediately update to `Sending SOS payload...`.
   - When the byte transfer completes, a confirmation alert will appear:
     > *"SOS alert successfully transmitted to peer [NODE-3E2A]."*
   - The message status in Phone A's database automatically updates to `TRANSFERRED_TO_NEARBY_DEVICE`.

3. **Reception & Ingestion on Phone B**:
   - Phone B's screen will immediately show a notification / snackbar:
     > *"Incoming SOS Received: [Medical] Civilian trapped on 2nd floor..."*
   - Phone B's background handler validates the JSON payload schema, enforces the size boundary, checks for duplicate UUIDs, and writes the record into Room.

---

## 🔍 Step 6: Verify Persistence and Room Database Storage

1. On **Phone B**:
   - Navigate to the **Message History** tab.
   - **Verification**: The received SOS message is visible at the top of the history list!
   - Verify details:
     - Category: `Medical` (with matching icon).
     - Priority: `CRITICAL`.
     - Description: Exact matching text entered on Phone A.
     - Coordinates: Exact latitude and longitude captured on Phone A.
     - Badge: **`RECEIVED (P2P)`** (distinct from local alerts).
   - Tap on the message item to open the **Alert Details Dialog**:
     - Inspect all fields including Sender Device ID, Sender Callsign, and satellite accuracy.

2. On **Phone A**:
   - Navigate to the **Message History** tab.
   - Verify the alert status badge now displays **`TRANSFERRED`**.

3. **App Kill & Persistence Test**:
   - Swipe away and kill the BLACKOUT app on **both** Phone A and Phone B.
   - Restart Phone B while still completely offline.
   - Open **Message History**: The received alert remains permanently stored in Room and displays instantly.

---

## ⚠️ Step 7: Edge Cases & Error Recovery Verification

### Test Case E1: Connection Rejection
1. Initiate connection from Phone B to Phone A.
2. When the verification dialog appears on Phone A, tap **Reject**.
3. **Expected Result**:
   - Phone B receives a polite rejection notice: *"Connection rejected by peer."*
   - Neither device establishes a connection. No messages can be transferred.

### Test Case E2: Duplicate Prevention
1. Re-transmit the exact same SOS message from Phone A to Phone B.
2. **Expected Result**:
   - Phone B receives the byte payload.
   - `ReceivedMessageHandler` checks `messageDao.getMessageById(messageId)` and detects that the UUID already exists.
   - The duplicate is safely discarded without creating a second record or corrupting the existing record.
   - Phone B's message count remains unchanged.

### Test Case E3: Unexpected Disconnection
1. Establish a connection between Phone A and Phone B.
2. Turn off Bluetooth on Phone A or walk out of physical radio range (> 50-100 metres).
3. **Expected Result**:
   - Both devices invoke `onDisconnected(endpointId)`.
   - Connected peer card vanishes and UI transitions cleanly back to idle radio state.
   - If a transfer was in progress, Phone A updates the message status to `TRANSFER_FAILED`.

### Test Case E4: Transmitting Without an Active Connection
1. Disconnect both devices.
2. On Phone A, attempt to transmit an alert when no peer is connected.
3. **Expected Result**:
   - The UI disables the transmission button or displays: *"No connected peer available. Please connect to a nearby node first."*
   - No crash or exception occurs.

### Test Case E5: Radio Hardware Disabled
1. Disable Bluetooth in Android device settings.
2. Open Nearby Network and tap *Scan for Devices*.
3. **Expected Result**:
   - App notifies user that Bluetooth radio must be turned on to scan or advertise.
   - No crash or unhandled exception occurs.
