# BLACKOUT — BACKEND API SPECIFICATION
## Phase 5: Supabase Edge Functions & Mobile Internet Gateway Protocol

This document defines the HTTP API specification for communication between BLACKOUT Android Internet Gateways and the Supabase cloud backend.

---

## 1. Overview & Conventions

- **Transport**: HTTPS with TLS 1.3 / 1.2
- **Format**: JSON (`application/json; charset=UTF-8`)
- **Protocol Version**: 1
- **Authentication**: `apikey` header and `Authorization: Bearer <anonKey>` using Supabase anon key
- **Gateway Identity**: `x-gateway-device-id` header carrying the callsign or hardware ID of the mobile gateway

---

## 2. Endpoints

### 2.1. Ingest Emergency Alert
Transfers a verified emergency SOS alert from a mobile gateway into the central database.

- **URL**: `POST /functions/v1/emergency-alerts`
- **Headers**:
  ```http
  Content-Type: application/json; charset=UTF-8
  apikey: <SUPABASE_ANON_KEY>
  Authorization: Bearer <SUPABASE_ANON_KEY>
  x-gateway-device-id: NODE-7F84
  ```

#### Request Body Schema
```json
{
  "protocolVersion": 1,
  "messageType": "SOS",
  "messageId": "a1b2c3d4-e5f6-4a1b-8c2d-3e4f5a6b7c8d",
  "originalSenderDeviceId": "DEV-PHONE-A",
  "gatewayDeviceId": "NODE-7F84",
  "category": "Medical Emergency",
  "description": "Patient experiencing severe chest pain. Needs defibrillator.",
  "priority": "CRITICAL",
  "latitude": 37.77490,
  "longitude": -122.41940,
  "accuracyMeters": 4.5,
  "locationTimestamp": 1793491200000,
  "locationStatus": "Location acquired",
  "createdAt": 1793491200000,
  "expiresAt": 1793664000000,
  "hopCount": 2
}
```

#### Field Specifications
| Field | Type | Required | Description |
|---|---|---|---|
| `protocolVersion` | `Integer` | YES | Must be `1` |
| `messageType` | `String` | YES | Must be `"SOS"` |
| `messageId` | `String` | YES | RFC 4122 UUID v4 |
| `originalSenderDeviceId` | `String` | YES | Node identifier of the alert's original author |
| `gatewayDeviceId` | `String` | YES | Identifier of the uploading gateway node |
| `category` | `String` | YES | One of the 8 canonical categories |
| `description` | `String` | YES | 1 to 1000 characters |
| `priority` | `String` | YES | `CRITICAL`, `HIGH`, `MODERATE`, or `LOW` |
| `latitude` | `Double` | NO | [-90.0, 90.0] or null |
| `longitude` | `Double` | NO | [-180.0, 180.0] or null |
| `accuracyMeters` | `Float` | NO | GPS accuracy in metres |
| `locationTimestamp` | `Long` | NO | Epoch milliseconds |
| `locationStatus` | `String` | NO | Location acquisition status string |
| `createdAt` | `Long` | YES | Epoch milliseconds of SOS creation |
| `expiresAt` | `Long` | YES | Epoch milliseconds when alert expires |
| `hopCount` | `Integer` | YES | Mesh hops traversed, integer between `0` and `7` |

---

#### Response Codes

##### HTTP 201 Created — Successful Ingestion
Returned when an alert is accepted for the first time and saved in the central database.
```json
{
  "status": "SUCCESS",
  "message": "Emergency alert successfully ingested and persisted in central database",
  "messageId": "a1b2c3d4-e5f6-4a1b-8c2d-3e4f5a6b7c8d",
  "backendReference": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "receivedAt": "2026-11-01T00:00:00.000Z"
}
```

##### HTTP 200 OK — Duplicate Accepted (Idempotency)
Returned when an alert with identical `messageId` and payload hash has already been received from another peer or gateway.
```json
{
  "status": "DUPLICATE_ACCEPTED",
  "message": "Emergency alert already recorded in central database",
  "messageId": "a1b2c3d4-e5f6-4a1b-8c2d-3e4f5a6b7c8d",
  "backendReference": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "ingestionStatus": "ACCEPTED"
}
```

##### HTTP 400 Bad Request — Schema / Constraint Violation
Non-retryable client error. The mobile gateway will mark the upload as `REJECTED`.
```json
{
  "error": "INVALID_CATEGORY",
  "message": "Invalid emergency category: CustomCategory"
}
```

##### HTTP 409 Conflict — Conflicting Payload Hash
Returned if an alert arrives with an existing `messageId` but differing payload contents (tampering or collision).
```json
{
  "error": "CONFLICTING_PAYLOAD",
  "message": "An alert with this messageId already exists with different content hash",
  "messageId": "a1b2c3d4-e5f6-4a1b-8c2d-3e4f5a6b7c8d"
}
```

##### HTTP 500 Internal Server Error
Temporary server error. The mobile gateway will retry using exponential backoff.
```json
{
  "error": "INTERNAL_SERVER_ERROR",
  "message": "Database query timed out"
}
```

---

### 2.2. Gateway Health Check
Verifies connectivity and service health for mobile gateways.

- **URL**: `GET /functions/v1/emergency-alerts/health`
- **Headers**:
  ```http
  apikey: <SUPABASE_ANON_KEY>
  Authorization: Bearer <SUPABASE_ANON_KEY>
  ```
- **Response (HTTP 200 OK)**:
  ```json
  {
    "status": "HEALTHY",
    "timestamp": "2026-11-01T00:00:00.000Z",
    "service": "blackout-emergency-gateway"
  }
  ```

---

## 3. Client Retry & Exponential Backoff Policy

When a mobile gateway encounters a transient failure (network timeout, HTTP 5xx, DNS failure):
- Attempt 1: Wait 5 seconds
- Attempt 2: Wait 10 seconds
- Attempt 3: Wait 20 seconds
- Attempt 4: Wait 40 seconds
- Attempt 5: Wait 80 seconds
- Attempt 6+: Maximum backoff capped at 300 seconds (5 minutes)

When an error is non-retryable (HTTP 400 validation error, HTTP 409 hash conflict):
- Set `uploadStatus = "REJECTED"`
- Set `nextAttemptAt = Long.MAX_VALUE` (no further attempts)
