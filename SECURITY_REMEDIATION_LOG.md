# BLACKOUT — Security Remediation & Vulnerability Changelog

This document records the exact vulnerabilities, reliability bugs, and performance bottlenecks identified, analyzed, and remediated during **Phase 8: Security Audit, Reliability Engineering, Performance & Stress Testing**.

---

### Finding 1: `SEC-BACK-01` — Missing Authentication and Authorization on Warning Signing and Revocation Endpoints
- **Severity**: **CRITICAL**
- **Affected Component**: `supabase/functions/warnings/index.ts`
- **Vulnerability**: Endpoints `POST /warnings/:warningId/sign` and `POST /warnings/:warningId/revoke` did not validate JWT authentication headers or caller roles. Anyone with network access could trigger backend cryptographic signing or revoke active emergency warnings.
- **Remediation**:
  - Implemented `authenticateCaller(req, supabaseAdmin, allowedRoles)` helper function.
  - Verifies caller session token with `supabaseAdmin.auth.getUser(token)`.
  - Queries `public.user_profiles` to verify caller role and active account status.
  - Enforced RBAC:
    - `/sign`: Restricted to `SUPER_ADMIN` and `PUBLIC_WARNING_PUBLISHER`.
    - `/revoke`: Restricted to `SUPER_ADMIN`, `PUBLIC_WARNING_PUBLISHER`, and `CONTROL_ROOM_OPERATOR`.
  - Added frontend defense-in-depth role guards in `web/src/services/signingService.ts`.
  - Recorded authenticated operator identity (`email`, `role`, `user.id`) in `audit_logs`.
- **Verification**: `web/src/test/signingService.test.ts` (unauthorized signing and revocation rejection tests passing).

---

### Finding 2: `BUG-RELAY-01` — Fatal NullPointerException in Offline Relay Dispatch
- **Severity**: **CRITICAL**
- **Affected Component**: `app/src/main/java/com/blackout/emergency/nearby/ReceivedMessageHandler.java` & `RelayEngine.java`
- **Vulnerability**: `RelayEngine.java` passed `null` as the executor when constructing `ReceivedMessageHandler`, causing an unhandled `NullPointerException` on `executor.execute(...)` whenever an offline SOS packet was received from a peer.
- **Remediation**:
  - In `ReceivedMessageHandler.java`, made executor null-safe: `this.executor = executor != null ? executor : Executors.newSingleThreadExecutor();`.
  - In `RelayEngine.java`, instantiated and retained a single persistent `ReceivedMessageHandler` instance rather than re-creating it with null on every incoming payload.
- **Verification**: `app/src/test/java/com/blackout/emergency/ReceivedMessageHandlerTest.java` (8 tests passing).

---

### Finding 3: `DATA-ROOM-01` — Missing Room Sequential Migrations and Dangerous Destructive Fallback
- **Severity**: **CRITICAL**
- **Affected Component**: `app/src/main/java/com/blackout/emergency/database/BlackoutDatabase.java`
- **Vulnerability**: `BlackoutDatabase.java` lacked sequential migrations v1 $\to$ v2 and v2 $\to$ v3, and configured `.fallbackToDestructiveMigration()`. Upgrades on client devices resulted in SQLite dropping all tables, permanently deleting un-synced SOS distress records.
- **Remediation**:
  - Implemented `MIGRATION_1_2` (creates `relay_queue` and `relay_history` tables + 4 indexes).
  - Implemented `MIGRATION_2_3` (creates `pending_uploads` table + 2 indexes).
  - Implemented `MIGRATION_3_4` (creates `public_warnings`, `warning_revisions`, `trusted_signing_keys`, `warning_relay_history`, `warning_sync_state` + 6 indexes).
  - Registered all migrations: `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)`.
  - Removed `.fallbackToDestructiveMigration()`.
- **Verification**: `app/src/test/java/com/blackout/emergency/RoomMigrationTest.java` (5 tests passing).

---

### Finding 4: `SEC-AND-01` — Insecure ADB Application Backup
- **Severity**: **HIGH**
- **Affected Component**: `app/src/main/AndroidManifest.xml`
- **Vulnerability**: Manifest configured `android:allowBackup="true"`, allowing unauthorized physical extraction of the unencrypted Room database containing private distress alerts via `adb backup`.
- **Remediation**: Set `android:allowBackup="false"` in `AndroidManifest.xml`.
- **Verification**: Manifest analysis and `assembleDebug` inspection.

---

### Finding 5: `SEC-AND-02` — Missing Android Network Security Configuration
- **Severity**: **MEDIUM**
- **Affected Component**: `app/src/main/AndroidManifest.xml`
- **Vulnerability**: Missing network security configuration permitted cleartext HTTP communication on unencrypted Wi-Fi gateways.
- **Remediation**:
  - Created `app/src/main/res/xml/network_security_config.xml` strictly forbidding cleartext traffic (`<base-config cleartextTrafficPermitted="false">`).
  - Linked in `AndroidManifest.xml` via `android:networkSecurityConfig="@xml/network_security_config"`.
- **Verification**: XML resource validation and `assembleDebug` compilation.

---

### Finding 6: `SEC-AND-03` — Missing Nearby Transport Payload Size Guard
- **Severity**: **MEDIUM**
- **Affected Component**: `app/src/main/java/com/blackout/emergency/nearby/NearbyConnectionManager.java`
- **Vulnerability**: Nearby Connections payload handler dispatched byte buffers directly to main thread listener without size validation, exposing the application to heap exhaustion from malicious peers transmitting oversized payloads.
- **Remediation**: Added guard: `if (bytes != null && bytes.length > 0 && bytes.length <= Constants.MAX_PAYLOAD_SIZE_BYTES && listener != null)`.
- **Verification**: `SecurityNegativeTestSuite.test12_OversizedPacket_PayloadValidation`.

---

### Finding 7: `REL-SYNC-01` — WorkManager Missing Explicit Exponential Backoff
- **Severity**: **MEDIUM**
- **Affected Component**: `app/src/main/java/com/blackout/emergency/warnings/sync/WarningSyncWorker.java`
- **Vulnerability**: Background synchronization workers did not declare explicit exponential backoff criteria on work requests, risking server hammering on unstable disaster networks.
- **Remediation**: Added `.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)` for one-time sync and 30 seconds for periodic sync.
- **Verification**: `NetworkResilienceTest.testExponentialBackoffProgression`.
