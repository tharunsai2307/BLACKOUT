# BLACKOUT — Notification Security & Privacy Architecture

## 1. Threat Model & Security Mandate

Emergency notification channels are high-value targets for spoofing, spamming, financial denial-of-service, and civilian harassment. **BLACKOUT** enforces comprehensive technical safeguards across identity, authorization, privacy, and rate limiting.

---

## 2. Privacy & Personally Identifiable Information (PII) Protection

### 2.1 Phone Number Masking Policy
- In all operator audit logs, browser console telemetry, and database error messages, recipient phone numbers are masked:
  - Raw: `+919876543210`
  - Masked: `+9198******3210`
- Raw phone numbers are accessible only to authenticated control room personnel via authorized Edge Function calls with cryptographic audit trails.

### 2.2 Error Log Sanitization
Carrier error responses frequently echo request payloads. The Edge Function parser strips all civilian descriptions, medical conditions, and exact street numbers before writing to `sms_delivery_attempts`:
```typescript
// Example sanitized error entry
{
  "errorCode": "CARRIER_ROUTING_TIMEOUT",
  "errorMessage": "Network timeout to destination MSC",
  "latencyMs": 4200
  // ZERO message body text, ZERO civilian names, ZERO GPS coordinates
}
```

---

## 3. Recipient Consent & Opt-In Governance

Unsolicited emergency SMS can cause civilian panic and violates telecommunications regulations.
1. **Consent Verification Matrix**:
   - `EMERGENCY_CONTACT_ALERT`: Recipient must have explicit opt-in registered (`has_emergency_contact_consent = TRUE`), OR the control room operator must verify and check the "Bona fide emergency contact verified" confirmation box.
   - `PUBLIC_WARNING_BROADCAST`: Recipient must have opted in to civil defense warnings (`has_public_warning_consent = TRUE`).
   - `OPERATOR_INCIDENT_DISPATCH`: Restricted strictly to certified search-and-rescue team leaders with verified badge records.
2. **Opt-Out & Revocation**:
   - If a recipient requests removal or replies `STOP`, their record in `sms_recipient_preferences` is marked `verification_status = 'REVOKED'` with `opted_out_at = NOW()`.
   - The Edge Function rejects future dispatches with `RECIPIENT_CONSENT_MISSING`.

---

## 4. Abuse Prevention & Cost Control

### 4.1 Rate Limiting Architecture
- **Per-Recipient Threshold**: Maximum 3 SMS notifications to the same telephone number within any rolling 10-minute window. Excess requests return HTTP 429 (`RATE_LIMIT_EXCEEDED`).
- **Global Provider Cap**: Maximum 30 requests per minute per provider configuration. Prevents carrier credit depletion and financial denial-of-service.

### 4.2 Idempotency & Duplicate Suppression
- Every SMS dispatch requires a unique `idempotency_key` (e.g. `sms-<incidentId>-<recipientPhone>-<timestamp>`).
- If an operator accidentally clicks "Send SMS" twice, or if a network retry occurs, the Edge Function catches the unique database constraint and returns the existing request status without initiating a second carrier charge.

---

## 5. Role-Based Access Control (RBAC) & Row-Level Security (RLS)

All SMS tables in Supabase PostgreSQL enforce strict Row-Level Security:

| Role | `sms_recipient_preferences` | `sms_notification_requests` | `sms_delivery_attempts` | `sms_provider_configuration` |
|---|---|---|---|---|
| `SUPER_ADMIN` | Full CRUD | Full CRUD | Read-Only | Full CRUD |
| `CONTROL_ROOM_OPERATOR` | Read / Verify Consent | Insert / Read | Read-Only | Read-Only |
| `FIELD_RESPONSE_COORDINATOR`| Read Only | Insert / Read | Read-Only | Read-Only |
| `VIEWER` | Blocked (0 rows) | Blocked (0 rows) | Blocked (0 rows) | Read-Only |
| `anon` (Unauthenticated) | Blocked (0 rows) | Blocked (0 rows) | Blocked (0 rows) | Blocked (0 rows) |

