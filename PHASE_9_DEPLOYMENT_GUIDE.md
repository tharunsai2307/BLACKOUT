# BLACKOUT — Phase 9 Production Deployment Guide

## 1. Prerequisites & Architectural Separation

Before deploying the BLACKOUT infrastructure into a live staging or production environment, ensure that production credentials and domains are strictly decoupled from development instances:

- **Development Environment**: Local Supabase emulation or sandbox project (`DEFAULT_SUPABASE_URL = "https://your-project-id.supabase.co"`).
- **Production Environment**: Dedicated, isolated Supabase project with automated Point-in-Time Recovery (PITR) and separate project credentials.

---

## 2. Supabase Production Database Deployment

### 2.1 Database Migrations Execution Order
The database schema consists of three sequential, non-destructive SQL migrations located in `supabase/migrations/`:

1. `20261101000000_phase5_central_backend.sql`
   - Provisions: `emergency_alerts`, `gateway_upload_history`, `emergency_alert_events`, `device_registrations`.
   - Constraints: Severity check constraints, coordinate bounds ($-90 \le \text{lat} \le 90$, $-180 \le \text{lon} \le 180$), priority bounds.
   - Row-Level Security: Mandatory RLS enabled across all tables.

2. `20261101010000_phase6_operations_portal.sql`
   - Provisions: `user_profiles`, `incident_assignments`, `response_teams`, `incident_verification_logs`, `audit_logs`.
   - Enums: `app_role_enum` (`SUPER_ADMIN`, `CONTROL_ROOM_OPERATOR`, `FIELD_RESPONSE_COORDINATOR`, `PUBLIC_WARNING_PUBLISHER`, `VIEWER`), `team_status_enum`.
   - Role-Based Access Control: Granular SELECT, INSERT, and UPDATE policies based on `user_profiles.role`.

3. `20261101020000_phase7_signed_public_warnings.sql`
   - Provisions: `public_warnings`, `warning_revisions`, `warning_signing_keys`, `warning_revocations`.
   - Indexes: B-tree indexes on `status`, `expires_at`, `signing_key_id`, and `warning_code`.

### 2.2 Execution via Supabase CLI
```bash
# 1. Login to Supabase CLI
supabase login

# 2. Link your production project
supabase link --project-ref <your-production-project-ref>

# 3. Apply database migrations sequentially
supabase db push
```

### 2.3 Post-Migration Security Checklist
- [ ] Confirm all tables report `Row Level Security: Enabled`.
- [ ] Verify foreign keys: `user_profiles.id` references `auth.users(id) ON DELETE CASCADE`.
- [ ] Confirm no service-role keys are exposed in client-facing tables.

---

## 3. Supabase Edge Functions Deployment

BLACKOUT requires two serverless Edge Functions deployed on Deno:

### 3.1 Function 1: `/emergency-alerts`
- **Purpose**: Authenticates and validates incoming emergency SOS payloads uploaded by gateway mobile devices. Computes SHA-256 payload hashes and enforces idempotency.
- **Deployment Command**:
  ```bash
  supabase functions deploy emergency-alerts --project-ref <your-production-project-ref>
  ```

### 3.2 Function 2: `/warnings`
- **Purpose**: Implements the public warning publishing workflow, RBAC authorization, deterministic canonical serialization, Ed25519 digital signing, and public key registry.
- **Deployment Command**:
  ```bash
  supabase functions deploy warnings --project-ref <your-production-project-ref>
  ```

### 3.3 Required Production Secrets Configuration
Set production secrets inside Supabase Vault using the CLI:
```bash
# 1. Provision Root Ed25519 Private Signing Key (32-byte Base64)
supabase secrets set WARNING_SIGNING_PRIVATE_KEY="<PROD_ED25519_PRIVATE_KEY_BASE64>"

# 2. Confirm default Supabase URL and Service Role Key are populated automatically
supabase secrets list
```

---

## 4. Emergency Operations Web Portal Deployment

### 4.1 Hosting Platform Recommendation
Based on the portal's client-side SPA architecture (React 18 + Vite + Tailwind CSS), the recommended hosting providers in order of operational reliability:

1. **Cloudflare Pages** *(Recommended for Disaster Resilience)*
   - **Pros**: Global Anycast edge network (300+ cities), unmetered DDoS mitigation, 100% uptime SLA on paid tier, zero cold starts for static assets.
   - **Cost**: Free tier covers unlimited bandwidth and requests; Pro plan at $20/month.
2. **Vercel**
   - **Pros**: Zero-configuration Git integration, preview environments, instant rollback.
   - **Cost**: Free Hobby plan; Pro plan at $20/user/month.
3. **Netlify**
   - **Pros**: High reliability, built-in form handling, edge prerendering.
   - **Cost**: Free Starter plan; Pro plan at $19/user/month.

### 4.2 Production Build Steps
```bash
# Inside web/ directory
cd web

# 1. Clean dependencies installation
npm ci

# 2. Execute unit and RBAC tests
npm test -- --run

# 3. Build optimized production distribution
npm run build
```
Build output is generated in `web/dist/`.

### 4.3 Environment Variables Configuration
Configure the following environment variables in your hosting provider's dashboard:

| Variable | Description | Example Production Value |
|---|---|---|
| `VITE_SUPABASE_URL` | Production Supabase API URL | `https://<prod-project-id>.supabase.co` |
| `VITE_SUPABASE_ANON_KEY` | Production Supabase Public Publishable Anon Key | `eyJhbGciOi...` |
| `VITE_MAP_TILE_URL` | High-availability Map Tile Server | `https://tile.openstreetmap.org/{z}/{x}/{y}.png` |

*Security Warning: Never set `SUPABASE_SERVICE_ROLE_KEY` or `WARNING_SIGNING_PRIVATE_KEY` in frontend environment variables.*

### 4.4 Authentication Redirect URL Configuration
In the Supabase Dashboard under **Authentication $\to$ URL Configuration**:
- **Site URL**: `https://portal.blackout.emergency` (or your domain)
- **Redirect URLs**:
  - `https://portal.blackout.emergency/login`
  - `https://portal.blackout.emergency/dashboard`

---

## 5. Android Application Release & Distribution

### 5.1 Pre-Release Checklist
- [ ] Application ID: `com.blackout.emergency`
- [ ] Minimum SDK: API 26 (Android 8.0 Oreo)
- [ ] Target SDK: API 37
- [ ] Cleartext traffic strictly disabled via `network_security_config.xml`
- [ ] Backup disabled via `android:allowBackup="false"`
- [ ] R8 code minification and tree shaking verified in `assembleRelease`

### 5.2 Release Artifacts
1. **Unsigned Release APK**: `app/build/outputs/apk/release/app-release-unsigned.apk`
   - Purpose: Direct side-loading, offline field distribution, and MDM deployment.
2. **Release App Bundle (AAB)**: `app/build/outputs/bundle/release/app-release.aab`
   - Purpose: Google Play Store publishing with Play App Signing.

### 5.3 Keystore Signing Instructions
Follow [`RELEASE_GUIDE.md`](file:///c:/Users/Hp/Music/My%20Projects/Java%20Capstone/RELEASE_GUIDE.md) to sign the release APK with your organization's protected release keystore using `apksigner`.

