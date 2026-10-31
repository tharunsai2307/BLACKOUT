# BLACKOUT — PHASE 5 IMPLEMENTATION REPORT
## Central Cloud Backend, Supabase PostgreSQL Database & Mobile Internet Gateway

### Executive Summary
Phase 5 connects the decentralized, offline-first BLACKOUT emergency communication network to a central cloud emergency infrastructure. When physical devices carrying stored or relayed emergency messages encounter internet connectivity (via Wi-Fi or cellular data), they automatically transition into **Internet Gateways**, uploading pending alerts to a secure central database powered by Supabase.

All project rules have been strictly observed:
- Native Android Java 11 with XML layouts only (zero Kotlin, zero Jetpack Compose, zero Flutter/React Native).
- Standard Java `HttpURLConnection` without unnecessary third-party libraries.
- No privileged secrets or service-role keys stored in the Android APK.
- Complete offline-first architecture preserved; all Phase 1–4 features remain fully operational.
- Room database schema upgraded to Version 3 with `pending_uploads` queue.
- 77 automated unit tests passing across all components.

---

## 1. Architectural Components

```
+--------------------------------------------------------------------------+
|                            ANDROID CLIENT                                |
|                                                                          |
|  [Emergency SOS Screen]               [Nearby Mesh P2P Cluster]         |
|           |                                       |                      |
|           v                                       v                      |
|  [Room Database v3]                            [Relay Engine]            |
|  - emergency_messages                         - Multi-hop queue          |
|  - pending_uploads <--------------------------------+                    |
|           |                                                              |
|           v (Automatic enqueue on create or receive)                     |
|  [PendingUploadManager]                                                  |
|           |                                                              |
|           v                                                              |
|  [InternetGatewayService]                                                |
|  - ConnectivityManager.NetworkCallback                                   |
|  - Auto-flushes when Wi-Fi/Cellular detected                             |
|           |                                                              |
|           v                                                              |
|  [BackendApiClient] (Pure Java HttpsURLConnection)                       |
+-----------|--------------------------------------------------------------+
            |
            | HTTPS POST /functions/v1/emergency-alerts
            | Headers: apikey, Authorization, x-gateway-device-id
            v
+--------------------------------------------------------------------------+
|                       CENTRAL CLOUD BACKEND                              |
|                                                                          |
|  [Supabase Edge Function: /emergency-alerts]                             |
|  - Protocol Version 1 validation                                         |
|  - RFC 4122 UUID validation                                              |
|  - Canonical SHA-256 payload hash generation                             |
|  - Idempotency & conflict detection                                      |
|  - Audit logging                                                         |
|           |                                                              |
|           v (Service Role Client)                                        |
|  [Supabase PostgreSQL Central Database]                                 |
|  - emergency_alerts (Unique message_id, check constraints)               |
|  - gateway_upload_history (Multi-gateway encounter logging)              |
|  - emergency_alert_events (Immutable audit events)                       |
|  - device_registrations (Gateway hardware registry)                      |
|  - Least-Privilege Row Level Security (RLS) Policies                     |
+--------------------------------------------------------------------------+
```

---

## 2. Key Implementations

### 2.1. Android Manifest Permissions
Added essential network permissions to `app/src/main/AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 2.2. Room Database Version 3 (`pending_uploads`)
- **`PendingUploadEntity.java`**: Stores `messageId` (PK), `uploadStatus`, `gatewayDeviceId`, `attemptCount`, `nextAttemptAt`, `lastAttemptAt`, `uploadedAt`, `backendReference`, and `lastErrorCode`. Indices on `uploadStatus` and `nextAttemptAt`.
- **`PendingUploadDao.java`**: Implements queries for inserting, updating, retrieving eligible items by timestamp, recording acceptances, and managing retries.
- **`BlackoutDatabase.java`**: Incremented version from 2 to 3, added `PendingUploadEntity.class`, and provided abstract `pendingUploadDao()`.

### 2.3. Repository Layer Auto-Queueing (`EmergencyRepository.java`)
- Modified `insertMessage`: Newly authored emergency SOS alerts are automatically enqueued in `PendingUploadDao` with status `UPLOAD_PENDING`.
- Modified `insertReceivedMessage`: Incoming emergency SOS alerts relayed from peer devices over Nearby Connections are also enqueued in `PendingUploadDao` with status `UPLOAD_PENDING`.

### 2.4. HTTP Client (`BackendApiClient.java`)
- Native implementation using `HttpURLConnection` / `HttpsURLConnection`.
- Configurable URL and API key (defaults from `Constants`, customizable via `SharedPreferences`).
- Serializes `EmergencyMessage` into canonical JSON.
- Enforces client-side validation prior to network dispatch.
- Handles HTTP status codes:
  - `201 Created`: Successful upload (`UploadResult.success`).
  - `200 OK`: Duplicate accepted (`UploadResult.duplicateAccepted`).
  - `400 Bad Request`: Schema validation rejection (`UploadResult.rejected`).
  - `409 Conflict`: Hash collision or payload tamper (`UploadResult.conflict`).
  - `5xx / IOException`: Server / network failure (`UploadResult.retryableError`).
- Health check endpoint support (`checkHealth()`).

### 2.5. Upload Queue Coordinator (`PendingUploadManager.java`)
- Singleton coordinator processing upload batches off the main thread.
- Exponential backoff algorithm for retryable failures:
  $$\text{delay} = \min(300000\text{ ms}, 5000\text{ ms} \times 2^{\min(\text{attempt}, 6)})$$
- Non-retryable rejections (400, 409) are marked `REJECTED` and given `nextAttemptAt = Long.MAX_VALUE`.
- Expired messages are identified and marked `EXPIRED`.
- Event listeners (`GatewaySyncListener`) notify UI components of sync progress and completion.

### 2.6. Live Connectivity Monitoring (`InternetGatewayService.java`)
- Leverages Android `ConnectivityManager.NetworkCallback` to detect live Wi-Fi, Cellular, or Ethernet internet capabilities.
- Automatically triggers `PendingUploadManager.flushPendingUploads()` when internet connectivity transitions to active.
- Provides thread-safe status inspection: `isInternetAvailable()` and `getCurrentNetworkType()`.

### 2.7. User Interface Integrations
- **`MainActivity.java`**: Initializes `InternetGatewayService` upon application launch.
- **`HomeFragment.java`**: Dashboard displays `"GATEWAY ONLINE (Wi-Fi/Cellular)"` status pill when connected to the internet.
- **`NearbyFragment.java`**: Dedicated **Internet Gateway & Cloud Sync** card displaying real-time online/offline state, pending alert count, and a manual **"Sync Pending Alerts Now"** action button.
- **`HistoryFragment.java`**: Alert inspection dialog displays cloud gateway upload status (`BACKEND_ACCEPTED`, `DUPLICATE_ACCEPTED`, `UPLOAD_PENDING`, or `RETRY_WAIT`) along with backend reference IDs and a **"Sync to Cloud"** action button.

### 2.8. Supabase Backend Infrastructure
- **`supabase/migrations/20261101000000_phase5_central_backend.sql`**:
  - Tables: `emergency_alerts`, `gateway_upload_history`, `emergency_alert_events`, `device_registrations`.
  - Check constraints for coordinates, description length, and mesh hop count.
  - Least-privilege RLS policies permitting public insertion of device registrations while reserving alert writes to authorized service clients.
- **`supabase/functions/emergency-alerts/index.ts`**:
  - Deno TypeScript Edge Function.
  - Computes SHA-256 canonical hash of alert payloads.
  - Enforces idempotency: identical alerts return `200 DUPLICATE_ACCEPTED` and record the encounter in `gateway_upload_history`.

---

## 3. File Summary

| File | Status | Description |
|---|---|---|
| `app/src/main/AndroidManifest.xml` | Modified | Added INTERNET and ACCESS_NETWORK_STATE permissions |
| `com/blackout/emergency/utils/Constants.java` | Modified | Added upload status constants & default Supabase configurations |
| `com/blackout/emergency/database/PendingUploadEntity.java` | Created | Room entity for `pending_uploads` table |
| `com/blackout/emergency/database/PendingUploadDao.java` | Created | Room DAO for pending upload management |
| `com/blackout/emergency/database/BlackoutDatabase.java` | Modified | Upgraded to Room version 3 |
| `com/blackout/emergency/database/EmergencyRepository.java` | Modified | Integrated `PendingUploadManager` auto-enqueueing |
| `com/blackout/emergency/gateway/BackendApiClient.java` | Created | Native HTTPS client for Supabase Edge Functions |
| `com/blackout/emergency/gateway/PendingUploadManager.java` | Created | Queue coordinator with exponential backoff |
| `com/blackout/emergency/gateway/InternetGatewayService.java` | Created | Network callback monitor and auto-flush coordinator |
| `com/blackout/emergency/activities/MainActivity.java` | Modified | Initializes gateway service on launch |
| `com/blackout/emergency/fragments/HomeFragment.java` | Modified | Shows live Gateway Online status |
| `com/blackout/emergency/fragments/NearbyFragment.java` | Modified | Added Gateway card and manual sync button |
| `app/src/main/res/layout/fragment_nearby.xml` | Modified | Added Gateway UI card |
| `com/blackout/emergency/fragments/HistoryFragment.java` | Modified | Alert detail dialog displays cloud sync status |
| `supabase/migrations/20261101000000_phase5_central_backend.sql` | Created | Complete PostgreSQL database schema |
| `supabase/functions/emergency-alerts/index.ts` | Created | Ingestion Edge Function with canonical hash & deduplication |
| `app/src/test/java/.../BackendApiClientTest.java` | Created | 13 unit tests for HTTP serialization, validation, response handling |
| `app/src/test/java/.../PendingUploadManagerTest.java` | Created | 6 unit tests for batch queueing and state transitions |
| `app/src/test/java/.../PendingUploadEntityTest.java` | Created | 2 unit tests for entity models |
