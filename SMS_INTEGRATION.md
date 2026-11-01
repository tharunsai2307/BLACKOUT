# BLACKOUT — Provider-Independent SMS Notification Architecture

## 1. Architectural Philosophy

Emergency communication systems require authenticated, dependable, and legally compliant notification channels. **BLACKOUT** implements a strictly **provider-independent, server-side SMS architecture**.

### Core Non-Negotiables:
- **Server-Side Exclusivity**: SMS dispatch is triggered exclusively via authenticated Supabase Edge Functions. Android mobile clients and web browser clients **never** talk directly to SMS carrier gateways or hold provider credentials.
- **Strictly Delimited Scenarios**: Blind mass messaging, SMS bombing, and unsolicited promotional messages are prohibited and architecturally blocked.
- **Truthful Status Reporting**: The platform distinguishes between `QUEUED`, `SUBMITTED`, `PROVIDER_ACCEPTED`, `DELIVERED`, `FAILED`, and `UNKNOWN`. Accepting an HTTP request does not mean an SMS reached the recipient handset.
- **Consent-Gated Delivery**: Messages are dispatched only to recipients with verified opt-in consent or explicit operator emergency verification.

---

## 2. Subsystem Architecture

```
+-------------------------------------------------------------------+
|                     Authorized Operator Portal                    |
|  - Operator triggers "Dispatch SMS" for verified incident         |
|  - Validates recipient phone (E.164) & checks consent status      |
+-------------------------------------------------------------------+
                                  |
                                  v (Authenticated HTTPS with JWT & Role Check)
+-------------------------------------------------------------------+
|               Supabase Edge Function (/notifications-sms)         |
|  - 1. Authenticate JWT & Role (SUPER_ADMIN, OPERATOR, DISPATCHER) |
|  - 2. Validate E.164 phone format (+919876543210)                 |
|  - 3. Verify Recipient Consent in `sms_recipient_preferences`     |
|  - 4. Resolve DLT-approved template in `SmsTemplateManager`       |
|  - 5. Check Idempotency Key (Suppress duplicates)                 |
|  - 6. Apply Rate Limiting (Max 3 / 10 mins per recipient)         |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                   ISmsProvider Abstraction Layer                 |
|  + MockSmsProvider (Deterministic dev/test provider)              |
|  + GenericWebhookSmsProvider (Twilio / AWS SNS / Infobip)         |
|  + IndianDltSmsProvider (Airtel IQ / Jio / BSNL DLT Gateway)     |
+-------------------------------------------------------------------+
                                  |
                                  v (Audit & Telemetry Persistence)
+-------------------------------------------------------------------+
|            Database Ledger (PostgreSQL RLS Protected)             |
|  - `sms_notification_requests`: High-level lifecycle state       |
|  - `sms_delivery_attempts`: Per-carrier latency, ref, error code |
+-------------------------------------------------------------------+
```

---

## 3. Permitted Notification Scenarios

| Scenario Identifier | Intended Use Case | Permitted Initiator | Consent Requirement |
|---|---|---|---|
| `EMERGENCY_CONTACT_ALERT` | Alerting designated family/kin when a civilian triggers an SOS beacon | Civilian App Onboarding or Operator Action | Pre-registered Emergency Contact Consent |
| `OPERATOR_INCIDENT_DISPATCH` | Alerting certified search-and-rescue teams of critical dispatched incidents | Control Room Operator / Dispatcher | Verified Responder Registration |
| `PUBLIC_WARNING_BROADCAST` | High-priority civil safety warning to subscribed citizens in a hazard perimeter | Authorized Public Warning Publisher | Opted-In Public Warning Consent |

---

## 4. Database Schema Specification

Applied in migration `supabase/migrations/20261101030000_phase9_geoapify_and_sms.sql`:

### 4.1 Table: `sms_recipient_preferences`
- `phone_number`: Unique E.164 string (e.g. `+919876543210`).
- `has_emergency_contact_consent`: Boolean flag.
- `has_public_warning_consent`: Boolean flag.
- `verification_status`: `UNVERIFIED` | `VERIFIED` | `REVOKED`.
- `consent_source`: `USER_APP_ONBOARDING` | `OPERATOR_VERIFIED` | `CIVIL_DEFENSE_REGISTRY`.

### 4.2 Table: `sms_notification_requests`
- `request_id`: UUID.
- `idempotency_key`: Unique string for duplicate suppression.
- `scenario`: Enum constraint.
- `incident_id`: Foreign key referencing `emergency_alerts(id)`.
- `status`: `QUEUED` $\to$ `SUBMITTED` $\to$ `PROVIDER_ACCEPTED` $\to$ `DELIVERED` | `FAILED`.
- `sanitized_message_preview`: PII-masked message body for audit inspection.
- `attempts_count`: Monotonically incrementing attempt tracker.

### 4.3 Table: `sms_delivery_attempts`
- `notification_request_id`: Foreign key referencing parent request.
- `provider_name`: Name of active provider.
- `provider_reference`: Upstream carrier message transaction ID.
- `error_code`, `error_message`: Sanitized error response with zero civilian PII.
- `latency_ms`: Round-trip API latency in milliseconds.

---

## 5. DLT-Compliant Template Catalog

To comply with telecommunications regulations (such as India TRAI TCCCPR 2018), message bodies must strictly adhere to pre-approved templates:

```typescript
export const APPROVED_TEMPLATES = {
  EMERGENCY_CONTACT_SOS: {
    dltTemplateId: "1407161234567890123",
    body: "BLACKOUT ALERT: Your emergency contact {name} reported an SOS ({category}) at GPS {coordinates}. Stand by for official rescue updates. Ref: {refId}",
  },
  OPERATOR_FIELD_DISPATCH: {
    dltTemplateId: "1407161234567890124",
    body: "BLACKOUT DISPATCH: Response team {teamName} assigned to Incident #{incidentId} ({category}) at {coordinates}. Priority: {priority}.",
  },
  PUBLIC_SAFETY_WARNING: {
    dltTemplateId: "1407161234567890125",
    body: "CIVIL DEFENSE WARNING: {title}. {description}. Issued by {authority}. Effective until {expiresAt}. Take immediate shelter.",
  },
};
```

---

## 6. India TRAI DLT Compliance Protocol

Before sending SMS in India, organizations must complete formal Distributed Ledger Technology (DLT) onboarding:
1. **Principal Entity (PE) Registration**: Register organization on a major telecom DLT portal (e.g. Airtel DLT, Jio DLT, Vilpower). Receive an official 19-digit Entity ID.
2. **Sender Header Registration**: Register a 6-character Alpha Header under the Service Implicit category (e.g. `BLKOUT` or `BLKOPS`).
3. **Template Approval**: Submit the 3 emergency templates above. Ensure variable placeholders (`{#var#}`) match the parameters.
4. **Consent Scrubbing**: Register consent templates for emergency contact notifications.
5. **DND Exemption**: Service Implicit classification ensures emergency SMS messages bypass Do-Not-Disturb (DND) scrubbers to reach recipients during critical emergencies.
