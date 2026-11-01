# BLACKOUT — SECURITY MODEL & THREAT ANALYSIS
## Zero-Trust Distribution Gateways & Threat Mitigations (Phase 7)

### Executive Overview
In disaster response and civil defense, adversaries or faulty intermediaries may attempt to cause panic by broadcasting fake evacuations, altering affected coordinates, or replaying expired advisories. The BLACKOUT security model assumes that **the entire distribution network—including cell towers, relay smartphones, and Wi-Fi Direct peers—is completely untrusted**.

---

## 1. Threat Model & Mitigations

| Threat | Attack Vector | Technical Mitigation |
|---|---|---|
| **Warning Forgery (Spoofing)** | Adversary fabricates a catastrophic flood warning and broadcasts it over Bluetooth. | **Ed25519 Asymmetric Cryptography**: Receiving devices require valid digital signatures generated with an authorized private key. Signatures cannot be forged without knowing the 256-bit private key. |
| **Payload Tampering** | Adversary alters an authentic warning's headline (e.g. changing evacuation route from North to South). | **Canonical Hash Integrity**: Receiving devices reconstruct canonical UTF-8 bytes (`BLACKOUT_WARNING_V1`). Any byte change results in signature verification failure (`INVALID_SIGNATURE`). |
| **Revision Rollback Attack** | Adversary replays an older Revision 1 (with smaller evacuation boundaries) after Revision 2 was issued. | **Strict Monotonic Revision Check**: Local database and relay manager reject incoming warnings where `incoming.revision <= local.revision` (`ROLLBACK_ATTEMPT`). |
| **Stale / Replay Attack** | Adversary broadcasts a valid warning from 2 years ago during clear weather. | **Mandatory Expiration Enforcement**: Devices reject warnings where `currentTime > expiresAt` with status `EXPIRED`. |
| **Malicious Relay / Man-in-the-Middle** | Intermediate node modifies transit packets or injects garbage data. | **Zero-Trust Gateways**: Gateways are treated as passive bit-pipes. Intermediate nodes cannot create valid signatures. Receiving devices independently verify every hop. |
| **Private Key Compromise** | Backend server is compromised and signing key seed is leaked. | **Key Revocation Protocol**: Emergency authority publishes a revocation directive using an air-gapped backup root key. Devices disqualify warnings signed by the compromised key (`REVOKED_SIGNING_KEY`). |
| **Unauthorized Publishing** | Low-privilege website operator attempts to issue an alert without authorization. | **Role-Based Workflow Enforcement**: Backend Edge Functions enforce database checks: warning status must be `APPROVED` by a supervisor before signing endpoint executes. |

---

## 2. Core Security Invariants

### 2.1 The Zero-Trust Distribution Gateway Principle
> *"The phone that downloads a warning from the internet is only a transit carrier. It is not an authority, and its word is never trusted by downstream nodes."*

Each receiving smartphone—whether hop 1, hop 5, or hop 20:
1. Re-assembles the canonical warning string locally.
2. Resolves the signing public key from its internal trusted key store.
3. Performs an independent Ed25519 cryptographic verification using Bouncy Castle.
4. Checks expiration, future effective dates, and local geographical relevance.
5. Emits alerts only if all cryptographic and temporal checks pass.

### 2.2 Air-Gapped Trust Anchors
- Android APKs embed the root authority public key (`KEY-BLACKOUT-ROOT-2026`) in code.
- No network access is required to verify warnings signed by this root key.
- A phone fresh out of the box in an isolated disaster valley can verify official emergency warnings immediately upon receiving them via Nearby Connections.

### 2.3 Rollback Protection
To prevent attackers from suppressing newer emergency instructions, Room entities maintain a dual structure:
- `public_warnings`: Holds the latest active, highest-revision warning per `warningId`.
- `warning_revisions`: Append-only immutable historical audit ledger preserving all superseded revisions.
- Attempting to overwrite an active record with an equal or lower revision is rejected at the DAO and repository layers.

