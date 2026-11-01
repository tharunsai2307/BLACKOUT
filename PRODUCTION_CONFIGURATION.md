# BLACKOUT — Production Configuration & Hardening Specification

## 1. Overview & Security Mandate

This specification governs the production environment configuration for **BLACKOUT**, spanning the Supabase PostgreSQL database, Supabase Edge Functions, digital signing infrastructure, and mobile client connectivity.

### Strict Security Principles:
1. **Zero Secret Leakage**: No service-role keys, database master passwords, or private signing keys in client code, frontend repositories, or Git.
2. **Severed Dev/Prod Roots**: Development Ed25519 keys must NEVER be promoted to production. Production keys must be generated inside a Hardware Security Module (HSM), Cloud KMS, or air-gapped environment.
3. **Least Privilege Enforcement**: Row-Level Security (RLS) is non-negotiable. Anonymous clients have strictly delimited insert/select permissions, and administrative tasks require authenticated JWTs with role claims.

---

## 2. Supabase Production Environment Setup

### 2.1 Project Configuration Checklist
- **Database Engine**: PostgreSQL 15+
- **Extensions Required**: `pgcrypto`, `uuid-ossp`, `postgis` (for geo-spatial radius queries).
- **SSL / TLS Policy**: Strictly enforce `sslmode=require` for all database pooling and direct connections.
- **Connection Pooling**: Supavisor pooler enabled (Transaction mode on Port 6543, Session mode on Port 5432).

### 2.2 Database Schema & Migrations
The production schema must be applied sequentially from Phase 5 and Phase 7 migrations:
- `supabase/migrations/20260215000000_emergency_alerts_schema.sql` (Base alerts, gateway history, audit logs)
- `supabase/migrations/20260217000000_ed25519_warning_system.sql` (Public warnings, key registry, warning revisions)
- `supabase/migrations/20260218000000_rls_security_hardening.sql` (Production RLS hardening)

#### Migration Safety & Idempotency Rules:
- All DDL statements must utilize `IF NOT EXISTS` or idempotent guard clauses.
- **Zero Destructive Migrations**: `DROP TABLE`, `DROP COLUMN`, and `TRUNCATE` are prohibited in production migration scripts.
- Schema upgrades must maintain backwards compatibility with older mobile client Room database schemas (e.g., Room v1 through v4).

### 2.3 Row Level Security (RLS) Policies
| Table | Permitted Operations | Role / Condition |
|---|---|---|
| `emergency_alerts` | `INSERT` | `anon`, `authenticated` (Civilian gateway uploads with valid SHA-256 payload hash) |
| `emergency_alerts` | `SELECT`, `UPDATE` | `authenticated` with role `OPERATOR`, `INCIDENT_COMMANDER`, `SUPER_ADMIN` |
| `gateway_upload_history` | `INSERT` | `anon`, `authenticated` (Audit record of gateway uploads) |
| `gateway_upload_history` | `SELECT` | `authenticated` with role `INCIDENT_COMMANDER`, `SUPER_ADMIN` |
| `public_warnings` | `SELECT` | `anon`, `authenticated` (Publicly verifiable civil warnings) |
| `public_warnings` | `INSERT`, `UPDATE` | `authenticated` with role `PUBLIC_WARNING_PUBLISHER`, `SUPER_ADMIN` |
| `public_key_registry` | `SELECT` | `anon`, `authenticated` (Public keys for client-side Ed25519 verification) |
| `public_key_registry` | `INSERT`, `UPDATE` | `authenticated` with role `SUPER_ADMIN` exclusively |
| `audit_logs` | `INSERT` | Internal Edge Function / Database triggers only |
| `audit_logs` | `SELECT` | `authenticated` with role `SUPER_ADMIN` exclusively |

### 2.4 Production CORS & Edge Function Ingress
All Supabase Edge Functions (`/emergency-alerts`, `/warnings/:warningId/sign`, `/warnings/:warningId/revoke`, `/keys`) must enforce production CORS headers:
```typescript
const PRODUCTION_ALLOWED_ORIGINS = [
  "https://blackout.emergency.network", // Production Operations Portal domain
  "https://admin.blackout.internal"
];

export const corsHeaders = {
  "Access-Control-Allow-Origin": PRODUCTION_ALLOWED_ORIGINS.join(", "),
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-blackout-gateway-id",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Max-Age": "86400"
};
```

---

## 3. Production Digital Signing Key Management (RFC 8032 Ed25519)

### 3.1 Key Generation & Storage Hierarchy
The master private key for signing public emergency alerts must never exist as plaintext in source code or local workstations.

```
+-------------------------------------------------------------+
|             Air-Gapped Offline Root Ceremony                |
|  - Generate Ed25519 Root Key Pair                           |
|  - Hardware Security Module (YubiKey 5 FIPS / Nitrokey HSM) |
+-------------------------------------------------------------+
                             |
                             v
+-------------------------------------------------------------+
|               Cloud KMS / Vault Key Custody                 |
|  - AWS KMS / Google Cloud KMS / HashiCorp Vault             |
|  - Injected into Supabase Edge Function Secrets:            |
|    `BLACKOUT_ED25519_PRIVATE_KEY_BASE64`                    |
|    `BLACKOUT_ACTIVE_KEY_ID` (e.g., "prod-key-2026-v1")      |
+-------------------------------------------------------------+
                             |
                             v
+-------------------------------------------------------------+
|               Public Key Registry (Supabase DB)             |
|  - Key ID: "prod-key-2026-v1"                               |
|  - Public Key (Base64)                                      |
|  - Status: ACTIVE                                           |
|  - Valid From: 2026-01-01T00:00:00Z                         |
|  - Valid Until: 2027-01-01T00:00:00Z                        |
+-------------------------------------------------------------+
                             |
                             v
+-------------------------------------------------------------+
|               Android Mobile Verification                   |
|  - Client bootstrap bundle: trusted root public key         |
|  - Dynamically cached via `WarningSyncWorker`               |
|  - Verified via Bouncy Castle `Ed25519Signer`               |
+-------------------------------------------------------------+
```

### 3.2 Canonical UTF-8 Serialization Standard
To prevent signature verification discrepancies across Deno (Edge Function), TypeScript (Portal), and Java (Android), all warning payloads must be signed using the exact canonical byte serialization:
```json
{
  "affectedRegions": ["City Center", "North Valley"],
  "category": "EVACUATION",
  "description": "Wildfire approaching rapidly. Evacuate immediately via Highway 101 North.",
  "effectiveAt": "2026-11-01T12:00:00Z",
  "expiresAt": "2026-11-01T18:00:00Z",
  "geographicScope": {
    "latitude": 37.7749,
    "longitude": -122.4194,
    "radiusMeters": 5000.0
  },
  "issuingAuthority": "Civil Protection Emergency Bureau",
  "language": "en",
  "protocolVersion": 1,
  "revision": 1,
  "severity": "EXTREME",
  "title": "IMMEDIATE EVACUATION ORDER",
  "warningId": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
}
```
- **Rules**:
  - Keys sorted in lexicographical order (ASCII).
  - No whitespace between delimiters (`:`, `,`).
  - RFC 3339 UTC timestamps ending with `Z`.
  - Floating numbers serialized without trailing zero decimals if integer (or consistent IEEE 754 float representation).

### 3.3 Key Rotation & Revocation Protocol
1. **Planned Rotation**:
   - Generate `prod-key-2027-v2` 30 days prior to expiration.
   - Insert new public key into `public_key_registry` with status `ACTIVE`.
   - Android mobile clients download new public key during periodic `WarningSyncWorker` runs.
   - Switch Edge Function secret `BLACKOUT_ACTIVE_KEY_ID` to `prod-key-2027-v2`.
   - Retain old public key with status `RETIRED` to verify historical warnings.
2. **Emergency Revocation (Key Compromise)**:
   - Execute Edge Function `/keys/:keyId/revoke` signed by `SUPER_ADMIN`.
   - Update `public_key_registry` record to `status = 'REVOKED'`, `revoked_at = NOW()`.
   - Android devices fetching key updates invalidate all warnings signed by the compromised key.

---

## 4. Mobile Client Environment Configuration

### 4.1 Production Android BuildConfig Settings
In `app/build.gradle`:
```groovy
buildTypes {
    release {
        minifyEnabled true
        shrinkResources true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        
        buildConfigField "String", "SUPABASE_URL", "\"https://your-production-project.supabase.co\""
        buildConfigField "String", "SUPABASE_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...\""
        buildConfigField "String", "TRUSTED_ROOT_KEY_ID", "\"prod-key-2026-v1\""
        buildConfigField "String", "TRUSTED_ROOT_PUBLIC_KEY", "\"MCowBQYDK2VwAyEA...\""
    }
}
```

### 4.2 ProGuard / R8 Hardening Rules
Ensure reflection and serialization used by Room and Bouncy Castle are preserved:
```proguard
# Keep Room Entities and DAOs
-keep class com.blackout.emergency.data.local.** { *; }
-dontwarn com.blackout.emergency.data.local.**

# Keep Bouncy Castle Ed25519 Cryptographic Classes
-keep class org.bouncycastle.crypto.params.Ed25519** { *; }
-keep class org.bouncycastle.crypto.signers.Ed25519** { *; }
-dontwarn org.bouncycastle.**

# Strip debug logging in production
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
```
