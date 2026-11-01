# BLACKOUT — Phase 8 Known Limitations & Operational Boundaries

## 1. Physical Hardware & RF Environment Boundaries

### 1.1 Physical Multi-Device Nearby Connections Testing
- **Current Test Status**: Multi-hop relay, inventory differential synchronization, duplicate suppression, and payload routing were thoroughly verified across simulated 4-node topologies in `OfflineWarningRelayMultiHopTest.java`.
- **Limitation**: In the current CI and single-machine workstation environment, physical Android handsets with independent Bluetooth 5.0 and Wi-Fi Direct chipsets were not connected to an ADB device matrix.
- **Real-World Factors Not Present in Emulation**:
  - RF attenuation from concrete walls, metal debris, and topography in disaster zones.
  - Human body radio absorption and handset orientation changes.
  - Operating system OEM battery optimizations (e.g., Samsung OneUI or Xiaomi MIUI aggressive background service termination).
  - Bluetooth co-existence interference in dense shelter environments.
- **Recommended Remediation**: Conduct field drills with 5–10 physical Android test devices running in battery-saver mode before operational deployment.

---

## 2. Cryptographic Key Management & Production Vault

### 2.1 Development Key Fallback
- For local development and automated integration testing without cloud secrets infrastructure, `supabase/functions/warnings/index.ts` and `web/src/services/signingService.ts` include fallback development seeds (`DEFAULT_PRIV_KEY_BASE64` and `ROOT_KEY_ID = "KEY-BLACKOUT-ROOT-2026"`).
- **Production Requirement**:
  - In a production environment, `WARNING_SIGNING_PRIVATE_KEY` must be provisioned exclusively through Supabase Vault or hardware security modules (HSM) such as Google Cloud KMS or AWS CloudHSM.
  - The private key must never be stored in plain text or browser memory.
  - Public keys distributed to Android devices must be signed by an offline Master Authority Key.

---

## 3. Web Operations Portal Map Visualization

- `web/package.json` was maintained with `maplibre-gl 4.7.1` (the secure, modern release of MapLibre GL) powering `EmergencyMap.tsx`.
- While automated unit tests mock browser DOM and Canvas interactions using `jsdom`, WebGL hardware acceleration is not exercised in headless test runners. Full browser manual rendering should be verified across Chromium and Firefox.

---

## 4. Scope & Authority Disclaimers

1. **Non-Integration with Government EAS / CAP**:
   - BLACKOUT is a resilient, offline-first peer-to-peer disaster communication prototype. It does not claim direct integration with official government Emergency Alert Systems (EAS), Common Alerting Protocol (CAP) national gateways, or 911/112 public safety answering points without formal memorandum of understanding (MoU).
2. **Phase Boundaries**:
   - In accordance with the Phase 8 Master Prompt constraints, no Phase 9 features were initiated, no production APKs were published to app stores, and no public domains were configured.
