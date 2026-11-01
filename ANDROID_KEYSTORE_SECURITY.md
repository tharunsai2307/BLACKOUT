# BLACKOUT — ANDROID KEYSTORE SECURITY ARCHITECTURE & IMPLEMENTATION REPORT
## Cryptographic Credential Protection, Key Lifecycle & Threat Mitigation (Phase 10)

**Date**: November 2026  
**Component**: Android Native Mobile Client (`com.blackout.emergency.auth`)  
**Security Level**: High / Production Hardened  
**Target Platform**: Android API 26+ (Android 8.0 Oreo through Android 15+)

---

## 1. Architectural Overview & Problem Statement

In previous iterations (Phases 1–9.5), user authentication session tokens were stored in private application SharedPreferences (`blackout_auth_session.xml`). While standard Android sandbox permissions isolate private SharedPreferences between applications on uncompromised devices, plaintext credential storage presented severe vulnerabilities:
1. **Device Root / Forensic Extraction**: In emergency and disaster zones, physical devices may be salvaged, searched, or compromised. Plaintext JWT `access_token` and `refresh_token` strings could be extracted directly from flash memory or backup tarballs.
2. **Token Rotation Race Conditions**: Supabase Auth enforces Refresh Token Rotation (RTR). Concurrent network requests encountering expired 401 statuses could attempt simultaneous token exchange. The first request invalidates the refresh token; the second fails, resulting in abrupt session termination.
3. **Accidental Deletion of Critical Emergency Data**: In an offline-first disaster response system, clearing authentication sessions (e.g. on logout or token expiration) must never delete pending SOS messages, peer relay queues, trusted Ed25519 public warning keys, or emergency contacts.

Phase 10 introduces a hardware-isolated, authenticated encryption architecture centered on `KeyStoreCryptoManager` and an upgraded `SessionManager`.

---

## 2. Cryptographic Specifications

```
+------------------------------------------------------------------------------------+
|                                 CRYPTO ENGINE SPEC                                 |
+------------------------------------------------------------------------------------+
| Algorithm               | Advanced Encryption Standard (AES)                       |
| Key Size                | 256 bits (32 bytes)                                      |
| Cipher Mode             | Galois/Counter Mode (GCM) with AEAD                      |
| Padding                 | NoPadding                                                |
| Key Storage Provider    | AndroidKeyStore (Hardware TEE / StrongBox backed)        |
| Key Alias               | blackout_aes_auth_key                                    |
| IV (Initialization Vec) | 96 bits (12 bytes) cryptographically secure random bytes |
| Authentication Tag      | 128 bits (16 bytes) verified automatically by AEAD       |
| Serialized Format       | Base64([ 12-byte IV || Ciphertext || 16-byte Tag ])       |
+------------------------------------------------------------------------------------+
```

### 2.1 Authenticated Encryption (AES-GCM)
Galois/Counter Mode provides both **confidentiality** (preventing unauthorized reading of token bytes) and **authenticity/integrity** (detecting any modification or bit-flipping of the ciphertext).
- Every encryption operation invokes `SecureRandom` to generate a fresh 12-byte IV.
- The 12-byte IV is prepended to the ciphertext and authentication tag.
- Decryption separates the first 12 bytes as the IV, configures `GCMParameterSpec(128, iv)`, and executes `cipher.doFinal()`.
- If a single bit of the IV, ciphertext, or tag is altered, the underlying cryptographic engine throws `AEADBadTagException`, safely preventing padding-oracle and chosen-ciphertext attacks.

---

## 3. KeyStore Key Lifecycle & Fallback Strategy

### 3.1 Hardware-Backed Key Generation
When running on an Android device:
```java
KeyGenerator keyGenerator = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
        "blackout_aes_auth_key",
        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setRandomizedEncryptionRequired(true)
        .build();

keyGenerator.init(spec);
SecretKey key = keyGenerator.generateKey();
```
- The private master AES key never leaves the Android hardware security module (Trusted Execution Environment or StrongBox).
- Decryption operations take place within the secure enclave.

### 3.2 Key Invalidation & Corrupted Ciphertext Recovery
- If the hardware key is invalidated (e.g. by OS lock screen changes or device re-enrollment) or ciphertext becomes corrupted, `KeyStoreCryptoManager` intercepts the exception and wraps it into a typed `CryptoException`.
- `SessionManager` catches this condition, clears only the corrupted authentication credentials, and transitions the app to require fresh re-authentication, **without crashing and without deleting local Room emergency records**.

---

## 4. Separation of Concerns & Migration Architecture

### 4.1 Storage Partitioning
Preferences are strictly partitioned:

| Category | Keys | Storage Mechanism | Security Level |
|---|---|---|---|
| **Sensitive Credentials** | `enc_access_token`, `enc_refresh_token` | AES-256-GCM Ciphertext | Protected by Android Keystore |
| **Token Timing** | `token_expires_at_sec` | Epoch seconds (long) | Plaintext preference |
| **User Profile Metadata** | `user_id`, `email`, `full_name`, `phone`, `role` | SharedPreferences | Non-sensitive profile data |
| **Operating State** | `is_guest_mode`, `onboarding_completed`, `profile_completed` | Boolean flags | Non-sensitive runtime state |

### 4.2 Non-Destructive Plaintext Migration Strategy
To ensure existing app users are not abruptly logged out upon upgrading to Phase 10:
1. `SessionManager` inspects `auth_migrated_v10`. If false and legacy `access_token` exists:
2. Encrypts the legacy `access_token` and `refresh_token` using `KeyStoreCryptoManager.encrypt()`.
3. Performs a dry-run decryption check: verifies `cryptoManager.decrypt(encAccess).equals(legacyAccess)`.
4. Only upon successful verification does it write `enc_access_token`, remove the legacy plaintext `access_token`, and persist `auth_migrated_v10 = true`.
5. If verification fails, sensitive tokens are purged safely, avoiding corrupted credentials.

---

## 5. Token Lifecycle & Concurrency Control

### 5.1 Proactive Expiration Detection
`SessionManager.isTokenExpired()` decodes the JWT claims payload without network access, extracts the `exp` claim, and applies a **30-second clock skew buffer**:
$$\text{isExpired} = \text{nowSec} \ge (\text{expSec} - 30)$$

### 5.2 Thread-Safe Atomic Refresh Mutex
To eliminate Refresh Token Rotation race conditions:
```java
synchronized (refreshLock) {
    if (!isTokenExpired()) {
        listener.onTokenRefreshed(getAccessToken());
        return;
    }
    pendingRefreshListeners.add(listener);
    if (isRefreshing) {
        return; // Join existing in-flight HTTP request
    }
    isRefreshing = true;
}
```
- When multiple background workers or UI components simultaneously encounter an expired token, only a single HTTP network request is dispatched to `/auth/v1/token?grant_type=refresh_token`.
- All callers queue up and are notified simultaneously upon completion.
- If the server returns HTTP 400 (Invalid or Revoked Refresh Token), credentials are purged, preventing infinite retry loops.

### 5.3 Offline Safety & Guest Mode
- In Guest mode, `isGuestMode()` returns `true`. The app operates entirely without tokens or network access.
- When the device is offline, token refresh network failures trigger safe callback notification without purging credentials, allowing the user to remain logged in locally until network connectivity returns.
- Logout purges encrypted tokens from preferences, while preserving Room database tables (`emergency_messages`, `relay_queue`, `emergency_contacts`, `public_warnings`).

---

## 6. Automated Verification Test Suite

The security implementation is verified by two test suites in `app/src/test/java/com/blackout/emergency/auth/`:

### 6.1 `KeyStoreCryptoManagerTest`
- `testEncryptAndDecryptRoundTrip`: Verifies exact recovery of arbitrary JWT tokens.
- `testRandomIvGeneratesDistinctCiphertexts`: Confirms non-deterministic encryption using distinct 12-byte IVs for identical plaintext inputs.
- `testCorruptedCiphertextFailsAuthenticationTag`: Tests bit-flipping of the 128-bit GCM authentication tag, ensuring instant rejection.
- `testCorruptedIvFailsDecryption`: Tests IV tampering rejection.
- `testTruncatedPayloadFailsDecryption`: Verifies rejection of truncated payloads.
- `testKeyDeletionAndRecreation`: Verifies that rotated keys correctly reject ciphertexts produced with previous keys.

### 6.2 `SessionManagerTest`
- `testSaveSessionEncryptsTokensAtRest`: Confirms tokens are encrypted and never stored in plaintext.
- `testMigrationFromLegacyPlaintextSession`: Verifies non-destructive upgrade path.
- `testTokenExpirationDetection`: Verifies JWT expiration boundary testing.
- `testAtomicTokenRefreshQueuing`: Verifies 5 concurrent threads produce exactly ONE network call.
- `testPermanentRefreshFailureSafelyClearsCredentials`: Verifies HTTP 400 response handling.
- `testGuestModeAllowsOfflineOperationWithoutTokens`: Confirms offline guest safety.
- `testLogoutClearsTokensWithoutAffectingRoomData`: Verifies data preservation on logout.

