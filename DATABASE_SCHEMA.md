# BLACKOUT — POSTGRESQL DATABASE SCHEMA SPECIFICATION
## Central Emergency Infrastructure (Phase 5)

This document specifies the PostgreSQL relational schema implemented on Supabase for the BLACKOUT decentralized emergency platform.

---

## 1. Schema Overview

```
                      +-----------------------------+
                      |    device_registrations     |
                      |-----------------------------|
                      | device_id (PK, TEXT)        |
                      | callsign (TEXT)             |
                      | hardware_model (TEXT)       |
                      | android_api_level (INT)     |
                      | created_at (TIMESTAMPTZ)    |
                      | last_seen_at (TIMESTAMPTZ)  |
                      +-----------------------------+
                                     |
                                     | uploads via
                                     v
+-------------------------------+         +--------------------------------+
|       emergency_alerts        |<--------|    gateway_upload_history      |
|-------------------------------|         |--------------------------------|
| id (PK, UUID)                 |         | id (PK, UUID)                  |
| message_id (UNIQUE, UUID)     |         | message_id (FK, UUID)          |
| original_sender_device_id     |         | gateway_device_id (TEXT)       |
| gateway_device_id (TEXT)      |         | upload_status (TEXT)           |
| category (ENUM)               |         | response_reference (UUID)      |
| description (TEXT)            |         | uploaded_at (TIMESTAMPTZ)      |
| latitude / longitude          |         +--------------------------------+
| accuracy_meters (FLOAT)       |
| priority (ENUM)               |         +--------------------------------+
| ingestion_status (ENUM)       |-------->|    emergency_alert_events      |
| operator_status (ENUM)        |         |--------------------------------|
| protocol_version (INT)        |         | id (PK, UUID)                  |
| hop_count (INT)               |         | message_id (FK, UUID)          |
| payload_hash (CHAR(64))       |         | event_type (TEXT)              |
| created_at (TIMESTAMPTZ)      |         | actor_id (TEXT)                |
| expires_at (TIMESTAMPTZ)      |         | event_timestamp (TIMESTAMPTZ)  |
+-------------------------------+         | metadata (JSONB)               |
                                          +--------------------------------+
```

---

## 2. Table Definitions

### 2.1. `emergency_alerts`
Primary repository of emergency SOS alerts ingested into the central database from mobile Internet Gateways.

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | `UUID` | NO | Internal primary key (`gen_random_uuid()`) |
| `message_id` | `UUID` | NO | Unique RFC 4122 UUID created on the originating device |
| `original_sender_device_id` | `TEXT` | NO | Hardware/node identifier of originating device |
| `gateway_device_id` | `TEXT` | NO | Identifier of the node that uploaded the alert to the internet |
| `category` | `emergency_category` | NO | One of 8 supported categories |
| `description` | `TEXT` | NO | Plain text details (max 1000 characters) |
| `priority` | `emergency_priority` | NO | Priority level (`CRITICAL`, `HIGH`, `MODERATE`, `LOW`) |
| `latitude` | `DOUBLE PRECISION` | YES | Latitude coordinate [-90.0, 90.0] |
| `longitude` | `DOUBLE PRECISION` | YES | Longitude coordinate [-180.0, 180.0] |
| `accuracy_meters` | `REAL` | YES | GPS location accuracy in metres |
| `location_timestamp` | `TIMESTAMPTZ` | YES | Device timestamp when GPS coordinate fix was acquired |
| `protocol_version` | `INT` | NO | Protocol version (1) |
| `hop_count` | `INT` | NO | Number of mesh hops traversed before gateway upload (0–7) |
| `payload_hash` | `CHAR(64)` | NO | SHA-256 canonical hash of original alert content |
| `ingestion_status` | `ingestion_status` | NO | Lifecycle status (`PENDING`, `ACCEPTED`, `DUPLICATE`, `REJECTED`, `EXPIRED`) |
| `operator_status` | `operator_status` | NO | Dispatch triage status (`NEW`, `ACKNOWLEDGED`, `DISPATCHED`, `RESOLVED`, `FALSE_ALARM`) |
| `created_at` | `TIMESTAMPTZ` | NO | Device creation timestamp |
| `expires_at` | `TIMESTAMPTZ` | NO | Expiry timestamp (typically createdAt + 48 hours) |
| `ingested_at` | `TIMESTAMPTZ` | NO | Cloud server insertion timestamp (`NOW()`) |

#### Constraints:
- `uq_emergency_alerts_message_id`: `UNIQUE (message_id)`
- `chk_emergency_alerts_description_length`: `length(trim(description)) > 0 AND length(description) <= 1000`
- `chk_emergency_alerts_latitude`: `latitude IS NULL OR (latitude >= -90.0 AND latitude <= 90.0)`
- `chk_emergency_alerts_longitude`: `longitude IS NULL OR (longitude >= -180.0 AND longitude <= 180.0)`
- `chk_emergency_alerts_hop_count`: `hop_count >= 0 AND hop_count <= 7`

---

### 2.2. `gateway_upload_history`
Audit ledger of all gateway upload encounters. When multiple mobile devices upload the same alert over time, each encounter is recorded here with gateway identifier and upload timestamp.

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | `UUID` | NO | Primary key (`gen_random_uuid()`) |
| `message_id` | `UUID` | NO | Reference to the emergency alert |
| `gateway_device_id` | `TEXT` | NO | Mobile gateway node identifier |
| `upload_status` | `TEXT` | NO | Upload result (`SUCCESS`, `DUPLICATE`, `FAILED`) |
| `response_reference` | `UUID` | YES | Central alert UUID returned to client |
| `uploaded_at` | `TIMESTAMPTZ` | NO | Ingestion timestamp (`NOW()`) |

---

### 2.3. `emergency_alert_events`
Immutable audit log recording all lifecycle and operator events on emergency alerts.

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | `UUID` | NO | Primary key (`gen_random_uuid()`) |
| `message_id` | `UUID` | NO | Reference to emergency alert |
| `event_type` | `TEXT` | NO | e.g. `ALERT_INGESTED_VIA_GATEWAY`, `OPERATOR_ACKNOWLEDGED`, `DISPATCHED` |
| `actor_id` | `TEXT` | NO | Node identifier or dispatcher username |
| `event_timestamp` | `TIMESTAMPTZ` | NO | Occurrence timestamp |
| `metadata` | `JSONB` | YES | Structured event context |

---

### 2.4. `device_registrations`
Registry of participating node hardware and callsigns that have connected as mobile gateways.

| Column | Type | Nullable | Description |
|---|---|---|---|
| `device_id` | `TEXT` | NO | Primary key |
| `callsign` | `TEXT` | NO | Human-readable callsign (e.g. `NODE-7F84`) |
| `hardware_model` | `TEXT` | YES | Device hardware description |
| `android_api_level` | `INT` | YES | Android OS API level |
| `created_at` | `TIMESTAMPTZ` | NO | Registration timestamp |
| `last_seen_at` | `TIMESTAMPTZ` | NO | Gateway heartbeat timestamp |

---

## 3. Custom Types & ENUMs

### `emergency_category`
- `'Medical Emergency'`
- `'Road Accident'`
- `'Trapped / Stranded'`
- `'Flood'`
- `'Fire'`
- `'Natural Disaster'`
- `'Personal Safety'`
- `'Other'`

### `emergency_priority`
- `'CRITICAL'`
- `'HIGH'`
- `'MODERATE'`
- `'LOW'`

### `ingestion_status`
- `'PENDING'`
- `'ACCEPTED'`
- `'DUPLICATE'`
- `'REJECTED'`
- `'EXPIRED'`

### `operator_status`
- `'NEW'`
- `'ACKNOWLEDGED'`
- `'DISPATCHED'`
- `'RESOLVED'`
- `'FALSE_ALARM'`

---

## 4. Canonical Hash & Idempotency Rules

To guarantee idempotency across multiple asynchronous mobile gateway uploads:
1. Originating device and Edge Function compute canonical payload hash:
   `SHA-256(message_id + "|" + original_sender_device_id + "|" + category + "|" + description + "|" + created_at_epoch)`
2. When an upload arrives:
   - If `message_id` does NOT exist: Insert alert with status `ACCEPTED`.
   - If `message_id` exists AND `payload_hash` matches: Return HTTP 200 with status `DUPLICATE_ACCEPTED` and existing reference. Log encounter in `gateway_upload_history`.
   - If `message_id` exists BUT `payload_hash` differs: Reject upload with HTTP 409 Conflict (`CONFLICTING_PAYLOAD`).
