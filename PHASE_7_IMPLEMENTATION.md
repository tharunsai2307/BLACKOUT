# BLACKOUT — PHASE 7 IMPLEMENTATION REPORT
## Digitally Signed Public Warnings and Offline Multi-Hop Warning Distribution

### Executive Summary
Phase 7 extends the BLACKOUT emergency communication ecosystem with an end-to-end, cryptographically verified public emergency warning publishing, signing, verification, and multi-hop offline distribution network.

This subsystem connects:
1. **BLACKOUT Emergency Operations Portal** (Phase 6 web application in React + TypeScript + Vite).
2. **Central Supabase PostgreSQL Database** (Phase 5 backend).
3. **Backend Digital Signing Service** (Supabase Edge Function using WebCrypto Ed25519).
4. **Native Android Application** (Java 11 + XML layouts).
5. **Google Nearby Connections Transport** (`P2P_CLUSTER` high-throughput radio mesh).
6. **Store-Carry-and-Forward Relay Engine** (Phase 4 offline propagation).

The core operational principle is **Zero-Trust Distribution Gateways**: an internet-connected smartphone serves merely as a transit pipe and is never trusted to create, alter, or validate public warnings. Every receiving smartphone independently verifies the Ed25519 digital signature against a trusted public key repository before accepting, storing, alerting, or relaying the warning across the offline mesh.

All non-negotiable rules were strictly honored:
- Native Android Java 11 + XML layouts preserved (zero Kotlin, zero Compose, zero Flutter).
- Phase 1–6 functionality fully preserved (SOS broadcast, multi-hop relay, Room DB, Internet Gateway, Operations Portal).
- Cryptographic algorithm: Ed25519 (RFC 8032) implemented consistently across Backend (WebCrypto), Frontend (WebCrypto), and Android (`org.bouncycastle:bcprov-jdk18on:1.78.1`).
- No private keys are hardcoded into Android or frontend code.
- Deterministic canonical UTF-8 key-value serialization (`BLACKOUT_WARNING_V1`).
- Room database migrated safely from Version 3 to Version 4 with zero data loss.
- 113 automated Android unit tests and 26 web unit tests passing.

---

## 1. System Architecture

```
+--------------------------------------------------------------------------+
|                     AUTHORISED WARNING PUBLISHER                         |
|                                                                          |
|  [Emergency Operations Portal (Web React + TS)]                          |
|  - Warning Creator with Category, Severity, Geofence, Expiration         |
|  - Interactive Leaflet Map for Geographic Selection                      |
|  - Role-Based Access Control (Admin / Dispatcher / Publisher)            |
|  - Status Flow: DRAFT -> SUBMITTED -> APPROVED -> SIGNED -> PUBLISHED   |
+------------------------------------|-------------------------------------+
                                     |
                                     | HTTPS POST /functions/v1/warnings/:id/sign
                                     v
+--------------------------------------------------------------------------+
|                       CENTRAL CLOUD BACKEND                              |
|                                                                          |
|  [Supabase Edge Function: /warnings]                                     |
|  - Validates Publisher Authority & Approval State                        |
|  - Builds Deterministic Canonical Payload (`BLACKOUT_WARNING_V1`)        |
|  - Signs Payload using Private Key (Secured in Environment Secret)       |
|  - Stores Signature, Algorithm & Signing Key ID in PostgreSQL            |
|  - Records Immutable Audit Event and Revision Ledger Record              |
+------------------------------------|-------------------------------------+
                                     |
                                     | HTTPS GET /functions/v1/warnings/active
                                     v
+--------------------------------------------------------------------------+
|                  INTERNET-CONNECTED ANDROID DEVICE                       |
|                       (Distribution Gateway)                             |
|                                                                          |
|  [WarningSyncWorker / WarningApiClient]                                  |
|  - Fetches Active Warnings & Key Registry Updates                        |
|  - Independent Local Cryptographic Verification via Bouncy Castle       |
|  - Local Room DB v4 (`public_warnings`, `warning_revisions`)             |
|  - High-Priority Notification Triggered (if unexpired & authentic)      |
+------------------------------------|-------------------------------------+
                                     |
                                     | Google Nearby Connections (P2P_CLUSTER)
                                     | Message: WARNING_INVENTORY -> WARNING_PAYLOAD
                                     v
+--------------------------------------------------------------------------+
|                     OFFLINE ANDROID DEVICE (HOP 1)                       |
|                                                                          |
|  [WarningRelayManager]                                                   |
|  - Receives Warning Transfer Payload                                     |
|  - Executes 10-Step Cryptographic Verification (Ed25519)                |
|  - Anti-Rollback Check: Rejects if Revision <= Stored Revision           |
|  - Persists to Local Room Database v4                                    |
|  - Alerts Local User (Audible / Visual / Geographic Relevance)           |
+------------------------------------|-------------------------------------+
                                     |
                                     | Store-Carry-and-Forward
                                     v
+--------------------------------------------------------------------------+
|               DEEP DISASTER ZONE OFFLINE DEVICE (HOP N)                  |
|                                                                          |
|  - Independently Verifies Digital Signature using Pre-Seeded Root Key    |
|  - Reconstructs Canonical Representation & Computes SHA-256 Digest       |
|  - Rejects Tampered Content / Replayed Warnings / Revoked Keys           |
+--------------------------------------------------------------------------+
```

---

## 2. Warning Data Model Specification

Each public emergency warning follows a strict schema enforced across PostgreSQL, the backend API, the Web Portal, and Android:

| Field | Type | Description |
|---|---|---|
| `warningId` | String (UUID) | Unique RFC 4122 identifier. |
| `protocolVersion` | Integer | Protocol schema version (current: `1`). |
| `revision` | Integer | Monotonically increasing revision counter (starts at `1`). |
| `title` | String | Urgent alert headline (e.g., "Flash Flood Emergency"). |
| `description` | String | Actionable instructions and situational context. |
| `category` | Enum String | One of 9 categories (FLOOD, CYCLONE, EARTHQUAKE, etc.). |
| `severity` | Enum String | One of 5 levels (INFORMATIONAL, ADVISORY, WATCH, WARNING, CRITICAL). |
| `issuingAuthority` | String | Organization publishing the warning. |
| `issuedAt` | Long (Epoch ms) | Timestamp when warning was authorized. |
| `effectiveAt` | Long (Epoch ms) | Timestamp when warning takes effect. |
| `expiresAt` | Long (Epoch ms) | Expiration timestamp after which warning is inactive. |
| `affectedRegions` | List<String> | Names of geographical sectors, basins, or administrative zones. |
| `latitude` | Double (Nullable)| Center latitude of affected geofence. |
| `longitude` | Double (Nullable)| Center longitude of affected geofence. |
| `radiusMeters` | Double (Nullable)| Geofence radius in meters. |
| `language` | String | ISO 639-1 language code (default: `en`). |
| `status` | Enum String | Lifecycle state (`PUBLISHED`, `EXPIRED`, `REVOKED`). |
| `signingKeyId` | String | Key ID identifying the public key in the trusted registry. |
| `signatureAlgorithm`| String | Must be `Ed25519`. |
| `signature` | String (Base64) | 64-byte Ed25519 signature of the canonical payload. |
| `canonicalPayload` | String | Deterministic newline-delimited canonical string representation. |

---

## 3. Cryptographic Implementation

### 3.1 Algorithm Selection: Ed25519
Ed25519 (Edwards-curve Digital Signature Algorithm over Curve25519 with SHA-512) was selected because:
- **Security**: 128-bit security level resilient to side-channel and timing attacks.
- **Compactness**: 32-byte public keys and 64-byte signatures, critical for low-bandwidth BLE and P2P Wi-Fi Direct packet sizes.
- **Deterministic**: RFC 8032 generates identical signatures for identical inputs, eliminating signing nonces.
- **High Performance**: Verifies in less than 1 millisecond on Android hardware and host JVMs.

### 3.2 Canonical UTF-8 Serialization (`BLACKOUT_WARNING_V1`)
To prevent signature failures caused by JSON whitespace, key order, unicode escaping, or float formatting differences across platforms, canonical content is formatted as:

```
BLACKOUT_WARNING_V1
warningId=<UUID>
protocolVersion=<INT>
revision=<INT>
category=<CATEGORY>
severity=<SEVERITY>
title=<TITLE>
description=<DESCRIPTION>
issuingAuthority=<AUTHORITY>
issuedAt=<LONG>
effectiveAt=<LONG>
expiresAt=<LONG>
affectedRegions=<SORTED_COMMA_SEPARATED_REGIONS>
latitude=<FORMATTED_DOUBLE_OR_EMPTY>
longitude=<FORMATTED_DOUBLE_OR_EMPTY>
radiusMeters=<FORMATTED_DOUBLE_OR_EMPTY>
language=<LANG>
```

---

## 4. Android Implementation Breakdown

### 4.1 Packages and Classes
- **Models (`com.blackout.emergency.warnings.models`)**:
  - `PublicWarning.java`: Complete domain model with serialization and verification helpers.
  - `TrustedSigningKey.java`: Domain model for public verification keys and validity periods.
- **Cryptography (`com.blackout.emergency.warnings.crypto`)**:
  - `WarningCanonicalSerializer.java`: Deterministic canonical string builder and SHA-256 generator.
  - `WarningSignatureVerifier.java`: 10-step verification engine utilizing Bouncy Castle `Ed25519Signer`.
  - `TrustedKeyRepository.java`: In-memory + persisted key trust store pre-seeded with `KEY-BLACKOUT-ROOT-2026`.
- **Database & Storage (`com.blackout.emergency.warnings.database`)**:
  - `PublicWarningEntity.java`, `WarningRevisionEntity.java`, `TrustedSigningKeyEntity.java`, `WarningRelayHistoryEntity.java`, `WarningSyncStateEntity.java`, `WarningRevocationEntity.java`.
  - Corresponding DAOs and migration `MIGRATION_3_4` in `BlackoutDatabase.java`.
  - `WarningRepository.java`: Central thread-safe database coordinator handling rollback prevention and deduplication.
  - `WarningExpiryManager.java`: Background scheduler sweeping expired warnings.
- **Multi-Hop Relay (`com.blackout.emergency.warnings.relay`)**:
  - `WarningRelayModels.java`: Protocol packets (`WARNING_INVENTORY`, `WARNING_PAYLOAD`, `WARNING_DELIVERY_ACK`, `WARNING_DELIVERY_REJECTED`).
  - `WarningRelayManager.java`: P2P cluster mesh coordinator connected into `RelayEngine`.
- **Sync & Geolocation (`com.blackout.emergency.warnings.sync`, `...location`, `...notifications`)**:
  - `WarningApiClient.java`: Native `HttpURLConnection` gateway client.
  - `WarningSyncWorker.java`: Android `WorkManager` background sync periodic worker.
  - `WarningGeographicFilter.java`: Haversine formula and administrative region relevance calculator.
  - `WarningNotificationManager.java`: Android high-importance notification channel with vibration and sound.
- **User Interface (`com.blackout.emergency.fragments`, `...adapters`, `...ui`)**:
  - `AlertsFragment.java`: Filter chips, emergency warnings RecyclerView, pull-to-refresh sync.
  - `AlertAdapter.java`: Warning card display with authenticity badges, severity indicators, and countdown timers.
  - `WarningDetailDialog.java`: Cryptographic inspection dialog displaying signature details, key ID, SHA-256 payload hash, and validity timeline.

---

## 5. Operations Portal (Web) Integration
- `web/src/services/signingService.ts`:
  - Browser-side WebCrypto Ed25519 verification (`crypto.subtle.verify`).
  - Canonical payload reconstruction matching Java `WarningCanonicalSerializer` character-for-character.
  - Backend Edge Function bridge for `/warnings/:warningId/sign` and `/warnings/:warningId/revoke`.
- `web/src/pages/PublicWarnings.tsx`:
  - Warning publishing workflow: Draft -> Submit -> Approve -> Sign -> Publish.
  - Digital Signing Confirmation Modal displaying canonical payload preview, SHA-256 hash, and key ID.
  - Revision history drawer with side-by-side revision inspection.
  - Live authenticity badges (`AUTHENTIC SIGNATURE (Ed25519)`).

---

## 6. Build and Verification Status
- **Android Unit Tests**: 113 / 113 passed (`./gradlew testDebugUnitTest` in 6.17s).
- **Web Unit Tests**: 26 / 26 passed (`npm --prefix web test` in 5.61s).
- **Android APK Build**: Successful (`app/build/outputs/apk/debug/app-debug.apk`, 10.5 MB).
- **Web Production Bundle**: Successful (`dist/`, 12.89s).
