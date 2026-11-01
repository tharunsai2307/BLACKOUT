# BLACKOUT — PHASE 10 INITIAL PROJECT AUDIT
## Comprehensive Architectural, Security, Configuration & Integration Assessment

**Date**: November 2026  
**Auditor**: Antigravity Core Systems & Security Engineering  
**Scope**: Android Mobile Client (Java 11/XML), Central Backend (Supabase PostgreSQL, GoTrue Auth, Edge Functions), Emergency Operations Portal (React/TS/Vite/Tailwind), Cryptographic Subsystems (Ed25519 & AES-GCM).

---

## 1. Executive Summary

This initial audit establishes the baseline state of the BLACKOUT decentralized emergency communication ecosystem before implementing Phase 10 hardening. The platform provides life-saving offline-first emergency capabilities, multi-hop mesh relays via Google Nearby Connections (P2P_CLUSTER), Ed25519 digitally signed public warning distribution, Supabase backend ingestion, and an operations command center.

The Phase 10 audit identified four primary areas requiring hardening and remediation:
1. **Android Token Persistence Security**: Session tokens (`access_token` and `refresh_token`) were persisted in plaintext `SharedPreferences` (`blackout_auth_session`). Android Keystore-backed AES-256-GCM authenticated encryption is required to safeguard credentials at rest.
2. **Supabase Environment Configuration**: The publishable anonymous key (`sb_publishable_i4TteJdAqTwsjaoohkC18g_fmW7EW2E`) and service-role secret key (`sb_secret_***REDACTED***`) must be securely mapped. Strict architectural separation must prevent the service-role key from ever leaking into client bundles or the Android APK.
3. **Network Cross-Boundary Access (Tunnel Network)**: Mobile devices (emulators and physical devices) require robust network routing (via local IP binding, ADB reverse port forwarding, and tunneling) to communicate with local development servers and live cloud APIs.
4. **Row-Level Security (RLS) & Cross-Platform Synchronization**: Automated multi-identity RLS validation (Citizen A vs. Citizen B isolation, Operator privileges, and unauthenticated restrictions) must be verified through empirical test execution.

---

## 2. Existing Functionality That Works

### 2.1 Android Application (Java 11 + XML)
- **Offline SOS Creation**: Users in Guest mode or Authenticated mode can create SOS emergency alerts without network access. Location coordinates, accuracy, category, description, and timestamps are persisted immediately to the local Room database (`EmergencyMessageEntity`).
- **P2P Mesh Relay Engine**: The `RelayEngine` and `NearbyConnectionsManager` handle Bluetooth/Wi-Fi Direct discovery, multi-hop forwarding (`P2P_CLUSTER`), packet deduplication, hop limits (max 7 hops), and TTL pruning (48 hours).
- **Public Warning Verification**: Ed25519 digital signature verification is implemented using Bouncy Castle (`Ed25519Signer`). Public warnings are parsed, canonicalized, verified against trusted public keys, and displayed with authentic status badges.
- **Room Database Architecture**: Migrations 1 through 5 are in place and non-destructive. Version 5 supports `user_profiles` and `emergency_contacts`.
- **UI Redesign (Phase 9.5)**: 5 bottom navigation tabs (Home, SOS, Alerts, History, Profile) with XML layouts, emergency contact management, and profile editing.
- **Pure Java GoTrue Client**: `SupabaseAuthClient.java` handles signup, login, token refresh, and password recovery via native `HttpURLConnection`.

### 2.2 Operations Command Center (React + TypeScript + Vite)
- **Role-Based Access Control (RBAC)**: Supports roles (`SUPER_ADMIN`, `CONTROL_ROOM_OPERATOR`, `FIELD_RESPONSE_COORDINATOR`, `PUBLIC_WARNING_PUBLISHER`) with offline credential fallback and automated exclusion of `CITIZEN` accounts from command tools.
- **Realtime Incident Monitoring**: MapLibre GL map visualization, live alert table, incident status modification, response team assignment, Geoapify geocoding, and SMS dispatch integration.
- **Ed25519 Public Warning Publishing**: Cryptographic keypair generation, warning canonicalization, signing, and broadcasting to Supabase.

### 2.3 Supabase Central Backend
- **Database Schema**: 5 applied SQL migrations covering core alerts, gateways, response teams, public warnings, geocode caches, SMS logs, citizen profiles, and emergency contacts.
- **Edge Functions**:
  - `emergency-alerts`: Gateway SOS ingestion, payload validation, SHA-256 deduplication, and reverse-geocode cache resolution.
  - `warnings`: Warning distribution and key registry distribution.
  - `geocode`: Forward and reverse geocoding proxy via Geoapify.
  - `notifications-sms`: Twilio SMS dispatch for emergency contact notification.

---

## 3. Missing Functionality & Gaps Identified

| # | Subsystem | Gap / Missing Capability | Impact | Phase 10 Action |
|---|---|---|---|---|
| 1 | **Android Auth** | No hardware-backed or Keystore encryption for tokens | Credentials stored in plaintext XML on device disk | Implement `KeyStoreCryptoManager` (AES-256-GCM) |
| 2 | **Android Auth** | Lack of atomic token refresh mutex | Concurrent network calls can trigger double refresh races | Add atomic synchronized refresh lock |
| 3 | **Android Auth** | Migration path from legacy plaintext session | App updates might drop session or corrupt tokens | Implement non-destructive migration & validation |
| 4 | **Backend / Web** | Placeholder URL in `.env` and `Constants.java` | Prevents live staging connectivity | Inject user publishable key and tunnel network |
| 5 | **Security** | Risk of service-role key leaking to client code | High security vulnerability if `sb_secret_...` is in APK | Quarantine service-role key strictly to `supabase/.env.local` |
| 6 | **DevOps / Testing** | No automated multi-role RLS test suite | RLS security policies assumed but not empirically verified | Implement `rlsSecurity.test.ts` & security report |
| 7 | **Network** | Android emulator/device cannot reach localhost portal | Blocks cross-platform testing on development workstation | Author `start_tunnel_network.bat` & ADB port forwards |

---

## 4. Security Weaknesses & Remediation Strategy

### 4.1 Plaintext Session Storage (High Vulnerability)
- **Flaw**: `SessionManager.java` persisted `access_token` and `refresh_token` in plaintext in `blackout_auth_session.xml`. On rooted devices or via Android backup extraction, tokens could be exfiltrated.
- **Remediation**:
  1. Leverage `AndroidKeyStore` provider to generate an AES-256 key (`AES/GCM/NoPadding`).
  2. Encrypt token payloads before writing to disk; prepend a 12-byte cryptographically secure random IV to the ciphertext.
  3. Separate non-sensitive metadata (`user_id`, `email`, `role`, `is_guest_mode`) from encrypted tokens.
  4. Ensure zero logging of tokens, headers, or keys in Logcat.

### 4.2 Refresh Token Race Conditions (Medium Vulnerability)
- **Flaw**: If multiple HTTP requests encounter an expired 401 token simultaneously, multiple calls to `refreshAccessToken()` could be dispatched concurrently. If Supabase refresh token rotation is active, the first call succeeds and invalidates the token, causing the second call to fail and prematurely log out the user.
- **Remediation**:
  - Implement an atomic synchronization flag and queue in `SessionManager` / `SupabaseAuthClient` so that only one token refresh occurs at any time, sharing the refreshed token with waiting callers.

### 4.3 Service Role Key Exposure Protection (Critical Rule)
- **Constraint**: `sb_secret_***REDACTED***` grants full administrative bypass of Row-Level Security in PostgreSQL.
- **Strict Quarantine**:
  - Must NEVER be committed to Git.
  - Must NEVER be referenced in `app/src/main/java`.
  - Must NEVER be defined as a `VITE_` variable in the web app.
  - Stored only in `supabase/.env.local` for server-side Edge Functions and administrative CLI execution.

---

## 5. Configuration & Environment Requirements

### 5.1 Supabase Runtime Credentials
- **Publishable / Anon Key**: `sb_publishable_i4TteJdAqTwsjaoohkC18g_fmW7EW2E`
  - Targets: `web/.env` (`VITE_SUPABASE_ANON_KEY`), `web/.env.example`, `Constants.DEFAULT_SUPABASE_ANON_KEY`.
- **Secret / Service-Role Key**: `sb_secret_***REDACTED***`
  - Targets: `supabase/.env.local` (`SUPABASE_SERVICE_ROLE_KEY`), `supabase/.env.example`.

### 5.2 Tunnel Network Architecture
- **Web Portal Binding**: Configure Vite in `web/vite.config.ts` to listen on `0.0.0.0:3000` to allow both LAN access (`192.168.29.178:3000`) and emulator loopback (`10.0.2.2:3000`).
- **ADB Reverse Port Forwarding**: Execute `adb reverse tcp:3000 tcp:3000` and `adb reverse tcp:54321 tcp:54321` to allow connected physical USB devices and emulators to reach local services.
- **Localtunnel / Cloudflare Tunnel**: Provide `start_tunnel_network.bat` for external mobile device testing without shared Wi-Fi.

---

## 6. Database Migration Dependency Analysis

The Supabase database migrations must execute in strict chronological sequence:

1. `20261101000000_phase5_central_backend.sql`:
   - Creates UUID extensions, `emergency_alerts`, `alert_gateways`, payload hash deduplication function, and public gateway upload policies.
2. `20261101010000_phase6_operations_portal.sql`:
   - Creates `app_role_enum`, `user_profiles`, `response_teams`, `alert_assignments`, `audit_logs`, and operator RLS policies. Depends on migration 0.
3. `20261101020000_phase7_signed_public_warnings.sql`:
   - Creates `public_warnings`, `trusted_signing_keys`, warning revision tracking, and publisher RLS policies. Depends on migration 1.
4. `20261101030000_phase9_geoapify_and_sms.sql`:
   - Creates `geocode_cache`, `sms_notifications`, Twilio dispatch logs. Depends on migration 0 and 1.
5. `20261101040000_phase9_5_auth_and_profiles.sql`:
   - Adds `CITIZEN` to `app_role_enum`, creates `emergency_contacts` table, triggers `on_auth_user_created` on `auth.users`, and defines strict citizen-isolation RLS policies. Depends on migrations 1 and 3.

**Rollback Plan**: In the event of schema rollback requirements, teardown scripts must execute in inverse order (5 -> 4 -> 3 -> 2 -> 1) to respect foreign key constraints.

---

## 7. Integration & E2E Testing Plan

To achieve the Definition of Done for Phase 10, the following scenarios must be systematically verified:

- **Scenario A (Live Authenticated SOS)**: Citizen registers/logs in on Android -> creates SOS -> gateway uploads to Supabase -> incident appears in real time on Operations Portal map and list.
- **Scenario B (Offline Multi-Hop SOS)**: Phone A generates SOS offline -> relays via Nearby Connections to Phone B -> relays to Phone C -> Phone C reaches Wi-Fi -> uploads to backend -> single deduplicated incident created in DB.
- **Scenario C (Operator Status Update)**: Operator in portal verifies and changes incident status to `IN_PROGRESS` / assigns rescue team -> Supabase updates -> Android retrieves status update.
- **Scenario D (Authentication & RLS Isolation)**:
  - Citizen A can SELECT and UPDATE only their own profile and emergency contacts.
  - Citizen A cannot access Citizen B's profile or contacts.
  - Citizens cannot access operator portal or update incident status directly.
  - Unauthenticated requests cannot read private citizen records.
  - Logout clears encrypted tokens but preserves Room database SOS messages and mesh relay history.
- **Scenario E (Offline Recovery & Resilience)**: Android app in airplane mode creates SOS -> app killed and restarted -> network restored -> pending upload queue resumes and successfully syncs.

---

## 8. Conclusion & Action Items

The BLACKOUT architecture is sound and well-tested across unit layers (198 passing tests in Phase 9.5.1). Phase 10 will complete the security hardening, credentials injection, Keystore encryption, tunnel networking, and live integration validation.

