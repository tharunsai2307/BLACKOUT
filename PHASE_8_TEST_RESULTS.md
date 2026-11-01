# BLACKOUT — Phase 8 Test Results Summary

## 1. Test Execution Overview

| Test Suite | Framework / Tool | Tests Total | Passed | Failed | Duration | Exit Code |
|---|---|---|---|---|---|---|
| **Android Unit Tests** | JUnit 4 / Gradle 9.6.0 | **143** | **143** | **0** | 19 s | `0` |
| **Web Unit & RBAC Tests** | Vitest 1.6.1 / Node.js 20 | **28** | **28** | **0** | 5.85 s | `0` |
| **Android Debug Assembly** | Android Gradle Plugin 8.13 | 34 tasks | 34 | 0 | 32 s | `0` |
| **Web Production Build** | Vite 5.4.21 / TypeScript 5.3 | 2,143 modules | 2,143 | 0 | 16.89 s | `0` |

---

## 2. Android Unit Test Suite Breakdown (143 Tests)

Commands executed:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
.\gradlew.bat testDebugUnitTest
```
Exit code: `0`

### Test Class Breakdown

| Test File | Tests Run | Result | Focus Area |
|---|---|---|---|
| `RoomMigrationTest.java` | 5 | **PASSED** | Sequential migrations v1 $\to$ v2 $\to$ v3 $\to$ v4, table & index creation |
| `SecurityNegativeTestSuite.java` | 12 | **PASSED** | 10 Ed25519 tamper tests, malformed JSON, payload limits |
| `ReliabilityStressPerformanceTest.java` | 5 | **PASSED** | 100/1000/5000 SOS volume, 100/1000 warnings, 100 Ed25519 throughput, partition healing |
| `NetworkResilienceTest.java` | 7 | **PASSED** | Timeout simulation, HTTP 500, HTTP 429, malformed response, backoff progression |
| `OfflineWarningRelayMultiHopTest.java` | 13 | **PASSED** | 4-hop mesh relay, multi-hop tampering rejection, differential sync |
| `WarningCryptographyTest.java` | 13 | **PASSED** | Ed25519 verification, canonical serialization, key revocation |
| `WarningLifecycleTest.java` | 9 | **PASSED** | State transitions (DRAFT $\to$ PUBLISHED $\to$ EXPIRED $\to$ REVOKED) |
| `WarningGeographicFilterTest.java` | 7 | **PASSED** | Haversine distance filtering, bounding box, regional scoping |
| `WarningApiClientTest.java` | 10 | **PASSED** | Supabase Edge Function & REST fallback parsing, key fetching |
| `BackendApiClientTest.java` | 11 | **PASSED** | SOS alert upload serialization, HTTP status classification |
| `PendingUploadManagerTest.java` | 12 | **PASSED** | Offline queuing, retry counter, duplicate upload prevention |
| `RelayQueueManagerTest.java` | 18 | **PASSED** | Store-carry-and-forward queue, FIFO priority sorting, attempt limits |
| `RelayPayloadModelsTest.java` | 5 | **PASSED** | SOS relay JSON serialization, hop counting |
| `RelayPolicyTest.java` | 4 | **PASSED** | TTL enforcement, maximum hop bounds |
| `ReceivedMessageHandlerTest.java` | 8 | **PASSED** | Incoming payload deserialization, executor dispatch, null safety |
| `EmergencyMessageTest.java` | 4 | **PASSED** | SOS model integrity, timestamp generation, UUID validation |
| `RoomEntityMappingTest.java` | 2 | **PASSED** | Model $\leftrightarrow$ Entity bidirectional mapping |
| `SosPayloadSerializerTest.java` | 5 | **PASSED** | Compact Nearby binary and JSON payload mapping |
| `LocationResultTest.java` | 4 | **PASSED** | GPS coordinate handling, accuracy bounds, fallback statuses |
| `NearbyDeviceTest.java` | 3 | **PASSED** | Peer device representation, connection states |
| `PendingUploadEntityTest.java` | 2 | **PASSED** | Upload entity mapping, attempt count tracking |
| `EmergencyAlertTest.java` | 2 | **PASSED** | Alert priority and category validation |
| `DateTimeUtilsTest.java` | 2 | **PASSED** | ISO 8601 formatting and millisecond conversions |

---

## 3. Web Operations Portal Test Suite Breakdown (28 Tests)

Command executed:
```powershell
npm --prefix web test -- --run
```
Exit code: `0`

### Test File Breakdown

| Test File | Tests Run | Result | Focus Area |
|---|---|---|---|
| `src/test/signingService.test.ts` | 10 | **PASSED** | Canonical serialization, Ed25519 browser verification, tamper detection, RBAC rejection |
| `src/test/rbac.test.ts` | 5 | **PASSED** | Role hierarchy (`SUPER_ADMIN`, `CONTROL_ROOM_OPERATOR`, `PUBLIC_WARNING_PUBLISHER`, etc.) |
| `src/test/formatters.test.ts` | 11 | **PASSED** | Coordinate formatting, relative time, severity badges |
| `src/test/exportCsv.test.ts` | 2 | **PASSED** | Incident audit CSV export and sanitization |

---

## 4. Build Verifications

### 4.1 Android Application Package (Debug APK)
Command executed:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
.\gradlew.bat assembleDebug
```
Output:
- Exit code: `0`
- Duration: `32 s`
- Status: `BUILD SUCCESSFUL in 32s` (34 actionable tasks: 3 executed, 31 up-to-date)
- Target Artifact: `app/build/outputs/apk/debug/app-debug.apk`

### 4.2 Web Operations Portal Production Build
Command executed:
```powershell
npm --prefix web run build
```
Output:
- Exit code: `0`
- Duration: `16.89 s`
- Status: `vite v5.4.21 building for production... ✓ 2143 modules transformed.`
- Target Artifact: `web/dist/`
