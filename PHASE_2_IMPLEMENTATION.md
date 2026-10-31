# BLACKOUT — Phase 2 Architecture & Implementation Report
## Emergency SOS, GPS Location Capture, and Offline Room Data Persistence

This document provides a comprehensive technical overview of the implementation completed for **Phase 2** of the **BLACKOUT** decentralized emergency communication application.

---

## 1. Executive Summary

Phase 2 transitions BLACKOUT from an architectural skeleton into a functional, offline-first emergency SOS beacon generator. Users can now:
1. Select from **8 standardized emergency categories** with dedicated icons and priority auto-assignment.
2. Enter detailed emergency descriptions.
3. Acquire actual physical GPS coordinates using the device's hardware satellite receiver (`LocationManager` with `GPS_PROVIDER`), with accuracy in metres and fix timestamps.
4. Review emergency coordinates, accuracy, and priority prior to confirmation.
5. Save the emergency alert permanently to a local SQLite database using **Android Jetpack Room 2.6.1**.
6. Inspect, filter, and review previously created emergency alerts in **Message History**, surviving app kills and device reboots.
7. Adhere strictly to **honest status reporting**: zero mock coordinates, zero fake networking, and graceful degradation when GPS satellite signals are obstructed indoors or underground.

---

## 2. Technology Stack & Dependencies

- **Language**: Java 11 (strict compliance — no Kotlin, no Compose, no web views).
- **UI Framework**: Native Android XML Layouts + Material Design Components 1.12.0.
- **Minimum SDK**: API 26 (Android 8.0 Oreo).
- **Target SDK**: API 37 (Android 16 / 17).
- **Local Persistence Engine**: Android Jetpack Room 2.6.1 (`androidx.room:room-runtime:2.6.1` and `androidx.room:room-compiler:2.6.1`).
- **Location API**: Android platform `android.location.LocationManager` with `GPS_PROVIDER` (direct satellite hardware access without internet dependency).

---

## 3. Architecture & Data Flow

```
+-------------------------------------------------------------------------+
|                               UI LAYER                                  |
|   +-----------------------+                    +--------------------+   |
|   |      SosFragment      |                    |  HistoryFragment   |   |
|   +-----------+-----------+                    +---------+----------+   |
|               |                                          |              |
+---------------|------------------------------------------|--------------+
                |                                          |
+---------------v------------------------------------------v--------------+
|                             SERVICE LAYER                               |
|   +------------------------------------+                                |
|   |          LocationService           |  (Hardware GPS Capture)        |
|   |   - GPS_PROVIDER listener          |  (15s timeout + cached fallback)|
|   |   - Permission & hardware checks   |  (No internet requirement)     |
|   +------------------------------------+                                |
|                                                                         |
|   +------------------------------------+                                |
|   |        EmergencyRepository         |  (Background Executor)         |
|   |   - insertMessage()                |  (Main-thread Handler dispatch)|
|   |   - getAllMessages()               |                                |
|   |   - getMessagesByCategory()        |                                |
|   |   - deleteMessage()                |                                |
|   +-----------------+------------------+                                |
+---------------------|---------------------------------------------------+
                      |
+---------------------v---------------------------------------------------+
|                            DATABASE LAYER                               |
|   +------------------------------------+                                |
|   |        EmergencyMessageDao         |  (Room DAO Interface)          |
|   +-----------------+------------------+                                |
|                     |                                                   |
|   +-----------------v------------------+                                |
|   |          BlackoutDatabase          |  (RoomDatabase version 1)      |
|   |   - Table: emergency_messages      |                                |
|   +------------------------------------+                                |
+-------------------------------------------------------------------------+
```

---

## 4. Detailed Component Implementation

### 4.1. The 8 Emergency Categories

Under `Constants.java`, 8 official emergency categories are defined with specific icons and assigned broadcast priority:

| Category | Constant | Assigned Priority | Icon Resource |
| :--- | :--- | :--- | :--- |
| **Medical Emergency** | `CATEGORY_MEDICAL` | `CRITICAL` | `ic_medical.xml` |
| **Road Accident** | `CATEGORY_ROAD_ACCIDENT` | `HIGH` | `ic_accident.xml` |
| **Trapped / Stranded** | `CATEGORY_TRAPPED` | `HIGH` | `ic_stranded.xml` |
| **Flood** | `CATEGORY_FLOOD` | `HIGH` | `ic_flood.xml` |
| **Fire** | `CATEGORY_FIRE` | `CRITICAL` | `ic_fire.xml` |
| **Natural Disaster** | `CATEGORY_DISASTER` | `CRITICAL` | `ic_disaster.xml` |
| **Personal Safety** | `CATEGORY_PERSONAL_SAFETY` | `HIGH` | `ic_personal_safety.xml` |
| **Other** | `CATEGORY_OTHER` | `MODERATE` | `ic_other.xml` |

In `fragment_sos.xml`, a single-selection `ChipGroup` allows users to select any of the 8 categories, dynamically updating the visual priority badge and icon in real-time.

---

### 4.2. Direct GPS Hardware Location Capture (`LocationService.java`)

Location acquisition operates independently of Google Play Services or active cellular connections:
1. **Permissions Verification**: Validates runtime `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`. If absent, alerts the user and triggers an `ActivityResultLauncher` permission request.
2. **Provider Availability**: Verifies whether `LocationManager.GPS_PROVIDER` is enabled on the device.
3. **Live Hardware Acquisition**: Registers a `LocationListener` on `GPS_PROVIDER` with 1-second interval and 0-metre displacement to capture a fresh satellite fix.
4. **15-Second Acquisition Timeout**: Satellite GPS fixes can take longer indoors or in dense urban canyons. A 15-second handler monitors acquisition:
   - If a fresh satellite fix arrives within 15 seconds, the listener is removed, timeout is cancelled, and the fix is returned with `Constants.LOC_STATUS_ACQUIRED` and `isFreshGps = true`.
   - If 15 seconds elapse without a fresh fix, `LocationService` checks `getLastKnownLocation`. If a recent cached location exists, it is returned with `Constants.LOC_STATUS_CACHED` and `isFreshGps = false`.
   - If no cached location is available, it returns `Constants.LOC_STATUS_UNAVAILABLE`.
5. **No Fake Coordinates**: Never generates mock, dummy, or hardcoded coordinates.
6. **Telemetry Fields**: Captures latitude, longitude, accuracy radius in metres (`Float`), and satellite timestamp (`Long`).

---

### 4.3. Room Database & Persistence Layer

The persistence architecture follows Android clean architectural principles:

- **Entity (`EmergencyMessageEntity.java`)**:
  - Table name: `emergency_messages`
  - Primary key: `messageId` (UUID string)
  - Fields: `messageId`, `senderDeviceId`, `senderCallsign`, `emergencyCategory`, `description`, `latitude`, `longitude`, `locationAccuracy`, `locationTimestamp`, `createdAt`, `expiresAt`, `priority`, `status`, `locationStatus`, `hopCount`.
  - Methods: `fromModel(EmergencyMessage)` and `toModel()`.
- **DAO (`EmergencyMessageDao.java`)**:
  - `@Insert(onConflict = OnConflictStrategy.REPLACE)` for saving alerts.
  - `@Query("SELECT * FROM emergency_messages ORDER BY createdAt DESC")` for loading messages newest first.
  - `@Query("SELECT * FROM emergency_messages WHERE emergencyCategory = :category ORDER BY createdAt DESC")` for category filtering.
  - `@Query("DELETE FROM emergency_messages WHERE messageId = :id")` for deleting individual alerts.
- **Database (`BlackoutDatabase.java`)**:
  - `@Database(entities = {EmergencyMessageEntity.class}, version = 1, exportSchema = false)`
  - Singleton instantiation via `Room.databaseBuilder(...).fallbackToDestructiveMigration().build()`.
- **Repository (`EmergencyRepository.java`)**:
  - `Executors.newSingleThreadExecutor()` guarantees all SQLite I/O occurs on a background worker thread.
  - `Handler(Looper.getMainLooper())` delivers typed callback responses (`Repository.Callback<T>`) safely to UI components.

---

### 4.4. Emergency SOS Screen (`SosFragment.java`)

The emergency creation workflow provides clarity under high-stress conditions:
1. **Category Selection**: Choose from Medical, Road Accident, Trapped, Flood, Fire, Natural Disaster, Personal Safety, or Other.
2. **Situation Input**: Multi-line `TextInputEditText` for details (injuries, number of people, hazards). If left blank, automatically populates a default description: *"Emergency Assistance Requested: [Category]"*.
3. **GPS Action**: User clicks *"Get My Location"*. The UI presents an indeterminate spinner and sets status to *"Getting location..."*.
4. **Telemetry Display**: Displays latitude, longitude, accuracy radius in metres, and fix timestamp once acquired.
5. **Creation Confirmation Dialog**:
   - Shows summary of Category, Priority, Description, Coordinates, Accuracy, and Status.
   - Includes notice: *"This emergency alert will be saved locally on this device. Offline peer-to-peer transmission will be active in future phases."*
6. **Save & Acknowledge**:
   - Saves alert asynchronously to Room database.
   - Status is marked `LOCAL_SAVED` (or `LOCATION_UNAVAILABLE` if no coordinates).
   - Shows confirmation dialog: *"Emergency alert saved on this device. This alert has not yet been transmitted to other devices or emergency services."*
   - Navigates back upon confirmation.

---

### 4.5. Message History Screen (`HistoryFragment.java` & `MessageAdapter.java`)

The history screen displays persistent alerts retrieved directly from SQLite:
1. **Room Integration**: Loads messages on `onViewCreated()` and automatically refreshes on `onResume()`.
2. **Category Filter Chips**: Single-selection chips allow filtering by *All Saved SOS*, *Medical*, *Road Accident*, *Trapped*, *Flood*, *Fire*, *Natural Disaster*, *Personal Safety*, and *Other*.
3. **Item Cards**:
   - Priority badge (Red = Critical, Amber = High, Cyan = Moderate).
   - Category badge.
   - Status pill (Green = `LOCAL_SAVED`, Amber = `LOCATION_UNAVAILABLE`).
   - Location row displaying exact coordinates and accuracy (e.g., `Lat: 37.77490° N, Lon: -122.41940° E (±5.2m)`) or `Coordinates: Not Available`.
   - Relative timestamp ("Just now", "5 mins ago", etc.).
4. **Alert Details Inspection Dialog**:
   - Tapping any card opens a full modal dialog displaying all telemetry fields, sender callsign, expiration timestamp, and full emergency description.
   - Includes a *"Delete Alert"* action with confirmation dialog, which permanently deletes the record from Room and updates the list.
5. **Empty State**: Displays an informative card when the database has no records.

---

### 4.6. Home Screen (`HomeFragment.java`)

The main dashboard reflects actual persistent device state:
- **Recent Emergency Activity Card**:
  - Automatically queries `EmergencyRepository.getAllMessages()` on launch and on resume.
  - If a saved alert exists, the card displays the newest emergency alert's priority, category, relative timestamp, description snippet, and coordinates.
  - Tapping the card navigates directly to the History tab.
  - If no alerts exist, displays the clean empty state *"No active emergency signals queued locally."*

---

## 5. Verification & Testing Results

### 5.1. Automated Unit Test Suite

The test suite in `app/src/test/java/com/blackout/emergency/` contains **18 automated unit tests** verifying all core contracts:

| Test Class | Focus | Result |
| :--- | :--- | :--- |
| `EmergencyMessageTest` | Message creation with/without coordinates, validation of all 8 categories, 48-hour packet TTL expiration, Phase 2 status assignments (`LOCAL_SAVED`, `LOCATION_UNAVAILABLE`, `EXPIRED`). | **Passed** |
| `RoomEntityMappingTest` | Bidirectional model-to-entity mapping fidelity, null coordinate handling, null safety, timestamp persistence. | **Passed** |
| `LocationResultTest` | Fresh GPS satellite fix, cached location, timeout handling, permission denied, and services disabled states. | **Passed** |
| `EmergencyAlertTest` | Public safety advisory model attributes and verification status. | **Passed** |
| `DateTimeUtilsTest` | Relative time calculations ("Just now", "5 mins ago"), full date formatting, and time formatters. | **Passed** |

**Execution Result**:
```bash
./gradlew testDebugUnitTest
# -> 18 passed, 0 skipped, 0 failed.
```

### 5.2. Compilation & APK Build

The application compiles with standard Java 11 bytecode compatibility and Room annotation processing:
```bash
./gradlew assembleDebug
# -> BUILD SUCCESSFUL in 2s
```

---

## 6. How to Verify on Android Device or Emulator

1. **Install and Launch**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.blackout.emergency/.activities.MainActivity
   ```
2. **Verify Emergency SOS Workflow**:
   - Tap **"TRIGGER EMERGENCY SOS"** on the Home screen.
   - Select any category (e.g. *Flood* or *Fire*). Verify priority updates dynamically.
   - Enter a description.
   - Tap **"Get My Location"**. Grant the location permission when prompted.
   - Verify coordinates, accuracy, and fix timestamp appear on screen.
   - Tap **"CREATE EMERGENCY SOS"**. Review the pre-save confirmation dialog and tap **"Confirm & Save"**.
   - Review the honest confirmation dialog: *"Emergency alert saved on this device. This alert has not yet been transmitted to other devices or emergency services."*
3. **Verify Persistence Across App Restarts**:
   - Force kill the application:
     ```bash
     adb shell am force-stop com.blackout.emergency
     ```
   - Relaunch the app:
     ```bash
     adb shell am start -n com.blackout.emergency/.activities.MainActivity
     ```
   - Check the **Home** screen: Notice the *Recent Emergency Activity* card now shows the saved alert!
   - Tap **History** tab: Verify the saved SOS is listed with all coordinates, priority, and timestamp intact.
   - Tap the item to inspect its complete details modal.
   - Test category filtering chips to isolate alerts by category.
   - Test deleting the alert to verify full Room CRUD operations.
