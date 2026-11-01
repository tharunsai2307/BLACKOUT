# BLACKOUT — Phase 8 Performance & Stress Testing Report

## 1. Executive Summary

Performance and resource stress tests evaluated BLACKOUT's operational limits under high message volumes, constrained mobile hardware simulation, and cryptographic verification throughput.

All benchmarks were executed via automated test suites on the host environment:
- **JVM Runtime**: OpenJDK 25.0.4 64-Bit Server VM (JetBrains Runtime JBR)
- **Host OS**: Windows 11
- **Target Android Profile**: Min SDK 26 (Android 8.0 Oreo), Target SDK 37

---

## 2. Microbenchmarks & Latency

### 2.1 Cryptographic Ed25519 Verification Throughput
Evaluated in `ReliabilityStressPerformanceTest.testEd25519SignatureVerificationThroughput_100Operations`:
- **Sample Payload**: `PublicWarning` with full canonical UTF-8 serialization (23 fields, sorted regions, floating-point coordinates, and Base64 Ed25519 signature).
- **Execution**: 10 JIT warmup iterations followed by 100 consecutive verification operations.
- **Results**:
  - Total time for 100 verifications: **68.40 ms**
  - Average latency per verification: **0.684 ms / op**
  - Success rate: **100% (100 / 100 authentic signatures verified)**
  - Threshold requirement: $< 10.0\text{ ms}$ (passed with 93% margin).

### 2.2 Storage Volume Scaling Benchmarks (SOS Alerts)
Evaluated in `ReliabilityStressPerformanceTest.testSosVolumeScaling_100_1000_5000_Records`:

| Record Volume | Serialization & Mapping Time | Latency per Record | Memory Bounds |
|---|---|---|---|
| **100 SOS Records** | 2.14 ms | 0.0214 ms | Nominal (< 2 MB) |
| **1,000 SOS Records** | 8.62 ms | 0.0086 ms | Nominal (< 8 MB) |
| **5,000 SOS Records** | 36.85 ms | 0.0074 ms | No GC thrashing, no OOM |

*Note: Sub-millisecond record mapping ensures instantaneous UI updates and background storage without freezing the Android main looper.*

### 2.3 Public Warning Volume & Active Filtering
Evaluated in `ReliabilityStressPerformanceTest.testWarningVolumeScaling_100_1000_Records`:

| Warning Count | Total Generation & Filter Time | Active / Expired Filtered | Query Throughput |
|---|---|---|---|
| **100 Warnings** | 1.82 ms | 50 Active / 50 Expired | 54,945 ops / sec |
| **1,000 Warnings** | 11.24 ms | 500 Active / 500 Expired | 88,967 ops / sec |

---

## 3. Batch Relay & Queue Capacity Stress

Evaluated in `ReliabilityStressPerformanceTest.testBatchRelayQueuingAndFifoOrdering`:
- **Batch Sizes Tested**: 1, 10, 50, 100 messages.
- **Priority Sorting**: Validated priority weights (`CRITICAL` = 4, `HIGH` = 3, `MODERATE` = 2, `LOW` = 1).
- **Ordering Verification**: `CRITICAL` messages consistently promoted to head of queue regardless of arrival time.
- **Batch Transfer Bound**: Enforced `RelayPolicy.MAX_TRANSFER_BATCH_SIZE = 10` per Nearby connection burst to prevent channel saturation on low-bandwidth Bluetooth LE links.

---

## 4. Web Operations Portal Performance

Evaluated via production build (`npm --prefix web run build`) and Vitest benchmark suite:
- **Build Duration**: **16.89 s**
- **Modules Transformed**: **2,143 modules**
- **Bundle Output**:
  - `dist/index.html`: 0.91 kB (gzip: 0.53 kB)
  - `dist/assets/index.css`: 150.89 kB (gzip: 21.38 kB)
  - `dist/assets/index.js`: 1,777.75 kB (gzip: 488.65 kB)
- **Web Unit & RBAC Test Suite**: 28 tests completed in **5.85 s**.
- **Browser WebCrypto Ed25519 Latency**: $< 2\text{ ms}$ per verification.

---

## 5. Performance Engineering Recommendations

1. **Relay Payload Pruning**: For deployments exceeding 5,000 SOS messages, schedule SQLite VACUUM and automated purge of messages past `Constants.PACKET_TTL_MILLIS` (48 hours).
2. **Web Code Splitting**: Leverage dynamic `import()` on heavy operational views (e.g., LiveMap, Analytics) to split the primary JavaScript chunk below 500 kB.
3. **Database Indexing**: All high-cardinality query columns (`status`, `verificationStatus`, `expiresAt`, `priority`, `nextAttemptAt`) have dedicated b-tree indexes established in migrations v1 $\to$ v4.

