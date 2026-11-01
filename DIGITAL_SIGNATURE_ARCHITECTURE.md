# BLACKOUT — DIGITAL SIGNATURE ARCHITECTURE
## Ed25519 Cryptographic Specification & Verification Engine (Phase 7)

### Executive Overview
In disaster scenarios with disrupted telecommunications, trust cannot rely on real-time Certificate Authorities (CA) or online OCSP responders. BLACKOUT employs an offline-first asymmetric digital signature architecture based on **Ed25519** (RFC 8032) to guarantee authenticity, integrity, and non-repudiation of public emergency warnings.

---

## 1. Cryptographic Primitive Selection: Ed25519

### 1.1 Technical Specification
- **Algorithm**: Edwards-curve Digital Signature Algorithm (EdDSA) using SHA-512 and Curve25519.
- **Curve Equation**: $-x^2 + y^2 = 1 - (121665/121666)x^2y^2$ over the prime field $2^{255} - 19$.
- **Key Sizes**:
  - Private Signing Key: 32 bytes (raw seed) / 64 bytes (expanded secret).
  - Public Verification Key: 32 bytes (compressed Edwards-y coordinate).
  - Signature: 64 bytes ($R \parallel S$).
- **Transport Encoding**: Standard Base64 (RFC 4648).

### 1.2 Cross-Platform Library Matrix
To guarantee consistent verification across disparate environments, the cryptographic engine is standardized as follows:

| Component | Platform | Library / API | Implementation |
|---|---|---|---|
| **Backend Signing** | Deno / Supabase Edge Functions | WebCrypto API | `crypto.subtle.importKey("raw", ..., "Ed25519")` + `crypto.subtle.sign()` |
| **Web Portal Verification** | React / Browser | WebCrypto API | `crypto.subtle.importKey("raw", ..., "Ed25519")` + `crypto.subtle.verify()` |
| **Android Verification** | Android API 26+ (Java 11) | Bouncy Castle | `org.bouncycastle.crypto.signers.Ed25519Signer` |
| **Host JVM Unit Tests** | Host Java 11 / 17 | Bouncy Castle | `Ed25519Signer` (deterministic behavior identical to Android) |

---

## 2. Signing Workflow (Backend Service)

The private signing key is stored in protected environment secrets (`WARNING_SIGNING_PRIVATE_KEY_SEED`) accessible exclusively to the Supabase Edge Function:

```
[Authorised Publisher] 
        |
        | Submits Warning Approval
        v
[Supabase Edge Function: /warnings/:id/sign]
        |
        +---> 1. Authenticate user role (requires 'admin' or 'dispatcher')
        |
        +---> 2. Verify warning approval status == 'APPROVED'
        |
        +---> 3. Fetch warning record from PostgreSQL
        |
        +---> 4. Construct canonical UTF-8 string: WarningCanonicalSerializer
        |
        +---> 5. Sign canonical bytes using WebCrypto Ed25519 private key
        |
        +---> 6. Encode 64-byte signature to Base64
        |
        +---> 7. Write signature, signing_key_id, canonical_payload to 'public_warnings'
        |
        +---> 8. Record immutable revision entry in 'warning_revisions'
        |
        +---> 9. Append audit log in 'audit_logs'
        |
        v
[Signed Warning Stored and Ready for Distribution]
```

---

## 3. 10-Step Verification Workflow (Android & Web)

Every node receiving a warning executes this 10-step verification sequence before accepting it as authentic:

```
[Inbound Warning Payload]
            |
            v
   [1. Schema Validation] ----------(Fail)-----> REJECT: INVALID_PAYLOAD
            | Pass
            v
   [2. Protocol Version Check] -----(Fail)-----> REJECT: INVALID_PAYLOAD
            | Pass
            v
   [3. Key Registry Lookup] --------(Not Found)-> REJECT: UNKNOWN_SIGNING_KEY
            | Key Found
            v
   [4. Key Revocation Check] -------(Revoked)--> REJECT: REVOKED_SIGNING_KEY
            | Key Active
            v
   [5. Key Temporal Validity] ------(Expired)--> REJECT: REVOKED_SIGNING_KEY
            | Within validFrom..validUntil
            v
   [6. Canonical Reconstruction]
            | UTF-8 Serialization
            v
   [7. Ed25519 Signature Check] ---(Mismatch)--> REJECT: INVALID_SIGNATURE
            | Valid Match
            v
   [8. Temporal Expiry Check] ------(Past)-----> ACCEPT & MARK: EXPIRED
            | Unexpired
            v
   [9. Future Effective Check] -----(Future)----> ACCEPT & MARK: NOT_YET_EFFECTIVE
            | Active
            v
   [10. Anti-Rollback Check] -------(Older)----> REJECT: ROLLBACK_ATTEMPT
            | Newer Revision
            v
   [ACCEPT: VERIFIED & TRIGGER ALERTS]
```

### Verification Result Codes:
- `VERIFIED`: Signature is cryptographically authentic, key is trusted and active, warning is unexpired and currently effective.
- `INVALID_SIGNATURE`: Cryptographic signature does not match canonical payload bytes (tampering detected).
- `UNKNOWN_SIGNING_KEY`: Signing key ID is not present in local trusted key store.
- `REVOKED_SIGNING_KEY`: Signing key has been revoked by administration.
- `EXPIRED`: Warning has passed its `expiresAt` timestamp.
- `NOT_YET_EFFECTIVE`: Warning `effectiveAt` is in the future.
- `INVALID_PAYLOAD`: Missing required fields, malformed UUID, or negative timestamps.
- `REVOKED_WARNING`: Warning has been formally superseded by an administrative revocation directive.
- `PENDING_KEY_UPDATE`: Key ID recognized as upcoming rotation pending network sync.

---

## 4. Root Trust & Key Distribution

- **Initial Trust Anchor**: Android clients ship with a pre-seeded root public key record:
  - Key ID: `KEY-BLACKOUT-ROOT-2026`
  - Authority: `BLACKOUT Civil Defense & Emergency Response Administration`
  - Public Key (Base64): `k5EwtLq5Md0tNFqNxdqdm2vL6c/q1ZgHcb6Uku32/Xc=`
  - Validity: 2026-01-01 to 2036-01-01.
- **Trust Continuity**: Subsequent operational keys are distributed via `/functions/v1/warnings/keys` and authenticated by cross-signing or root authority certificates.
- **Strict Prohibition**: Private keys are never distributed to clients, never included in APK assets, and never committed to version control.

