# BLACKOUT — EMERGENCY WARNING DISTRIBUTION PROTOCOL
## Protocol Specification Version 1.0 (Phase 7)

### Executive Overview
The BLACKOUT Emergency Warning Protocol provides a standardized, tamper-evident, versioned data contract for distributing digitally signed public emergency warnings over disconnected peer-to-peer radio meshes (Google Nearby Connections `P2P_CLUSTER`) and cloud gateways.

---

## 1. Protocol Architecture & Invariants

1. **Protocol Version**: Current active version is `1`. Payloads bearing unsupported versions are rejected immediately without processing.
2. **Deterministic Canonicalization**: Digital signatures are computed strictly over a normalized UTF-8 key-value representation (`BLACKOUT_WARNING_V1`). The signature itself is excluded from the signed bytes.
3. **Immutability of Signed Revisions**: Once signed, warning attributes (title, description, severity, timestamps, coordinates, regions) cannot be modified. Any alteration requires a new revision with a strictly incremented revision counter and a new signature.
4. **Independent Verification**: Every receiving node must independently verify the Ed25519 signature before marking the warning authentic or relaying it to subsequent nodes.
5. **Differential Synchronization**: Nodes exchange lightweight inventory catalogs before transmitting complete warning bodies, avoiding redundant transfers over battery-constrained P2P radio links.

---

## 2. Canonical Serialization Format (`BLACKOUT_WARNING_V1`)

Digital signatures are generated over the UTF-8 bytes of this exact newline-delimited format:

```text
BLACKOUT_WARNING_V1
warningId=<UUID>
protocolVersion=<INTEGER>
revision=<INTEGER>
category=<CATEGORY_ENUM>
severity=<SEVERITY_ENUM>
title=<UTF8_STRING>
description=<UTF8_STRING>
issuingAuthority=<UTF8_STRING>
issuedAt=<EPOCH_MILLISECONDS>
effectiveAt=<EPOCH_MILLISECONDS>
expiresAt=<EPOCH_MILLISECONDS>
affectedRegions=<SORTED_COMMA_SEPARATED_LIST>
latitude=<FORMATTED_DOUBLE_OR_EMPTY>
longitude=<FORMATTED_DOUBLE_OR_EMPTY>
radiusMeters=<FORMATTED_DOUBLE_OR_EMPTY>
language=<ISO_639_1_CODE>
```

### Canonicalization Rules:
- **Header**: First line must be literally `BLACKOUT_WARNING_V1`.
- **Delimiter**: Lines are separated by single newline characters (`\n`, ASCII 0x0A).
- **Regions Sorting**: `affectedRegions` elements are trimmed, deduplicated, sorted alphabetically in ascending lexicographical order, and joined by single commas (`,`). If empty, the value is blank (`affectedRegions=`).
- **Float Normalization**:
  - `latitude`: Formatted to 6 decimal places (`%.6f`) using US locale (`.` decimal point). If null, blank (`latitude=`).
  - `longitude`: Formatted to 6 decimal places (`%.6f`) using US locale. If null, blank (`longitude=`).
  - `radiusMeters`: Formatted to 2 decimal places (`%.2f`) using US locale. If null, blank (`radiusMeters=`).
- **Timestamps**: Represented as positive 64-bit integer strings in millisecond epoch time.
- **Character Encoding**: Strictly UTF-8 without byte order marks (BOM).

---

## 3. Supported Enums

### 3.1 Warning Categories
- `FLOOD`: Flash floods, dam breaches, river overflow, coastal inundation.
- `CYCLONE`: Hurricanes, typhoons, severe cyclonic storms, storm surges.
- `HEAVY_RAINFALL`: Inundation risks, cloudbursts, intense precipitation.
- `LANDSLIDE`: Debris flows, mudslides, slope failures.
- `EARTHQUAKE`: Seismic events, building collapse hazards, aftershocks.
- `FIRE`: Wildfires, industrial blazes, hazardous material fires.
- `EVACUATION`: Mandatory or voluntary civil evacuation directives.
- `INFRASTRUCTURE_EMERGENCY`: Power grid failure, water contamination, bridge collapse.
- `GENERAL_PUBLIC_SAFETY`: Curfews, civil defense notices, biological hazards.

### 3.2 Severity Levels
- `INFORMATIONAL`: Background updates, status reports, preparatory advice.
- `ADVISORY`: Minor weather or civil disturbances; situational awareness.
- `WATCH`: Favorable atmospheric/seismic conditions for an emergency event; heighten vigilance.
- `WARNING`: Severe hazardous condition is occurring or imminent; protective action required.
- `CRITICAL`: Extreme life-threatening emergency; immediate survival action required.

---

## 4. Mesh Transport Messages

All protocol packets exchanged over Nearby Connections are encoded as UTF-8 JSON payloads:

### 4.1 `WARNING_INVENTORY` (`warning_inventory`)
Exchanged immediately upon peer connection establishment to determine catalog differences.

```json
{
  "protocolVersion": 1,
  "messageType": "warning_inventory",
  "senderDeviceId": "DEV-4A7B1C",
  "inventory": [
    {
      "warningId": "d3b07384-d113-46fb-9b24-9b2bfa6eb542",
      "revision": 1,
      "payloadHash": "2a84aeb6b5df946e30122eefd0cb099ebff0352ba8740ba2501a301416e788ad",
      "expiresAt": 1850000000000,
      "verificationStatus": "VERIFIED"
    }
  ]
}
```

### 4.2 `WARNING_PAYLOAD` (`warning_payload`)
Transfers an authentic warning record to a peer.

```json
{
  "protocolVersion": 1,
  "messageType": "warning_payload",
  "senderDeviceId": "DEV-4A7B1C",
  "warning": {
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
    "affectedRegions": ["Central Basin", "North Riverbank", "Sector 4"],
    "latitude": 13.08268,
    "longitude": 80.270718,
    "radiusMeters": 5000.0,
    "language": "en",
    "status": "PUBLISHED",
    "signingKeyId": "KEY-BLACKOUT-ROOT-2026",
    "signatureAlgorithm": "Ed25519",
    "signature": "sL8/lOZt03vDDP2IIlFbVjTpgWDy22y1Z8kiF0BJ4IxuBDkAp4NUwl4i0kVZu5v36GVVa90TmyrpnUxscmd2AA=="
  }
}
```

### 4.3 `WARNING_DELIVERY_ACK` (`warning_delivery_ack`)
Acknowledges receipt, signature verification, and storage.

```json
{
  "protocolVersion": 1,
  "messageType": "warning_delivery_ack",
  "warningId": "d3b07384-d113-46fb-9b24-9b2bfa6eb542",
  "revision": 1,
  "senderDeviceId": "DEV-9F2E3A",
  "status": "ACCEPTED"
}
```
*Possible status values: `ACCEPTED`, `DUPLICATE`.*

### 4.4 `WARNING_DELIVERY_REJECTED` (`warning_delivery_rejected`)
Sent when a transmitted warning fails schema validation, verification, or anti-rollback checks.

```json
{
  "protocolVersion": 1,
  "messageType": "warning_delivery_rejected",
  "warningId": "d3b07384-d113-46fb-9b24-9b2bfa6eb542",
  "revision": 1,
  "senderDeviceId": "DEV-9F2E3A",
  "reason": "Cryptographic signature verification failed: INVALID_SIGNATURE"
}
```

---

## 5. Security & Verification Lifecycle Rules

1. **Anti-Rollback Rule**: If an incoming warning has `revision < existing.revision`, it is rejected immediately (`ROLLBACK_ATTEMPT`).
2. **Duplicate Suppression Rule**: If `revision == existing.revision`, the node returns an ACK with status `DUPLICATE` and aborts further processing.
3. **Superseding Rule**: If `revision > existing.revision` and signature verifies, the existing record is moved to `warning_revisions` (immutable archive) and the new revision becomes the active warning.
4. **Revocation Precedence**: If a valid revocation record is processed for `warningId`, any present or future revisions of that warning are suppressed from user notifications and marked `REVOKED`.
5. **Key Revocation Rule**: Warnings signed by a key marked `REVOKED` in the trusted key store are rejected with `REVOKED_SIGNING_KEY`.
