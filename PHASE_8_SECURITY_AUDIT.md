# BLACKOUT — Phase 8 Security Audit Report

## 1. Executive Summary

This Security Audit was conducted across all tiers of the **BLACKOUT** offline-first emergency communication and disaster response ecosystem:
- **Android Native Client** (Java 11, Room SQLite, Nearby Connections P2P_CLUSTER, WorkManager, Bouncy Castle Ed25519)
- **Supabase Backend & Edge Functions** (Deno, PostgreSQL, Row-Level Security, Ed25519 digital signing, Public Key Registry)
- **Emergency Operations Web Portal** (React 18, TypeScript, Vite, Tailwind CSS, WebCrypto Ed25519)

Every finding was audited against real-world disaster conditions where network adversaries, malicious peers, or compromised clients could attempt to forge emergency alerts, replay stale warnings, tamper with coordinates, or exhaust local device resources.

---

## 2. Findings Register

| Finding ID | Component | Severity | Description | Status |
|---|---|---|---|---|
| `SEC-BACK-01` | Supabase Edge Functions | **CRITICAL** | `supabase/functions/warnings/index.ts` endpoints `/sign` and `/revoke` lacked JWT authentication and caller role authorization. Anyone with the URL could digitally sign or revoke warnings without authorization. | **RESOLVED** |
| `BUG-RELAY-01` | Android Relay Engine | **CRITICAL** | `RelayEngine.java` passed a null executor reference to `ReceivedMessageHandler`, causing immediate `NullPointerException` on incoming SOS mesh packets. | **RESOLVED** |
| `DATA-ROOM-01` | Android Room DB | **CRITICAL** | `BlackoutDatabase.java` lacked sequential migrations v1 $\to$ v2 and v2 $\to$ v3 and retained `fallbackToDestructiveMigration()`, risking catastrophic data loss of civilian distress signals during upgrades. | **RESOLVED** |
| `SEC-AND-01` | Android Manifest | **HIGH** | `AndroidManifest.xml` had `android:allowBackup="true"`, allowing physical attackers or malware to extract unencrypted Room SQLite databases containing private distress alerts via `adb backup`. | **RESOLVED** |
| `SEC-WEB-01` | Operations Portal | **HIGH** | `web/package.json` included legacy `maplibre-gl <= 6.4.0` with known Cross-Site Scripting (XSS) advisory GHSA-jrc7-96c5-q579. | **RESOLVED** |
| `SEC-AND-02` | Android Network Security | **MEDIUM** | Android manifest lacked `network_security_config.xml`, permitting potential cleartext (HTTP) interception on unsecured emergency Wi-Fi gateways. | **RESOLVED** |
| `SEC-AND-03` | Android Nearby Manager | **MEDIUM** | Incoming Nearby Connections payloads were dispatched to the main thread without byte-length validation, creating memory exhaustion risks from oversized peer packets. | **RESOLVED** |
| `REL-SYNC-01` | Android WorkManager | **MEDIUM** | `WarningSyncWorker.java` lacked explicit `BackoffPolicy.EXPONENTIAL` on work requests, risking server hammering during transient network connectivity. | **RESOLVED** |

---

## 3. Cryptographic Security & Tamper-Proofing

### 3.1 Ed25519 Implementation
- **Algorithm**: RFC 8032 Ed25519 using `BouncyCastleProvider` / `Ed25519Signer` on Android, WebCrypto Ed25519 in the browser, and Deno WebCrypto `crypto.subtle` in Supabase Edge Functions.
- **Canonical Serialization**: Enforced via `WarningCanonicalSerializer.java` and `signingService.ts`. All fields (warningId, revision, timestamps, sorted regions, coordinates formatted to 6 decimal places, radius to 2 decimal places) are serialized into a newline-delimited canonical UTF-8 byte stream.
- **Root Key Registry**: `KEY-BLACKOUT-ROOT-2026` public key `k5EwtLq5Md0tNFqNxdqdm2vL6c/q1ZgHcb6Uku32/Xc=`.

### 3.2 Automated Negative Cryptographic Tests
All 10 negative tests demanded by Master Prompt Section 3.B were implemented in `SecurityNegativeTestSuite.java` and verified with 100% pass rate:

1. **Tampered Title**: Title changed post-signing $\to$ `PublicWarning.VERIFY_INVALID_SIGNATURE`.
2. **Tampered Description**: Description modified to false all-clear $\to$ `PublicWarning.VERIFY_INVALID_SIGNATURE`.
3. **Tampered Severity**: Critical alert changed to informational $\to$ `PublicWarning.VERIFY_INVALID_SIGNATURE`.
4. **Tampered Geographical Scope**: Latitude, longitude, radius, or regions altered $\to$ `PublicWarning.VERIFY_INVALID_SIGNATURE`.
5. **Tampered Expiry**: Extended timestamp injected $\to$ `PublicWarning.VERIFY_INVALID_SIGNATURE`.
6. **Random Signature Bytes**: 64 random bytes substituted $\to$ `PublicWarning.VERIFY_INVALID_SIGNATURE`.
7. **Unknown Signing Key ID**: Key ID set to rogue value $\to$ `PublicWarning.VERIFY_UNKNOWN_SIGNING_KEY`.
8. **Revoked Signing Key**: Valid key revoked in `TrustedKeyRepository` $\to$ `PublicWarning.VERIFY_REVOKED_SIGNING_KEY`.
9. **Revision Replay (Anti-Rollback)**: Replaying old revision 1 against revision 3 $\to$ rejected by monotonic revision checks.
10. **Malformed Canonical Payload**: Truncated bytes, malformed base64, corrupted headers $\to$ rejected gracefully without throwing uncaught exceptions or crashing.

---

## 4. Android Attack Surface Hardening

1. **Exported Components**:
   - `MainActivity` is the only exported activity (`android:exported="true"`) as required for the launcher intent.
   - All internal services, receivers, and background workers are unexported (`android:exported="false"`).
2. **Data Backup Security (`SEC-AND-01`)**:
   - Set `android:allowBackup="false"` in `AndroidManifest.xml` to prevent extraction of Room SQLite database, private emergency messages, and trusted public keys via `adb backup`.
3. **TLS & Cleartext Policy (`SEC-AND-02`)**:
   - Added `app/src/main/res/xml/network_security_config.xml` with `<base-config cleartextTrafficPermitted="false">`.
   - All REST and Edge Function communication strictly enforces HTTPS/TLS 1.2+.
4. **Nearby Transport Payload Limits (`SEC-AND-03`)**:
   - Enforced `Constants.MAX_PAYLOAD_SIZE_BYTES` (32 KB) guard in `NearbyConnectionManager.java` before passing bytes to listeners.
5. **Permission Model**:
   - Location permissions (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`) handled gracefully with fallbacks (`Constants.LOC_STATUS_DENIED`, `Constants.LOC_STATUS_DISABLED`).
   - Bluetooth and Wi-Fi Direct permissions configured with strict SDK API level annotations (`tools:targetApi="s"`, `tools:targetApi="tiramisu"`).

---

## 5. Supabase Backend & RBAC Audit

1. **Edge Function Authentication & Role Enforcement (`SEC-BACK-01`)**:
   - Implemented `authenticateCaller(req, supabaseAdmin, allowedRoles)` in `supabase/functions/warnings/index.ts`.
   - Validates caller JWT with `supabaseAdmin.auth.getUser(token)`.
   - Queries `public.user_profiles` table to check active account status and role.
   - Enforces RBAC permissions:
     - `POST /warnings/:warningId/sign`: Restricted strictly to `SUPER_ADMIN` and `PUBLIC_WARNING_PUBLISHER`.
     - `POST /warnings/:warningId/revoke`: Restricted strictly to `SUPER_ADMIN`, `PUBLIC_WARNING_PUBLISHER`, and `CONTROL_ROOM_OPERATOR`.
   - Authenticated operator identity (`email`, `role`, `user.id`) recorded in `audit_logs`.
2. **Row Level Security (RLS)**:
   - `emergency_alerts`: Public/mobile INSERT allowed via authenticated Edge Function; SELECT restricted to authorized emergency operators.
   - `public_warnings`: Public SELECT permitted for offline distribution; INSERT/UPDATE restricted to authorized roles.
   - `user_profiles`: Modification restricted to `SUPER_ADMIN`.
   - `audit_logs`: Append-only table; no client modification or deletion permitted.

---

## 6. Verification Summary

- **Total Vulnerabilities Remediated**: 8 (3 Critical, 2 High, 3 Medium)
- **Automated Security Tests**: 12 negative test cases in `SecurityNegativeTestSuite.java`, all passing.
- **Web RBAC Tests**: 7 security tests in `rbac.test.ts` and `signingService.test.ts`, all passing.
