# BLACKOUT — SUPABASE CENTRAL BACKEND SETUP GUIDE
## Phase 5: Central PostgreSQL Database & Supabase Edge Functions

This guide details the complete process for setting up the central emergency infrastructure for BLACKOUT using Supabase.

---

## 1. Prerequisites

- A free [Supabase](https://supabase.com) account.
- [Supabase CLI](https://supabase.com/docs/guides/cli) installed locally (`npm install -g supabase` or `brew install supabase/tap/supabase`).
- Docker installed and running (for local Supabase development and edge function deployment).

---

## 2. Project Creation

1. Log in to your [Supabase Dashboard](https://app.supabase.com).
2. Click **New Project**.
3. Choose an organization and specify:
   - **Name**: `blackout-emergency-central`
   - **Database Password**: Set a strong password (keep this secure).
   - **Region**: Select a region close to your target deployment area.
   - **Pricing Plan**: Free Tier.
4. Click **Create new project** and wait 1–2 minutes for database provisioning to finish.

---

## 3. Database Migration Deployment

The complete PostgreSQL database schema, ENUMs, constraints, audit tables, and Row Level Security (RLS) policies are provided in:
`supabase/migrations/20261101000000_phase5_central_backend.sql`

### Method A: Via Supabase Dashboard SQL Editor (Recommended)
1. In your Supabase Dashboard, navigate to the **SQL Editor** tab on the left sidebar.
2. Click **New query**.
3. Copy the entire contents of `supabase/migrations/20261101000000_phase5_central_backend.sql` and paste it into the editor.
4. Click **Run** (Ctrl+Enter / Cmd+Enter).
5. Verify in the **Table Editor** that the following tables appear:
   - `emergency_alerts`
   - `gateway_upload_history`
   - `emergency_alert_events`
   - `device_registrations`

### Method B: Via Supabase CLI
```bash
# Link your local project to your Supabase project ref
supabase link --project-ref <your-project-ref>

# Push the migration
supabase db push
```

---

## 4. Deploying the Supabase Edge Function

The ingestion API endpoint is implemented as a TypeScript Edge Function running on Deno in:
`supabase/functions/emergency-alerts/index.ts`

### Deploying using Supabase CLI
```bash
# Deploy the emergency-alerts Edge Function
supabase functions deploy emergency-alerts --no-verify-jwt
```
> **Note on `--no-verify-jwt`**: The function performs authorization and validation internally using the gateway device ID, API key, and payload integrity hash. Mobile nodes authenticate using the public publishable anon key.

### Function Environment Variables
Supabase automatically injects:
- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- `SUPABASE_ANON_KEY`

No additional secret configuration is required. The function leverages `SUPABASE_SERVICE_ROLE_KEY` internally to persist alerts securely across RLS boundaries while restricting mobile clients to anon privileges.

---

## 5. Testing the Edge Function with cURL

### 1. Health Check
```bash
curl -X GET "https://<your-project-ref>.supabase.co/functions/v1/emergency-alerts/health" \
  -H "apikey: <your-anon-key>" \
  -H "Authorization: Bearer <your-anon-key>"
```
**Expected Response:**
```json
{
  "status": "HEALTHY",
  "timestamp": "2026-11-01T00:00:00.000Z",
  "service": "blackout-emergency-gateway"
}
```

### 2. Ingesting a Test SOS Alert
```bash
curl -X POST "https://<your-project-ref>.supabase.co/functions/v1/emergency-alerts" \
  -H "Content-Type: application/json" \
  -H "apikey: <your-anon-key>" \
  -H "Authorization: Bearer <your-anon-key>" \
  -H "x-gateway-device-id: GATEWAY-TERMINAL-01" \
  -d '{
    "protocolVersion": 1,
    "messageType": "SOS",
    "messageId": "a1b2c3d4-e5f6-4a1b-8c2d-3e4f5a6b7c8d",
    "originalSenderDeviceId": "DEV-TEST-01",
    "gatewayDeviceId": "GATEWAY-TERMINAL-01",
    "category": "Medical Emergency",
    "description": "Patient experiencing severe chest pain. Needs defibrillator.",
    "priority": "CRITICAL",
    "latitude": 37.7749,
    "longitude": -122.4194,
    "accuracyMeters": 4.5,
    "locationTimestamp": 1793491200000,
    "locationStatus": "Location acquired",
    "createdAt": 1793491200000,
    "expiresAt": 1793664000000,
    "hopCount": 1
  }'
```
**Expected Response (HTTP 201 Created):**
```json
{
  "status": "SUCCESS",
  "message": "Emergency alert successfully ingested and persisted in central database",
  "messageId": "a1b2c3d4-e5f6-4a1b-8c2d-3e4f5a6b7c8d",
  "backendReference": "<database-uuid>",
  "receivedAt": "2026-11-01T00:00:00.000Z"
}
```

### 3. Submitting the Same Alert Again (Idempotency Verification)
Execute the same curl command again.
**Expected Response (HTTP 200 OK):**
```json
{
  "status": "DUPLICATE_ACCEPTED",
  "message": "Emergency alert already recorded in central database",
  "messageId": "a1b2c3d4-e5f6-4a1b-8c2d-3e4f5a6b7c8d",
  "backendReference": "<database-uuid>",
  "ingestionStatus": "ACCEPTED"
}
```

---

## 6. Configuring the Android Application

1. Open `app/src/main/java/com/blackout/emergency/utils/Constants.java`.
2. Update the default Supabase project URL:
   ```java
   public static final String DEFAULT_SUPABASE_URL = "https://<your-project-ref>.supabase.co";
   public static final String DEFAULT_SUPABASE_ANON_KEY = "<your-anon-key>";
   ```
3. Alternatively, the application reads custom configuration dynamically from Android `SharedPreferences`:
   - Preference key: `pref_supabase_url`
   - Preference key: `pref_supabase_anon_key`

---

## 7. Security Best Practices

1. **Least Privilege**: The Android client uses **ONLY** the public publishable anon key.
2. **No Privileged Keys in APK**: `SUPABASE_SERVICE_ROLE_KEY` is strictly confined to server-side Edge Functions and never distributed in the Android APK.
3. **Database RLS**: Mobile anon clients have zero direct table write permissions; all writes flow through the Edge Function which validates protocol version, payload integrity hash, and hop constraints before insertion.
