# BLACKOUT — SIGNING KEY MANAGEMENT & TRUST INFRASTRUCTURE
## Cryptographic Key Lifecycle, Registry Schema & Security Operations (Phase 7)

### Executive Overview
This document specifies the lifecycle, storage, rotation, and revocation procedures for asymmetric Ed25519 signing keys used to authorize emergency public warnings across the BLACKOUT network.

---

## 1. Key Registry Database Schema (`warning_signing_keys`)

All public keys and trust metadata are maintained in the central Supabase PostgreSQL database:

```sql
CREATE TABLE IF NOT EXISTS public.warning_signing_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_id VARCHAR(64) UNIQUE NOT NULL,
    public_key VARCHAR(128) NOT NULL, -- Base64-encoded raw 32-byte Ed25519 public key
    algorithm VARCHAR(32) NOT NULL DEFAULT 'Ed25519',
    authority_id VARCHAR(128) NOT NULL, -- Issuing organization identity
    valid_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_until TIMESTAMPTZ NOT NULL,
    key_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, RETIRED, REVOKED
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ,
    revocation_reason TEXT,
    metadata JSONB DEFAULT '{}'::jsonb,
    CONSTRAINT check_key_status CHECK (key_status IN ('ACTIVE', 'RETIRED', 'REVOKED'))
);
```

### Row Level Security (RLS) Policy:
- **SELECT**: Public read access (`true`) for all authenticated and anonymous clients (Android devices and browser portals need public keys to verify signatures).
- **INSERT / UPDATE / DELETE**: Restricted strictly to users with role `admin` or database service role.

---

## 2. Key Generation Procedure

Ed25519 key pairs must be generated in a secure air-gapped environment or automated Hardware Security Module (HSM).

### Generation via Node.js / WebCrypto:
```typescript
import { webcrypto } from 'node:crypto';

async function generateEd25519KeyPair() {
  const keyPair = await webcrypto.subtle.generateKey(
    { name: 'Ed25519' },
    true,
    ['sign', 'verify']
  );

  const rawPub = await webcrypto.subtle.exportKey('raw', keyPair.publicKey);
  const rawPriv = await webcrypto.subtle.exportKey('pkcs8', keyPair.privateKey);

  console.log('Public Key (Base64):', Buffer.from(rawPub).toString('base64'));
  console.log('Private Key (Base64):', Buffer.from(rawPriv).toString('base64'));
}
```

### Generation via OpenSSL:
```bash
# 1. Generate private key
openssl genpkey -algorithm Ed25519 -out warning_private_key.pem

# 2. Extract public key
openssl pkey -in warning_private_key.pem -pubout -out warning_public_key.pem
```

---

## 3. Secret Storage & Isolation

- **Production Private Key**:
  - Injected as an environment secret into Supabase Edge Functions: `WARNING_SIGNING_PRIVATE_KEY_SEED`.
  - Never stored in plaintext files, never committed to git repositories, and never readable via client APIs.
- **Root Trust Anchor**:
  - The initial root verification key is hardcoded as an immutable default trust anchor in Android's `TrustedKeyRepository.java`:
    ```java
    public static final String ROOT_KEY_ID = "KEY-BLACKOUT-ROOT-2026";
    public static final String ROOT_PUBLIC_KEY = "k5EwtLq5Md0tNFqNxdqdm2vL6c/q1ZgHcb6Uku32/Xc=";
    public static final String ROOT_AUTHORITY = "BLACKOUT Civil Defense & Emergency Response Administration";
    ```

---

## 4. Key Rotation Lifecycle

```
[ACTIVE] -----------> [RETIRED] -----------> [EXPIRED]
   |                      |
   +---(If Compromised)---+---> [REVOKED]
```

1. **Scheduled Rotation Window**:
   - Keys are rotated every 12 to 24 months.
   - At least 30 days prior to retirement, the new key is generated and registered in `warning_signing_keys` with status `ACTIVE`.
   - Android clients downloading active warnings also fetch key updates via `WarningApiClient.fetchTrustedKeys()`.
2. **Key Retirement (`RETIRED`)**:
   - The retiring key stops being used for new signatures.
   - Old warnings signed by the retired key remain valid until their natural `expiresAt` date passes.
3. **Key Revocation (`REVOKED`)**:
   - Triggered immediately if private key exposure is suspected.
   - Key status is updated to `REVOKED` in the database with a mandatory `revocation_reason`.
   - Android clients sync the revocation record; all warnings signed with that key are immediately disqualified (`REVOKED_SIGNING_KEY`).

---

## 5. Emergency Key Compromise Procedure

If a signing key is suspected of compromise:
1. **Immediate Revocation**: Execute administrative update on `warning_signing_keys` setting `key_status = 'REVOKED'`.
2. **Backend Secret Rotation**: Remove the compromised private key seed from backend secrets and replace with a freshly generated pair.
3. **Revocation Broadcast**: Issue a high-priority signed revocation warning using the backup emergency root key.
4. **Android Mesh Propagation**: Distribute the key revocation update over Nearby Connections to flush corrupted warnings from offline nodes.
