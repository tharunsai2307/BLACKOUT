# BLACKOUT — BACKEND WARNING API SPECIFICATION
## Central Edge Functions, REST Contracts & Role-Based Authorization (Phase 7)

### Base URLs
- **Edge Functions**: `https://<PROJECT_ID>.supabase.co/functions/v1/warnings`
- **PostgreSQL REST Fallback**: `https://<PROJECT_ID>.supabase.co/rest/v1`

---

## 1. Endpoints

### 1.1 Digital Warning Signing
Signs an approved emergency warning using the backend protected Ed25519 private key.

- **Method**: `POST`
- **Path**: `/functions/v1/warnings/{warningId}/sign`
- **Headers**:
  - `Authorization: Bearer <USER_JWT>`
  - `apikey: <SUPABASE_ANON_KEY>`
  - `Content-Type: application/json`
- **Required Roles**: `admin`, `dispatcher`
- **Pre-Conditions**:
  - Warning must exist in database.
  - Warning status must be `APPROVED` (unapproved drafts cannot be signed).
  - `expiresAt` must be greater than current time.
- **Request Body**:
  ```json
  {
    "signingKeyId": "KEY-BLACKOUT-ROOT-2026"
  }
  ```
- **Success Response (200 OK)**:
  ```json
  {
    "success": true,
    "warning": {
      "id": "d3b07384-d113-46fb-9b24-9b2bfa6eb542",
      "revision": 1,
      "status": "PUBLISHED",
      "signing_key_id": "KEY-BLACKOUT-ROOT-2026",
      "signature_algorithm": "Ed25519",
      "signature": "sL8/lOZt03vDDP2IIlFbVjTpgWDy22y1Z8kiF0BJ4IxuBDkAp4NUwl4i0kVZu5v36GVVa90TmyrpnUxscmd2AA==",
      "canonical_payload": "BLACKOUT_WARNING_V1\n..."
    }
  }
  ```
- **Error Responses**:
  - `401 Unauthorized`: Missing or invalid authentication token.
  - `403 Forbidden`: User does not possess signing authority.
  - `400 Bad Request`: Warning is not approved or is already expired.
  - `404 Not Found`: Warning ID does not exist.

---

### 1.2 Fetch Active Published Warnings
Retrieves currently published, unexpired warnings for distribution to internet-connected gateway devices.

- **Method**: `GET`
- **Path**: `/functions/v1/warnings/active`
- **Headers**:
  - `apikey: <SUPABASE_ANON_KEY>`
  - `Accept: application/json`
- **Success Response (200 OK)**:
  ```json
  {
    "warnings": [
      {
        "warningId": "d3b07384-d113-46fb-9b24-9b2bfa6eb542",
        "protocolVersion": 1,
        "revision": 1,
        "title": "Severe Flash Flooding Alert",
        "description": "Immediate flash flooding reported in low-lying areas. Move to higher ground immediately.",
        "category": "FLOOD",
        "severity": "CRITICAL",
        "issuingAuthority": "BLACKOUT Civil Defense & Emergency Response Administration",
        "issuedAt": 1793400000000,
        "effectiveAt": 1793400000000,
        "expiresAt": 1850000000000,
        "affectedRegions": ["Sector 4", "Central Basin", "North Riverbank"],
        "latitude": 13.08268,
        "longitude": 80.270718,
        "radiusMeters": 5000.0,
        "language": "en",
        "status": "PUBLISHED",
        "signingKeyId": "KEY-BLACKOUT-ROOT-2026",
        "signatureAlgorithm": "Ed25519",
        "signature": "sL8/lOZt03vDDP2IIlFbVjTpgWDy22y1Z8kiF0BJ4IxuBDkAp4NUwl4i0kVZu5v36GVVa90TmyrpnUxscmd2AA=="
      }
    ]
  }
  ```

---

### 1.3 Fetch Specific Warning & Revision History
Retrieves full details of a specific warning, including its cryptographic signature and immutable revision audit ledger.

- **Method**: `GET`
- **Path**: `/functions/v1/warnings/{warningId}`
- **Success Response (200 OK)**:
  ```json
  {
    "warning": { ... },
    "revisions": [
      {
        "revision": 1,
        "title": "Initial Flash Flood Advisory",
        "signature": "...",
        "created_at": "2026-11-01T00:00:00Z"
      }
    ]
  }
  ```

---

### 1.4 Fetch Trusted Public Key Registry
Retrieves active and historical public verification keys and their operational statuses.

- **Method**: `GET`
- **Path**: `/functions/v1/warnings/keys`
- **Success Response (200 OK)**:
  ```json
  {
    "keys": [
      {
        "keyId": "KEY-BLACKOUT-ROOT-2026",
        "publicKey": "k5EwtLq5Md0tNFqNxdqdm2vL6c/q1ZgHcb6Uku32/Xc=",
        "algorithm": "Ed25519",
        "authorityId": "BLACKOUT Civil Defense & Emergency Response Administration",
        "validFrom": 1767225600000,
        "validUntil": 2082758400000,
        "keyStatus": "ACTIVE",
        "revokedAt": null,
        "revocationReason": null
      }
    ]
  }
  ```

---

### 1.5 Revoke Warning
Formally revokes a warning across the entire network.

- **Method**: `POST`
- **Path**: `/functions/v1/warnings/{warningId}/revoke`
- **Headers**:
  - `Authorization: Bearer <USER_JWT>`
  - `apikey: <SUPABASE_ANON_KEY>`
  - `Content-Type: application/json`
- **Required Roles**: `admin`, `dispatcher`
- **Request Body**:
  ```json
  {
    "reason": "Threat neutralized. Water receded below danger mark."
  }
  ```
- **Success Response (200 OK)**:
  ```json
  {
    "success": true,
    "status": "REVOKED",
    "revocationId": "a90b4321-f112-46bb-9a11-001122334455"
  }
  ```

---

## 2. Audit Logging
Every signing, revision, and revocation event generates an append-only entry in the database `audit_logs` table:
- `user_id`: Operator account ID.
- `action`: `WARNING_SIGNED`, `WARNING_PUBLISHED`, `WARNING_REVOKED`.
- `resource_type`: `public_warning`.
- `resource_id`: Warning UUID.
- `details`: JSON snapshot of canonical payload hash and key ID.
- `ip_address`: Network IP of caller.
- `timestamp`: UTC timestamp.
