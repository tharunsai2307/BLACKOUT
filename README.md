# BLACKOUT — Decentralized Emergency Communication Platform

**BLACKOUT** is an offline-first, decentralized emergency communications system built for Android. It enables civilians, first responders, and emergency coordinators to prepare, broadcast, and relay life-saving SOS alerts and official civil safety warnings across nearby devices—even during complete electrical grid collapses, cellular outages, or internet blackouts.

---

## 🛡️ Product Vision & Core Tenets

When natural disasters, conflict, or severe infrastructure failures strike, centralized communications (cellular towers, fiber broadband, DNS, and cloud services) are often the first to fail. BLACKOUT is engineered under the principle of **Zero Cloud Dependency**:

1. **Decentralized Store-Carry-and-Forward Mesh**: Devices physically close to one another exchange emergency packets directly using Google's Nearby Connections API (`Strategy.P2P_CLUSTER`). Mobile carriers transport messages through physical space and relay them to newly encountered nodes across multiple hops.
2. **Offline-First Resilience**: All emergency data structures, SOS preparation forms, direct satellite GPS fixes, local database persistence, and peer-to-peer data transfers function without active internet or network handshakes.
3. **Honest System Transparency**: In emergency situations, false indicators of delivery, fake coordinates, or fabricated peer connections can cost lives. The application strictly reflects real hardware state and never generates pseudo-coordinates or simulated mesh peers.
4. **Mission-Critical Visual Identity**: Dark-first tactical design, restrained emergency red accents, cyan telemetry highlights, and high-contrast typography for legibility under smoke, darkness, or high glare.

---

## 📱 Technology Stack & Platform Specifications

- **Platform**: Native Android (Android SDK)
- **Primary Language**: Java (Java 11 compatibility)
- **Layout Architecture**: Native Android XML Layouts & Material Design Components
- **Persistence Engine**: Android Jetpack Room 2.6.1 (`androidx.room:room-runtime` & `androidx.room:room-compiler`) — Version 4 schema
- **Cryptography Engine**: Bouncy Castle Java Provider (`org.bouncycastle:bcprov-jdk18on:1.78.1`) for Ed25519 asymmetric signature verification (RFC 8032)
- **Background Scheduling**: Android Jetpack WorkManager 2.9.0 (`androidx.work:work-runtime`)
- **P2P Wireless Engine**: Google Nearby Connections API (`com.google.android.gms:play-services-nearby:19.3.0`)
- **Operations Portal & Web**: React 18 + TypeScript + Vite + Tailwind CSS + Leaflet (Phase 6 & 7)
- **P2P Wireless Engine**: Google Nearby Connections API (`com.google.android.gms:play-services-nearby:19.3.0`)
- **Cloud Backend & Gateway**: Supabase PostgreSQL with Edge Functions (Deno/TypeScript) & native Android `HttpURLConnection`
- **Minimum SDK**: Android 8.0 (API level 26)
- **Target SDK**: Android 16 / Android 17 (API level 37)
- **Build System**: Android Gradle Plugin (AGP) 9.5.0 with Gradle 9.6.0

---

## 🗂️ Application Structure & Screens (Phase 5)

| Screen | Responsibility | Phase 5 Status |
| :--- | :--- | :--- |
| **Home** | Primary mission dashboard. Real-time radio state overview, live Nearby connection state with connected peer count, live **Internet Gateway Online/Offline telemetry**, prominent SOS trigger hero card, and recent emergency activity card bound to Room DB. | Fully Implemented (Live Gateway & Mesh State Hookup) |
| **Emergency SOS** | Emergency beacon preparation. 8 categories selector, dynamic priority computation, genuine GPS capture button, live coordinates/accuracy review, pre-save confirmation dialog, Room storage, automatic relay queue, and automatic cloud upload queue enqueueing. | Fully Implemented (Hardware GPS, Relay & Cloud Queue Hookup) |
| **Nearby Network** | Local peer network dashboard. Local node callsign display, independent Advertising and Discovery radio toggles, connection approval with 4-digit mutual verification code, active connected peer card, live Multi-Hop Relay Engine Status card, and **Internet Gateway & Cloud Sync card with manual sync action**. | Fully Implemented (Mesh Relay & Cloud Gateway Sync) |
| **Public Alerts** | Civil defense and public emergency warning feed. Live Room DB binding, category filter chips, pull-to-refresh sync, cryptographic authenticity badges (`AUTHENTIC SIGNATURE (Ed25519)`), remaining validity countdown timers, and detailed cryptographic inspection dialog. | Fully Implemented (Phase 7 Signed Warnings, Verification & Multi-Hop Relay) |
| **Message History** | Outgoing SOS records, incoming broadcasts, and relayed packet logs with priority badges, timestamps, coordinates/accuracy, category filters, and detailed audit view dialog. Displays status badges: `LOCAL SAVED`, `RECEIVED (P2P)`, `RELAYED (HOP X)`, `TRANSFERRED`, and `TRANSFER FAILED`. **Detailed inspection dialog displays cloud gateway upload status with backend reference ID and manual sync trigger.** | Fully Implemented (Room DB Querying with Hop & Cloud Audit) |
| **Settings & Info** | Node identity callsign, live runtime location permission status check & requester, alert notifications toggles, privacy model, and protocol specs. | Fully Implemented (Live System Permission Binding) |

---

## 🌐 Central Cloud Backend & Mobile Internet Gateway (Phase 5)

In Phase 5, BLACKOUT bridges offline delay-tolerant networks with centralized emergency response infrastructure:
- **Mobile Internet Gateway**: Any device with stored, carried, or relayed SOS alerts that encounters an active Wi-Fi or cellular internet connection automatically uploads pending alerts to the central database.
- **Pure Java Network Stack**: Uses standard Java `HttpURLConnection` / `HttpsURLConnection` without heavy third-party HTTP clients.
- **Central PostgreSQL on Supabase**: Relational schema (`emergency_alerts`, `gateway_upload_history`, `emergency_alert_events`, `device_registrations`) with check constraints and Row Level Security (RLS).
- **Supabase Edge Function (`emergency-alerts`)**: Validates protocol version, RFC 4122 UUID format, coordinate bounds, and hop limits. Computes canonical SHA-256 payload hash for tamper detection.
- **Idempotency & Deduplication**: Multi-gateway uploads of the same alert are deduplicated; existing identical records return `200 DUPLICATE_ACCEPTED` and record the encounter in `gateway_upload_history`.
- **Zero Privileged Secrets in APK**: Android client uses only the public publishable anon key. The privileged `SUPABASE_SERVICE_ROLE_KEY` is strictly confined to server-side Edge Functions.
- **Exponential Backoff**: Transient network or server errors retry with exponential backoff (5s to 5m), while client validation errors (400) or hash conflicts (409) are marked `REJECTED`.

---

## 🔄 Multi-Hop Store-Carry-and-Forward Relay Engine (Phase 4)

- **Topology**: Ad-hoc physical encounters (`Phone A ↔ Phone B`, and later `Phone B ↔ Phone C`). Phone A and Phone C do not need to be within radio range or connected simultaneously.
- **Persistent Relay Queue (`relay_queue`)**: Newly created and received messages are stored in Room and queued as `PENDING`. Survives device reboots and app kills.
- **Message Inventory Exchange (`MESSAGE_INVENTORY`)**: Upon connecting, nodes exchange known message ID catalogs (up to 50 items) to prevent transferring duplicates.
- **Application-Level Acknowledgements (`DELIVERY_ACK`)**: Senders mark an SOS as delivered to a peer only when the receiver explicitly acknowledges successful validation and Room database persistence.
- **Hop Ceiling & Expiry Enforcement**: Monotonically increments `hopCount` up to 7 hops (`RelayPolicy.DEFAULT_MAX_HOPS`) and drops packets exceeding the 48-hour Time-to-Live.
- **Bounded Retry Backoff**: Interrupted transfers enter `RETRY_WAIT` with exponential backoff (30s to 15m), preventing radio thrashing.

---

## 🛰️ Genuine Hardware GPS Location Capture (Phase 2)

The `LocationService` captures actual physical coordinates using Android's direct satellite `LocationManager` and `GPS_PROVIDER`:
- **No Internet Required**: Direct communication with satellite constellations (GPS/GLONASS/Galileo).
- **Zero Mock / Fake Coordinates**: Never generates mock, random, or hardcoded coordinates.
- **Accurate Accuracy & Fix Time**: Records accuracy radius in metres and satellite fix epoch timestamp.
- **Graceful Degradation**: Users can create and store an emergency SOS beacon even if GPS coordinates are unavailable (`STATUS_LOCATION_UNAVAILABLE`), ensuring vital emergency text is never blocked.

---

## 💾 Offline Room Database Persistence (Version 4)

The local database (`BlackoutDatabase`) stores emergency alerts, relay queues, delivery history, and cloud upload queues permanently across restarts:
- **`emergency_messages`**: Emergency alert records (`messageId`, `senderDeviceId`, `senderCallsign`, `emergencyCategory`, `description`, `latitude`, `longitude`, `locationAccuracy`, `createdAt`, `expiresAt`, `priority`, `status`, `hopCount`).
- **`relay_queue`**: Store-carry-and-forward queue (`messageId`, `relayStatus`, `queuedAt`, `nextAttemptAt`, `attemptCount`, `lastAttemptAt`, `lastError`, `priority`).
- **`relay_history`**: Peer delivery log for audit and duplicate suppression (`messageId`, `fromDeviceId`, `toDeviceId`, `hopNumber`, `transferStatus`, `timestamp`).
- **`pending_uploads`**: Internet Gateway upload queue (`messageId`, `uploadStatus`, `gatewayDeviceId`, `attemptCount`, `nextAttemptAt`, `lastAttemptAt`, `uploadedAt`, `backendReference`, `lastErrorCode`).
- **`public_warnings`**: Active digitally signed public warnings (`warningId`, `protocolVersion`, `revision`, `title`, `description`, `category`, `severity`, `issuingAuthority`, `issuedAt`, `effectiveAt`, `expiresAt`, `affectedRegions`, `latitude`, `longitude`, `radiusMeters`, `language`, `status`, `signingKeyId`, `signatureAlgorithm`, `signature`, `canonicalPayload`, `verificationStatus`, `payloadHash`).
- **`warning_revisions`**: Immutable historical audit ledger of superseded warning revisions.
- **`trusted_signing_keys`**: Trusted public verification keys registry.
- **`warning_relay_history`**: Store-carry-and-forward warning distribution peer event logs.
- **`warning_sync_state`**: Differential sync tracking table.
- **`warning_revocations`**: Formal administrative revocation directives cache.

---

## 🚀 Running the Application

### 1. Prerequisites
- Android Studio Ladybug / Meerkat (or newer)
- Android SDK Platforms 26–37 installed
- Physical Android devices running API 26 or higher

### 2. Build via Gradle Wrapper
From the project root:
```bash
# Build Debug APK
./gradlew assembleDebug

# Run Full Android Unit Test Suite (113 tests passing, 0 failures)
./gradlew testDebugUnitTest

# Run Operations Portal Web Test Suite (26 tests passing, 0 failures)
npm --prefix web test -- --run
```

### 3. Deploy to Device
Deploy directly through Android Studio or via adb:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.blackout.emergency/.activities.MainActivity
```

---

## 🖥️ Government Operations Portal & Web Dashboard (Phase 6)

In Phase 6, BLACKOUT introduces a production-ready, dark-themed operations center web portal for authorized disaster response personnel and control room operators:
- **Location**: `web/` directory
- **Tech Stack**: React 18, TypeScript, Vite, Tailwind CSS, Supabase JS, MapLibre GL JS, Recharts, Vitest.
- **Key Capabilities**:
  - Real-time ingestion stream connecting via Supabase Realtime Change-Data-Capture.
  - Interactive tactical incident map with OpenStreetMap raster tiles (free & tokenless).
  - Searchable, filterable incident management table with RFC 4180 CSV export.
  - Verification workflow (`UNVERIFIED` → `UNDER_REVIEW` / `VERIFIED` / `FALSE_REPORT`).
  - Response unit tracking and incident dispatching.
  - Civil defense warning broadcast lifecycle (`DRAFT` → `SUBMITTED_FOR_APPROVAL` → `APPROVED` → `PUBLISHED` → `REVOKED`).
  - Real aggregated mathematical charts and tamper-evident audit ledger.
  - Database-backed Role-Based Access Control (RBAC) with Row-Level Security (RLS).

To run the operations portal:
```bash
cd web
npm install
npm run dev     # Starts web console at http://localhost:3000
npm run test    # Runs Vitest automated test suite (18 tests passing)
npm run build   # Production build to web/dist/
```

For complete operations portal documentation, consult:
- [`OPERATIONS_PORTAL.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/OPERATIONS_PORTAL.md) — Comprehensive portal user manual, architecture, and deployment guide
- [`SUPABASE_SETUP.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/SUPABASE_SETUP.md) — Supabase database and Edge Function provisioning
- [`DATABASE_SCHEMA.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/DATABASE_SCHEMA.md) — PostgreSQL table definitions and constraints
- [`BACKEND_API.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/BACKEND_API.md) — HTTP API endpoints and payload schemas
- [`PHASE_5_IMPLEMENTATION.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/PHASE_5_IMPLEMENTATION.md) — Architectural overview of Phase 5
- [`PHASE_5_TESTING.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/PHASE_5_TESTING.md) — Automated test suite and manual verification flows
- [`RELAY_PROTOCOL.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/RELAY_PROTOCOL.md) — P2P store-carry-and-forward protocol specification

---

## 🧭 Multi-Phase Roadmap

- **Phase 1 (Complete)**: Complete UI/UX Development, Java Clean Architecture, Navigation Lifecycle, and Honest State Validation.
- **Phase 2 (Complete)**: Direct Hardware GPS Location Capture, Room Database Persistence, 8 Emergency Categories, History Inspection, and Offline Storage.
- **Phase 3 (Complete)**: Real Device-to-Device Offline P2P Communication via Google Nearby Connections, Mutual Handshake Verification, JSON Byte Serialization, and Duplicate-Proof Room Ingestion.
- **Phase 4 (Complete)**: Multi-Hop Store-Carry-and-Forward Relay Engine, Message Inventory Exchange, Application-Level Acknowledgements, Persistent Room Queue, and 7-Hop Enforcement.
- **Phase 5 (Complete)**: Central Cloud Backend, Supabase PostgreSQL Database, Edge Functions Ingestion API, Mobile Internet Gateway, Automatic Network Synchronization, and 77 Automated Unit Tests.
- [`PHASE_7_IMPLEMENTATION.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/PHASE_7_IMPLEMENTATION.md) — Digitally signed public warnings and offline warning distribution architecture
- [`PHASE_7_TESTING.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/PHASE_7_TESTING.md) — Phase 7 automated testing suite (113 Android + 26 Web unit tests)
- [`WARNING_PROTOCOL.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/WARNING_PROTOCOL.md) — Public warning protocol specification, binary models & canonical format
- [`DIGITAL_SIGNATURE_ARCHITECTURE.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/DIGITAL_SIGNATURE_ARCHITECTURE.md) — Ed25519 asymmetric signature engine and 10-step verification workflow
- [`KEY_MANAGEMENT.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/KEY_MANAGEMENT.md) — Public key registry, key lifecycle, rotation, and revocation procedures
- [`OFFLINE_WARNING_DISTRIBUTION.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/OFFLINE_WARNING_DISTRIBUTION.md) — Store-carry-and-forward mesh mechanics and zero-trust distribution
- [`WARNING_API.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/WARNING_API.md) — Backend Edge Functions, signing endpoints, and RBAC authorization
- [`SECURITY_MODEL.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/SECURITY_MODEL.md) — Threat model, anti-tampering guarantees, and rollback protection
- [`PHASE_8_IMPLEMENTATION.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/PHASE_8_IMPLEMENTATION.md) — Security Audit, Reliability Engineering, Performance & Stress Testing Overview
- [`PHASE_8_SECURITY_AUDIT.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/PHASE_8_SECURITY_AUDIT.md) — Findings register, threat analysis, cryptographic negative tests, and attack surface hardening
- [`PHASE_8_RELIABILITY_TESTING.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/PHASE_8_RELIABILITY_TESTING.md) — Offline operation, multi-hop mesh, partition healing, and Room sequential migrations
- [`PHASE_8_PERFORMANCE_REPORT.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/PHASE_8_PERFORMANCE_REPORT.md) — Latency benchmarks, Ed25519 throughput, volume scaling (100/1000/5000 records), and batch relay queuing
- [`PHASE_8_TEST_RESULTS.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/PHASE_8_TEST_RESULTS.md) — Test execution breakdown: 143 Android unit tests + 28 Web tests passing
- [`PHASE_8_KNOWN_LIMITATIONS.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/PHASE_8_KNOWN_LIMITATIONS.md) — Documentation of physical multi-device boundaries, simulated mesh, and secrets provisioning
- [`SECURITY_REMEDIATION_LOG.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/SECURITY_REMEDIATION_LOG.md) — Detailed changelog of vulnerabilities and reliability bugs remediated
- [`PHASE_9_IMPLEMENTATION.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/PHASE_9_IMPLEMENTATION.md) — Phase 9 Full System Integration, Deployment & Release Overview
- [`PHASE_9_DEPLOYMENT_GUIDE.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/PHASE_9_DEPLOYMENT_GUIDE.md) — Step-by-step production deployment for Supabase DB, Edge Functions, Web Portal, and Android
- [`PHASE_9_TEST_RESULTS.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/PHASE_9_TEST_RESULTS.md) — Acceptance testing report, automated test suites, and 5-stage integration verification matrix
- [`PRODUCTION_CONFIGURATION.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/PRODUCTION_CONFIGURATION.md) — Production hardening specification, database policies, KMS/Vault signing, and secrets isolation
- [`RELEASE_GUIDE.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/RELEASE_GUIDE.md) — Android release packaging, keystore security, zipalign & apksigner instructions, and AAB distribution
- [`BACKUP_AND_RECOVERY.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/BACKUP_AND_RECOVERY.md) — Operational monitoring, PITR database backups, failover playbooks, and cost breakdown
- [`PILOT_READINESS_CHECKLIST.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/PILOT_READINESS_CHECKLIST.md) — Field pilot checklist, hardware qualifications, OEM battery setup, and civilian boundary disclaimers
- [`KNOWN_LIMITATIONS.md`](file:///C:/Users/Hp/Music/My%20Projects/Java%20Capstone/KNOWN_LIMITATIONS.md) — Engineering constraints, RF propagation physics, Google Play Services dependencies, and store-carry latency

---

## 🧭 Multi-Phase Roadmap

- **Phase 1 (Complete)**: Complete UI/UX Development, Java Clean Architecture, Navigation Lifecycle, and Honest State Validation.
- **Phase 2 (Complete)**: Direct Hardware GPS Location Capture, Room Database Persistence, 8 Emergency Categories, History Inspection, and Offline Storage.
- **Phase 3 (Complete)**: Real Device-to-Device Offline P2P Communication via Google Nearby Connections, Mutual Handshake Verification, JSON Byte Serialization, and Duplicate-Proof Room Ingestion.
- **Phase 4 (Complete)**: Multi-Hop Store-Carry-and-Forward Relay Engine, Message Inventory Exchange, Application-Level Acknowledgements, Persistent Room Queue, and 7-Hop Enforcement.
- **Phase 5 (Complete)**: Central Cloud Backend, Supabase PostgreSQL Database, Edge Functions Ingestion API, Mobile Internet Gateway, Automatic Network Synchronization, and 77 Automated Unit Tests.
- **Phase 6 (Complete)**: Web-Based Emergency Operations Portal, Incident Management, Verification Workflow, Team Dispatching, and Real-Time Leaflet Geospatial Map.
- **Phase 7 (Complete)**: Digitally Signed Public Warnings, Ed25519 Cryptographic Verification, Operations Portal Warning Publishing, Multi-Hop Offline Mesh Distribution, Anti-Rollback Protection, Room v4 Migration, and 113 Passing Android Unit Tests + 26 Passing Web Tests.
- **Phase 8 (Complete)**: Comprehensive Security Audit, Reliability Engineering, Performance & Stress Testing. Remediated 8 vulnerabilities across Android, Edge Functions, and Web. Created 4 automated test suites (`RoomMigrationTest`, `SecurityNegativeTestSuite`, `ReliabilityStressPerformanceTest`, `NetworkResilienceTest`). Verified 143 passing Android unit tests + 28 passing Web tests. Assembled clean debug APK and production Web build.
- **Phase 9 (Complete)**: Full System Integration, Production Deployment & Release. Verified end-to-end integration across Android, Supabase Backend, and Web Operations Portal. Compiled R8-minified release APK (`app-release-unsigned.apk`, 7.28 MB) and release Android App Bundle (`app-release.aab`, 7.65 MB). Built production Web assets (`web/dist/`). Verified 143/143 Android tests and 28/28 Web tests. Authored all deployment, release signing, monitoring, disaster recovery, and pilot readiness documentation.

