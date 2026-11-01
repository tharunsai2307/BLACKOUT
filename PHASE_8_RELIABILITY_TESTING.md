# BLACKOUT — Phase 8 Reliability Engineering & Failure Testing Report

## 1. Scope & Objective

Emergency communications must operate under severe physical and infrastructure degradation. Reliability testing evaluated:
1. **Offline Operation**: Functionality when cell towers, Wi-Fi backhaul, and internet gateways are completely severed.
2. **Multi-Hop Store-Carry-and-Forward Mesh**: Peer discovery, inventory reconciliation, duplicate suppression, and hop degradation.
3. **Database Migration Reliability**: Sequential schema upgrades across Phase 1, Phase 4, Phase 5, and Phase 7 without destructive table drops.
4. **Network Partition & Healing**: Mesh disconnection and reconnection behavior under oscillating links.

---

## 2. Room Database Migration Integrity (`DATA-ROOM-01`)

### 2.1 The Hazard
In legacy versions, `BlackoutDatabase.java` relied on `fallbackToDestructiveMigration()`. When upgrading an existing database across schema versions without defined sequential migrations, SQLite dropped all tables—destroying unforwarded SOS distress signals and local public warnings.

### 2.2 The Remediation
- Implemented `MIGRATION_1_2`: Creates `relay_queue` and `relay_history` tables with 4 performance indexes.
- Implemented `MIGRATION_2_3`: Creates `pending_uploads` table with status and attempt indexes.
- Implemented `MIGRATION_3_4`: Creates `public_warnings`, `warning_revisions`, `trusted_signing_keys`, `warning_relay_history`, and `warning_sync_state` tables with 6 performance indexes.
- Registered sequential migrations: `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)`.
- **Removed** `.fallbackToDestructiveMigration()` entirely.

### 2.3 Verification via `RoomMigrationTest.java`
- Tested sequential version numbering: $1 \to 2$, $2 \to 3$, $3 \to 4$.
- Dynamic proxy interception verified DDL execution for all 7 application tables.
- Verified zero `DROP TABLE` or `TRUNCATE` operations during full sequence upgrades.
- Result: **5 / 5 tests passed**.

---

## 3. Offline Store-Carry-and-Forward Mesh Resilience

### 3.1 Relay Engine Null-Safety (`BUG-RELAY-01`)
- **Fault Identified**: `RelayEngine.java` passed `null` as the executor when constructing `ReceivedMessageHandler`, causing `NullPointerException` on `executor.execute(...)` when receiving an SOS packet from an offline peer.
- **Remediation**:
  - In `ReceivedMessageHandler.java`, made executor null-safe: `this.executor = executor != null ? executor : Executors.newSingleThreadExecutor();`.
  - In `RelayEngine.java`, instantiated and reused a single persistent `ReceivedMessageHandler` instance.
- **Verification**: Verified end-to-end payload handling in `OfflineWarningRelayMultiHopTest.java` and `ReceivedMessageHandlerTest.java`.

### 3.2 Duplicate Suppression & Partition Healing
- Tested network partition oscillation: 50 unique messages relayed across 10 connection cycles (500 transfer opportunities).
- Result: **450 duplicates suppressed** (90.0% bandwidth savings). Only 50 unique records persisted into Room SQLite.
- Verified in `ReliabilityStressPerformanceTest.testNetworkPartitionReconnectionDuplicateSuppression`.

### 3.3 Multi-Hop Chain Degradation
- Test topology: Node A (Origin Gateway) $\to$ Node B $\to$ Node C $\to$ Node D (Offline Device).
- Verified independent Ed25519 cryptographic signature verification at each hop.
- Rejection of in-transit payload tampering before persisting or forwarding.
- TTL expiration: Messages past `Constants.PACKET_TTL_MILLIS` (48 hours) or exceeding `RelayPolicy.DEFAULT_MAX_HOPS` (7 hops) are automatically pruned from the relay queue.

---

## 4. WorkManager & Network Resilience

### 4.1 Exponential Backoff Enforced (`REL-SYNC-01`)
- `WarningSyncWorker.java` configured with:
  - `OneTimeWorkRequest`: `.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)`.
  - `PeriodicWorkRequest`: `.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)`.
- Exponential progression verified in `NetworkResilienceTest.java`:
  - Attempt 1: 30s
  - Attempt 2: 60s
  - Attempt 3: 120s
  - Attempt 4: 240s
  - Attempt 6+: Capped at 15 minutes (`RelayPolicy.MAX_RETRY_BACKOFF_MILLIS`).

### 4.2 Network Fault Recovery Matrix

| Fault Injected | Component Under Test | Expected Behavior | Observed Result | Status |
|---|---|---|---|---|
| SocketTimeoutException | `BackendApiClient` | Marked retryable, status `NETWORK_ERROR` | Retryable flag set, exception caught cleanly | **PASS** |
| SocketTimeoutException | `WarningApiClient` | IOException thrown to WorkManager for exponential backoff | Caught by worker, triggers retry | **PASS** |
| HTTP 500 Server Error | `BackendApiClient` | Status `SERVER_ERROR`, marked retryable | Retryable flag set, backoff scheduled | **PASS** |
| HTTP 429 Rate Limit | `BackendApiClient` | Marked retryable with backoff | Retryable flag set, client avoids storming | **PASS** |
| HTTP 503 Unavailable | `BackendApiClient` | Marked retryable | Retryable flag set | **PASS** |
| Non-JSON HTML Error Body | `WarningApiClient` | JSONException caught safely | Worker logs failure and retries | **PASS** |
| Malformed JSON Body | `BackendApiClient` | Non-fatal parsing | Returns error result without crash | **PASS** |
| Oversized Peer Payload | `NearbyConnectionManager` | Dropped at transport boundary | Discarded, main thread unaffected | **PASS** |

---

## 5. Summary of Automated Reliability Test Suites

1. `RoomMigrationTest.java`: 5 tests, 100% pass rate.
2. `OfflineWarningRelayMultiHopTest.java`: 13 tests, 100% pass rate.
3. `RelayQueueManagerTest.java`: 18 tests, 100% pass rate.
4. `NetworkResilienceTest.java`: 7 tests, 100% pass rate.
