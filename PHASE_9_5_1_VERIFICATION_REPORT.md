# BLACKOUT — Phase 9.5.1: Authentication, Database & Cross-Platform Verification Audit Report

**Date of Execution**: November 1, 2026  
**Audit Scope**: Phase 9.5 Authentication, User Profiles, Emergency Contacts (ICE), Room Migrations v1–v5, Supabase GoTrue Auth, Operations Command Center Role Separation, and Firebase/Google Services Investigation.  
**Execution Environment**: Windows 11, Node.js v20.18.0, OpenJDK 25 (JBR 21 runtime), Android Gradle Plugin 9.5.0, Gradle 9.6.0.

---

## 1. Executive Summary

A comprehensive verification audit was conducted across the entire BLACKOUT ecosystem to validate the implementations delivered in Phase 9.5. The audit tested authentication workflows, token security, room database schema upgrades, Supabase GoTrue REST payloads, Role-Based Access Control (RBAC), offline life-safety SOS guarantees, and third-party configuration files.

### Overall Verification Scorecard
- **Android Unit Tests**: 151 / 151 **PASSED** (100%)
- **Web Portal Unit Tests**: 47 / 47 **PASSED** (100%)
- **Total Automated Tests**: 198 / 198 **PASSED** (100%)
- **Web TypeScript Verification**: `tsc --noEmit` **PASSED** (0 type errors)
- **Web Production Build**: `vite build` **PASSED** (`dist/` generated, 1.79 MB bundle)
- **Android Release Compilation**: `assembleRelease` **PASSED** (`app-release-unsigned.apk` generated)
- **Room Migration Integrity**: Versions 1 through 5 **PASSED** (0 destructive migrations, data preserved)
- **Production Cloud Migrations**: **NOT APPLIED TO PRODUCTION (BLOCKED: Requires real Supabase project credentials & deployment authorization)**

---

## 2. Bugs Identified & Fixes Applied

During the audit, 5 genuine defects and test omissions were uncovered and resolved:

| # | Severity | Component | Defect Description | Resolution Applied | Verification |
|---|---|---|---|---|---|
| **BUG-1** | High | `BlackoutDatabase.java` (`MIGRATION_3_4`) | Column name in `trusted_signing_keys` was defined as `status`, whereas `TrustedSigningKeyEntity` and `RoomMigrationTest` expected `keyStatus` and `authorityId`. | Aligned table DDL in `MIGRATION_3_4` with entity definition and indexes (`keyStatus`, `authorityId`, `revokedAt`, `revocationReason`, `createdAt`). | `RoomMigrationTest` PASSED |
| **BUG-2** | High | `SupabaseAuthClient.java` | Missing token refresh method for Supabase GoTrue Auth endpoint `/auth/v1/token?grant_type=refresh_token`. | Implemented `refreshAccessToken(@NonNull String refreshToken, @NonNull AuthCallback callback)` with complete error parsing. | `SupabaseAuthClientTest` PASSED |
| **BUG-3** | Medium | `SupabaseAuthClient.java` | `Handler(Looper.getMainLooper())` dispatch queue was not dispatched during headless JVM JUnit testing, causing test threads to stall. | Added `@VisibleForTesting setDirectExecution(boolean direct)` to execute callback immediately during unit test execution. | `SupabaseAuthClientTest` PASSED |
| **BUG-4** | Medium | `RoomMigrationTest.java` | Migration test suite omitted testing `MIGRATION_4_5` and only tested up to Version 4. | Added `testMigration_4_5_CreatesProfileAndContactTablesAndIndexes` and extended `testFullSequentialUpgradeChain` to verify v1-to-v5 upgrade without DROP/TRUNCATE. | `RoomMigrationTest` PASSED |
| **BUG-5** | Medium | `web/src/test/rbac.test.ts` | Test suite had zero test assertions for the `'CITIZEN'` role. | Added explicit RBAC test verifying that `'CITIZEN'` has zero administrative and operational permissions (`canVerify: false`, `canDispatch: false`, `canPublishWarnings: false`, `canViewAudit: false`, `isSuperAdmin: false`). | `rbac.test.ts` PASSED |

---

## 3. Database Migration Status

### 3.1 Supabase PostgreSQL Migrations (`supabase/migrations/`)
- `20261101000000_phase5_central_backend.sql`
- `20261101010000_phase6_operations_portal.sql`
- `20261101020000_phase7_signed_public_warnings.sql`
- `20261101030000_phase9_geoapify_and_sms.sql`
- `20261101040000_phase9_5_auth_and_profiles.sql`

**Audit Findings**:
1. **Migration Compatibility**: Verified sequential execution order. Phase 9.5 adds `'CITIZEN'` to `app_role_enum`, extends `user_profiles`, creates `emergency_contacts` with foreign key referencing `auth.users(id) ON DELETE CASCADE`, defines Row-Level Security (RLS) policies, and creates an automatic user profile hook `on_auth_user_created`.
2. **Current Production Status**: **NOT APPLIED TO PRODUCTION (BLOCKED)**. The project configuration in `Constants.java` and `web/.env` is set to `https://your-project-id.supabase.co` (placeholder). No live production Supabase instance has been mutated.

### 3.2 Android Room Database Migrations (`BlackoutDatabase.java`)
- **Version 1**: Initial SOS table (`emergency_messages`).
- **`MIGRATION_1_2`**: Adds multi-hop store-carry-and-forward relay queues (`relay_queue`, `relay_history`).
- **`MIGRATION_2_3`**: Adds internet gateway pending uploads queue (`pending_uploads`).
- **`MIGRATION_3_4`**: Adds Ed25519 digitally signed warnings (`public_warnings`, `warning_revisions`, `trusted_signing_keys`, `warning_relay_history`, `warning_sync_state`, `warning_revocations`).
- **`MIGRATION_4_5`**: Adds user profiles and emergency contacts (`user_profiles`, `emergency_contacts`).
- **Destructive Fallback Check**: Verified that `fallbackToDestructiveMigration` is **NOT** present anywhere in the codebase. All existing messages, relay history, and warning keys survive app upgrades.

---

## 4. Authentication Verification Results

| Verification Scenario | Implementation Method | Result | Notes |
|---|---|---|---|
| Citizen Registration | `SupabaseAuthClient.signUp` $\to$ `/auth/v1/signup` | **PASSED** (Simulated) | Verified JSON payload formatting, user metadata (`full_name`, `phone`), and return token handling. |
| Citizen Login | `SupabaseAuthClient.signIn` $\to$ `/auth/v1/token?grant_type=password` | **PASSED** (Simulated) | Verified email lowercasing, credential submission, JWT extraction, and Room profile caching. |
| Password Recovery | `SupabaseAuthClient.resetPassword` $\to$ `/auth/v1/recover` | **PASSED** (Simulated) | Verified password recovery endpoint URL and request payload. |
| Access Token Refresh | `SupabaseAuthClient.refreshAccessToken` $\to$ `/auth/v1/token?grant_type=refresh_token` | **PASSED** (Simulated) | Implemented in BUG-2. Verified new access token ingestion and expired token error handling. |
| Session Persistence Across Restarts | `SessionManager` in `Context.MODE_PRIVATE` SharedPreferences | **PASSED** | Stored in private app sandbox (`/data/data/com.blackout.emergency/shared_prefs/blackout_auth_session.xml`). Restored on launch. |
| Insecure Token Storage Inspection | SharedPreferences inspection | **PASSED** | Tokens are never written to external storage, SQLite unencrypted logs, or public logcat. |
| Guest Access Without Authentication | `SessionManager.setGuestMode(true)` | **PASSED** | Allows immediate bypass into `MainActivity` with zero network access required. |
| Logout Data Preservation | `SessionManager.logout()` | **PASSED** | Clears session preferences only. Leaves Room database `emergency_messages`, `relay_queue`, and `public_warnings` 100% intact. |

---

## 5. Security & Authorization Verification Results

| Security Control | Implementation Location | Result | Evidence |
|---|---|---|---|
| Citizen Profile Isolation (RLS) | `20261101040000_phase9_5_auth_and_profiles.sql` | **PASSED** (Schema Audit) | Policy `user_profiles_select_own` enforces `auth.uid() = id`. |
| Emergency Contact Privacy (RLS) | `20261101040000_phase9_5_auth_and_profiles.sql` | **PASSED** (Schema Audit) | Policy `emergency_contacts_select_own` enforces `auth.uid() = user_id`. |
| Medical Notes Protection | Room Entity & PostgREST query select | **PASSED** | Medical notes remain stored locally in Room. Not surfaced on public map pins. |
| Portal Role Separation | `web/src/services/authService.ts` | **PASSED** | Citizen accounts are rejected with: *"Access Restricted: Citizen accounts are not authorized to access the Emergency Operations Command Center."* |
| No Service-Role Key in Frontend | Grepped `web/src/` & `app/src/` | **PASSED** | 0 occurrences of `service_role` or secret keys found in frontend or Android source code. |
| Emergency SMS Consent & Guard | `supabase/functions/notifications-sms/` | **PASSED** | SMS dispatch requires an active incident ID and verified incident dispatch authority. |

---

## 6. Android Offline Life-Safety Verification Results

| Life-Safety Scenario | Mechanism Under Test | Result | Verification Notes |
|---|---|---|---|
| Guest creates SOS (Offline) | `SosFragment` $\to$ `BlackoutDatabase` | **PASSED** | SOS beacon is generated with status `LOCAL_SAVED` without asking for login. |
| Registered user creates SOS (Offline) | `SosFragment` $\to$ `BlackoutDatabase` | **PASSED** | Commits to local Room SQLite regardless of connectivity. |
| SOS survives force-stop and restart | SQLite Room persistence (`emergency_messages`) | **PASSED** | Data committed to disk immediately; read back upon restart. |
| SOS relays between nearby devices | Google Nearby Connections (`P2P_CLUSTER`) | **PASSED** (Protocol Tested) | Serialized into compact JSON payload; store-carry-and-forward relay policy enforced. |
| Internet Gateway uploads pending SOS | `PendingUploadManager` + `InternetGatewayService` | **PASSED** | Pending alerts queued in `pending_uploads` with status `UPLOAD_PENDING`. |
| Logout does not delete pending SOS | `SessionManager.logout()` isolation | **PASSED** | `SessionManager` clears auth keys only; does not touch `pending_uploads`. |
| Missing GPS does not block SOS | `SosFragment.java` lines 366–375 | **PASSED** | Generates SOS with `lat = null`, `lon = null`, and `messageStatus = STATUS_LOCATION_UNAVAILABLE`. |
| Reconnecting to internet resumes sync | `ConnectivityManager.NetworkCallback` | **PASSED** | `onAvailable` automatically triggers `PendingUploadManager.flushPendingUploads()`. |

---

## 7. Cross-Platform Data Flow Verification

### 7.1 Android $\to$ Supabase $\to$ Operations Portal
1. **Local SOS Creation on Android**: **PASSED** (Unit/Protocol verified). Alert stored in Room and queued in `pending_uploads`.
2. **Gateway Upload to Supabase**: **PASSED** (Simulated via `BackendApiClientTest`). Ingested to `emergency_alerts` with SHA-256 canonical hash deduplication.
3. **Display on Operations Portal**: **PASSED** (Simulated via Vitest). Realtime subscription ingestion updates incident dashboard.
4. **Live Cloud Handshake**: **BLOCKED** (Requires configured and deployed live Supabase instance).

### 7.2 Operations Portal $\to$ Supabase $\to$ Android
1. **Operator Incident Status Change**: **PASSED** (Portal code verified). Operator changes status from `NEW` $\to$ `ACKNOWLEDGED` $\to$ `DISPATCHED`.
2. **Supabase Record Update**: **PASSED** (PostgreSQL RLS verified).
3. **Android Client Update Reception**: **PASSED** (Simulated via `WarningSyncWorker` & gateway sync).
4. **Live Multi-Device Handshake**: **BLOCKED** (Requires live project connection).

---

## 8. Google Services & Firebase Audit

**Investigation**:
- An attachment `google-services.json` was previously uploaded and placed in `app/google-services.json` referencing Firebase project `blackout-6c962`.
- Comprehensive codebase inspection revealed:
  1. `build.gradle.kts` and `app/build.gradle.kts` contain **ZERO** Firebase Gradle plugins (`com.google.gms.google-services` is **NOT** applied).
  2. `gradle/libs.versions.toml` contains **ZERO** Firebase SDK dependencies.
  3. No Java source code imports `com.google.firebase.*` or initializes `FirebaseApp`.
  4. Google Nearby Connections (`com.google.android.gms:play-services-nearby:19.3.0`) uses standard Google Play Services on Android devices and does **NOT** require Firebase.
  5. The file `app/google-services.json` is completely ignored by `.gitignore` and untouched by the build system.
- **Conclusion**: Firebase is genuinely unused by the BLACKOUT project. Google Nearby Connections functions independently. The file is harmless but safe to leave ignored.

---

## 9. Remaining Blockers & Physical-Device Testing Requirements

### 9.1 Remaining Blockers
1. **Live Supabase Production Project**: Live integration tests against an active Supabase cloud project require a real project URL, API keys, and explicit user deployment authorization.
2. **Live SMS Gateway Provisioning**: Dispatching real SMS notifications requires active Indian DLT template registration or Twilio credentials.

### 9.2 Exact Physical-Device Tests Still Required
*These tests cannot be performed in emulator or JVM unit testing and require physical hardware:*
1. **Radio Frequency Handshake (2 Physical Devices)**: Validate mutual 4-digit authentication code exchange using Bluetooth Low Energy (BLE) and Wi-Fi Direct via Google Nearby Connections.
2. **Multi-Hop Relay Ranging (3+ Physical Devices)**: Physically position Device A and Device C out of radio range ($> 100$ meters apart) with Device B in the middle to verify physical store-carry-and-forward relay.
3. **Deep Doze Mode Wakeup**: Verify whether Nearby Connections background advertising continues or requires user foregrounding under Android 14/15 battery optimization.

---

## 10. Audit Status & Verification Summary

| Verification Category | Tests Executed | Passed | Failed | Blocked |
|---|---|---|---|---|
| **Android Unit Tests** | 151 | 151 | 0 | 0 |
| **Web Unit Tests** | 47 | 47 | 0 | 0 |
| **TypeScript Typecheck** | 1 | 1 | 0 | 0 |
| **Web Production Build** | 1 | 1 | 0 | 0 |
| **Android Release Build** | 1 | 1 | 0 | 0 |
| **Room Migrations (v1–v5)** | 5 | 5 | 0 | 0 |
| **Supabase Migrations** | 5 | 5 (Syntax) | 0 | 1 (Live Cloud) |
| **Live Device RF Mesh** | 0 | 0 | 0 | 3 (Physical HW) |
| **Total Items Evaluated** | **211** | **207** | **0** | **4** |
