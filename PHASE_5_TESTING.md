# BLACKOUT — PHASE 5 TESTING & VERIFICATION GUIDE
## Automated Test Results and Manual End-to-End Validation

This document details the testing verification for Phase 5 (Central Cloud Backend, Supabase Database & Internet Gateway).

---

## 1. Automated Unit Test Results

Automated unit tests were executed using `./gradlew testDebugUnitTest`.

### Test Summary
- **Total Tests Executed**: 77
- **Passed**: 77 (100%)
- **Failed**: 0
- **Skipped**: 0

### Test Breakdown by Suite

| Test Suite | Class | Tests | Status |
|---|---|---|---|
| **Phase 5 Backend API Client** | `BackendApiClientTest` | 13 | PASSED |
| **Phase 5 Upload Manager** | `PendingUploadManagerTest` | 6 | PASSED |
| **Phase 5 Room Upload Entity** | `PendingUploadEntityTest` | 2 | PASSED |
| **Phase 4 Relay Queue Manager** | `RelayQueueManagerTest` | 9 | PASSED |
| **Phase 4 Relay Payload Models** | `RelayPayloadModelsTest` | 6 | PASSED |
| **Phase 4 Relay Policy Rules** | `RelayPolicyTest` | 4 | PASSED |
| **Phase 3 Payload Serializer** | `SosPayloadSerializerTest` | 7 | PASSED |
| **Phase 3 Received Message Handler** | `ReceivedMessageHandlerTest` | 7 | PASSED |
| **Phase 3 Nearby Device Model** | `NearbyDeviceTest` | 5 | PASSED |
| **Phase 2 Emergency Message Model** | `EmergencyMessageTest` | 6 | PASSED |
| **Phase 2 Room Entity Mapping** | `RoomEntityMappingTest` | 2 | PASSED |
| **Phase 2 Location Result Model** | `LocationResultTest` | 6 | PASSED |
| **Phase 1 Emergency Alert Model** | `EmergencyAlertTest` | 2 | PASSED |
| **Phase 1 Date Time Formatting** | `DateTimeUtilsTest` | 2 | PASSED |
| **TOTAL** | | **77** | **ALL PASSED** |

---

## 2. Phase 5 Unit Test Details

### `BackendApiClientTest` (13 tests)
1. `testBuildJsonPayloadContainsAllRequiredFields`: Validates that JSON payload contains `protocolVersion`, `messageType`, `messageId`, `originalSenderDeviceId`, `gatewayDeviceId`, `category`, `description`, `priority`, `latitude`, `longitude`, `hopCount`, `createdAt`, and `expiresAt`.
2. `testValidateMessageSuccess`: Verifies a valid message passes validation without errors.
3. `testValidateMessageInvalidUuid`: Verifies malformed non-UUID strings are rejected.
4. `testValidateMessageInvalidCategory`: Verifies unrecognized emergency categories are rejected.
5. `testValidateMessageEmptyDescription`: Verifies whitespace or empty descriptions are rejected.
6. `testValidateMessageExcessiveDescriptionLength`: Verifies descriptions exceeding 1000 characters are rejected.
7. `testValidateMessageInvalidLatitude`: Verifies latitudes outside [-90, 90] are rejected.
8. `testValidateMessageInvalidLongitude`: Verifies longitudes outside [-180, 180] are rejected.
9. `testValidateMessageExceededHopCount`: Verifies hop counts exceeding 7 are rejected.
10. `testUploadAlertHttp201Success`: Verifies HTTP 201 response is parsed into `UploadResult.success`.
11. `testUploadAlertHttp200DuplicateAccepted`: Verifies HTTP 200 duplicate response is parsed into `UploadResult.duplicateAccepted`.
12. `testUploadAlertHttp400Rejection`: Verifies HTTP 400 response is parsed into non-retryable `UploadResult.rejected`.
13. `testUploadAlertHttp409Conflict`: Verifies HTTP 409 response is parsed into non-retryable conflict.
14. `testUploadAlertHttp500ServerErrorIsRetryable`: Verifies HTTP 500 response is marked as retryable.
15. `testUploadAlertNetworkExceptionIsRetryable`: Verifies `IOException` network errors are marked retryable.
16. `testCheckHealthSuccess`: Verifies backend health check endpoint parsing.

### `PendingUploadManagerTest` (6 tests)
1. `testEnqueueForUploadInsertsRecord`: Verifies newly created or received alerts are stored in `pending_uploads` with status `UPLOAD_PENDING`.
2. `testFlushPendingUploadsSuccess`: Verifies successful HTTP 201 marks entity `BACKEND_ACCEPTED` with backend reference ID.
3. `testFlushPendingUploadsDuplicateAccepted`: Verifies HTTP 200 duplicate marks entity `DUPLICATE_ACCEPTED` with backend reference ID.
4. `testFlushPendingUploadsRetryableFailureUpdatesBackoff`: Verifies server errors update status to `RETRY_WAIT`, increments attemptCount, and computes exponential backoff.
5. `testFlushPendingUploadsNonRetryableRejection`: Verifies validation rejections set status to `REJECTED` and `nextAttemptAt = Long.MAX_VALUE`.
6. `testFlushPendingUploadsExpiredAlertMarkedExpired`: Verifies expired alerts are marked `EXPIRED` without transmitting over the network.

---

## 3. Manual End-to-End Test Scenarios

### Scenario 1: Immediate Upload on Internet Connection
1. Launch BLACKOUT on a device with active Wi-Fi or cellular internet.
2. Confirm the Home dashboard displays `"GATEWAY ONLINE"`.
3. Create an emergency SOS (e.g. Category: `Medical Emergency`, Description: `Severe laceration`).
4. Observe:
   - Alert saved locally to Room database.
   - Background worker immediately executes `BackendApiClient.uploadAlert`.
   - Alert successfully ingested by the Supabase Edge Function (`HTTP 201 Created`).
5. Open History tab, tap on the alert:
   - Dialog shows: `Upload Status: BACKEND_ACCEPTED` with backend UUID reference.

### Scenario 2: Offline SOS Creation & Deferred Gateway Upload
1. Turn on Airplane Mode (disable Wi-Fi and Cellular).
2. Create an emergency SOS in BLACKOUT.
3. Observe:
   - Alert saved locally to Room database.
   - Pending upload recorded in Room with status `UPLOAD_PENDING`.
   - Nearby Network tab displays: `Internet Gateway & Cloud Sync: OFFLINE (1 emergency alert(s) buffered offline)`.
4. Turn Airplane Mode off (re-enable Wi-Fi).
5. Observe:
   - Android `ConnectivityManager.NetworkCallback` fires in `InternetGatewayService`.
   - Network status updates to `ONLINE (WI-FI)`.
   - Gateway automatically invokes `PendingUploadManager.flushPendingUploads`.
   - Notification toast appears: `"Internet Gateway: Uploaded 1 alert(s) to cloud database"`.
   - Nearby screen updates to: `All emergency alerts synchronized with central database`.

### Scenario 3: Relayed SOS Cloud Upload (Delay-Tolerant Networking)
1. Device A (offline in emergency zone) creates an SOS.
2. Device A connects via Nearby Connections P2P to Device B (offline courier).
3. Device B receives and persists Device A's SOS.
4. Device B travels into an area with cellular or Wi-Fi coverage.
5. Device B's `InternetGatewayService` detects internet:
   - Device B uploads Device A's SOS to the Supabase cloud backend.
   - Central database registers the alert with:
     - `message_id`: Device A's UUID
     - `original_sender_device_id`: Device A
     - `gateway_device_id`: Device B
     - `hop_count`: 1

### Scenario 4: Deduplication & Idempotency Across Multiple Gateways
1. Device A's SOS was relayed to both Device B and Device C.
2. Device B connects to Wi-Fi first and uploads the SOS (`HTTP 201 Created`).
3. Device C connects to cellular 5 minutes later and also uploads the SOS.
4. Edge Function computes canonical SHA-256 hash, matches `message_id`, and detects existing identical alert.
5. Edge Function logs the second upload encounter in `gateway_upload_history` with `gateway_device_id = Device C` and returns `HTTP 200 DUPLICATE_ACCEPTED`.
6. Device C records `DUPLICATE_ACCEPTED` locally. No duplicate rows created in `emergency_alerts`.
