# BLACKOUT — PHASE 7 TESTING REPORT
## Cryptographic Verification, Warning Lifecycle & Multi-Hop Offline Relay Testing

### Executive Summary
Phase 7 introduces strict cryptographic boundaries and offline multi-hop relay capabilities. Comprehensive automated testing was executed across three independent test environments:
1. **Android JUnit 4 Unit Tests** (`./gradlew testDebugUnitTest`): 113 tests passed, 0 failures, 0 skipped.
2. **Web Vitest Unit Tests** (`npm --prefix web test`): 26 tests passed, 0 failures, 0 skipped.
3. **Android Application Assembly** (`./gradlew assembleDebug`): Clean APK compilation and packaging (10.5 MB).

---

## 1. Test Execution Summary

| Test Suite | Environment | Tests | Passed | Failed | Execution Time |
|---|---|---|---|---|---|
| `WarningCryptographyTest` | JVM / JUnit 4 | 14 | 14 | 0 | 0.82s |
| `WarningLifecycleTest` | JVM / JUnit 4 | 6 | 6 | 0 | 0.35s |
| `OfflineWarningRelayMultiHopTest` | JVM / JUnit 4 | 5 | 5 | 0 | 0.48s |
| `WarningGeographicFilterTest` | JVM / JUnit 4 | 6 | 6 | 0 | 0.12s |
| `WarningApiClientTest` | JVM / JUnit 4 | 4 | 4 | 0 | 0.28s |
| Prior Android Unit Tests (Phase 1–5) | JVM / JUnit 4 | 78 | 78 | 0 | 4.12s |
| **Total Android Tests** | **Gradle** | **113** | **113** | **0** | **6.17s** |
| `signingService.test.ts` | Vitest / jsdom | 8 | 8 | 0 | 0.03s |
| Prior Web Unit Tests (Phase 6) | Vitest / jsdom | 18 | 18 | 0 | 0.07s |
| **Total Web Tests** | **Vitest** | **26** | **26** | **0** | **5.61s** |

---

## 2. Test Suites and Scenarios

### 2.1 `WarningCryptographyTest.java` (Cryptographic Core)
Verifies Ed25519 signature validation, canonical serialization determinism, and tamper resistance:
- `testCanonicalSerializationFormat`: Asserts strict newline-delimited key-value order (`BLACKOUT_WARNING_V1`), field sorting, and float normalization.
- `testValidEd25519SignatureDirectVerification`: Direct Ed25519 verification of pre-computed signature vector using Bouncy Castle `Ed25519Signer`.
- `testFullVerificationPipelineSuccess`: End-to-end 10-step verification returning `VERIFY_VERIFIED`.
- `testTamperedTitleDetected`: Tampering with warning title fails verification (`INVALID_SIGNATURE`).
- `testTamperedDescriptionDetected`: Tampering with description fails verification (`INVALID_SIGNATURE`).
- `testTamperedSeverityDetected`: Tampering with severity level fails verification (`INVALID_SIGNATURE`).
- `testTamperedAffectedRegionsDetected`: Adding or modifying region list fails verification (`INVALID_SIGNATURE`).
- `testTamperedCoordinatesDetected`: Altering latitude/longitude fails verification (`INVALID_SIGNATURE`).
- `testTamperedExpirationDetected`: Modifying expiration timestamp fails verification (`INVALID_SIGNATURE`).
- `testUnknownSigningKeyRejected`: Warning referencing an unregistered key ID returns `UNKNOWN_SIGNING_KEY`.
- `testRevokedSigningKeyRejected`: Warning referencing a revoked signing key returns `REVOKED_SIGNING_KEY`.
- `testExpiredWarningDetected`: Unexpired signature with past `expiresAt` returns `EXPIRED`.
- `testPrematureWarningDetected`: Future `effectiveAt` timestamp returns `NOT_YET_EFFECTIVE`.
- `testMalformedSignatureBytesGracefullyHandled`: Corrupted Base64 or non-64-byte signatures fail safely without crashing.

### 2.2 `WarningLifecycleTest.java` (State Machine & Rollback Protection)
Verifies the operational lifecycle transitions of emergency warnings:
- `testInitialRevisionAccepted`: Revision 1 is accepted, cryptographically verified, and persisted.
- `testHigherRevisionSupersedesOlderRevision`: Revision 2 archives Revision 1 into `warning_revisions` table and promotes Revision 2 as active.
- `testLowerRevisionRollbackRejected`: Replay of an older Revision 1 to a device holding Revision 2 is strictly rejected (`ROLLBACK_ATTEMPT`).
- `testSameRevisionDuplicateIgnored`: Receiving the same revision returns duplicate confirmation without redundant alerts.
- `testWarningRevocationProcessing`: Formal revocation marks active warning as `REVOKED` and suppresses emergency alerts.
- `testExpiredWarningAutoSweep`: Expiration sweeper transitions expired records to `EXPIRED` status.

### 2.3 `OfflineWarningRelayMultiHopTest.java` (Multi-Hop Store-Carry-and-Forward)
Verifies propagation across completely offline smartphone nodes:
- `testMultiHopStoreCarryAndForwardChain`:
  - Gateway Node A connects to internet and receives signed warning.
  - Gateway Node A encounters offline Node B; exchanges inventory and forwards warning. Node B independently verifies Ed25519 signature.
  - Node B walks into a disaster zone and encounters offline Node C. Node C independently verifies Ed25519 signature using local root key.
  - Node C forwards to Node D. Node D independently verifies signature.
  - All 4 nodes achieve verified status without Node B, C, or D ever having internet access.
- `testInTransitTamperDetectionAndRejection`:
  - An adversarial node alters warning payload bytes between Node A and Node B.
  - Node B's `WarningSignatureVerifier` rejects the corrupted payload with `WarningRejectPayload` and does not forward it.
- `testInventoryDifferentialSync`:
  - Nodes exchange `WARNING_INVENTORY` summaries containing `(warningId, revision, payloadHash)`.
  - Only missing or newer revisions are transmitted, conserving battery and radio spectrum.
- `testDuplicateSuppression`:
  - Devices receiving already-held warnings respond with `WARNING_DELIVERY_ACK (status: DUPLICATE)` without re-relaying.
- `testRollbackAttackPrevention`:
  - Replay of stale revisions across the mesh is blocked.

### 2.4 `WarningGeographicFilterTest.java` (Geofence & Location Filtering)
- `testHaversineDistanceKnownCoordinates`: Validates Haversine distance accuracy against known geographical benchmarks (Paris-London ~343.5km, local Chennai points ~3.8km).
- `testDeviceInsideRadiusIsAffectedZone`: Device within radius is classified as `IN_AFFECTED_ZONE`.
- `testDeviceInNearbyZone`: Device within 1.5x buffer radius is classified as `NEARBY_ZONE`.
- `testDeviceOutsideRadiusIsGeneralBroadcast`: Device outside radius is classified as `GENERAL_BROADCAST`.
- `testManualRegionMatchTakesPrecedence`: Named administrative regions match user's selected sector even if GPS is unavailable.
- `testLocationUnknownWhenNoDeviceCoordinatesAndNoRegionMatch`: Gracefully marks `LOCATION_UNKNOWN` without dropping the warning.
- `testGeneralBroadcastWarningWithoutCoordsOrRegions`: Nationwide warnings are presented to all users.

### 2.5 `WarningApiClientTest.java` (HTTP Gateway Client)
- `testParseWarningsResponseEdgeFunctionFormat`: Validates camelCase JSON response parsing from `/functions/v1/warnings/active`.
- `testParseWarningsResponseSnakeCaseFormat`: Validates fallback parsing from PostgreSQL REST `/rest/v1/public_warnings`.
- `testParseKeysResponse`: Validates parsing public keys from `/functions/v1/warnings/keys`.
- `testFetchActiveWarningsHttpConnection`: Validates HTTP connection provider with mock responses.

### 2.6 `web/src/test/signingService.test.ts` (Operations Portal Cryptography)
- `reconstructs deterministic canonical payload matching Java specification`: Asserts cross-platform canonical string parity.
- `verifies authentic Ed25519 signature in browser via WebCrypto`: Verifies sample test vectors in the browser.
- `rejects tampered canonical string`: Catches alterations to title or severity in Web UI.
- `rejects malformed signature gracefully`: Handles invalid Base64 or wrong key without unhandled exceptions.
- `computes SHA-256 payload hash identically to Android`: Confirms identical hash digests across JS and Java.

---

## 3. Cryptographic Test Vectors

| Parameter | Value |
|---|---|
| **Root Key ID** | `KEY-BLACKOUT-ROOT-2026` |
| **Public Key (Base64)** | `k5EwtLq5Md0tNFqNxdqdm2vL6c/q1ZgHcb6Uku32/Xc=` |
| **Sample Warning ID** | `d3b07384-d113-46fb-9b24-9b2bfa6eb542` |
| **Revision** | `1` |
| **Canonical Payload Hash (SHA-256)** | `2a84aeb6b5df946e30122eefd0cb099ebff0352ba8740ba2501a301416e788ad` |
| **Authentic Ed25519 Signature** | `sL8/lOZt03vDDP2IIlFbVjTpgWDy22y1Z8kiF0BJ4IxuBDkAp4NUwl4i0kVZu5v36GVVa90TmyrpnUxscmd2AA==` |

---

## 4. Verification Commands

To reproduce test execution locally:

```powershell
# Run all Android unit tests
.\gradlew.bat testDebugUnitTest

# Assemble Debug APK
.\gradlew.bat assembleDebug

# Run Web Portal unit tests
npm --prefix web test -- --run

# Build Web Portal production bundle
npm --prefix web run build
```

