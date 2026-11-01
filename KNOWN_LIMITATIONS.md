# BLACKOUT — Known System Limitations & Constraints

## 1. Overview & Operational Realism

This document outlines the engineering, environmental, and physical constraints of the **BLACKOUT** emergency communication platform. In a life-safety disaster scenario, overpromising capability can be fatal. All operators, first responders, and technical personnel must understand these boundaries.

---

## 2. Radio Frequency (RF) & Physical Propagation Limitations

1. **Concrete, Rubble, and Structural Attenuation**:
   - 2.4 GHz Bluetooth Low Energy and 5 GHz Wi-Fi signals suffer severe attenuation when propagating through reinforced concrete, metal sheeting, and collapsed structural rubble (typically 15 to 30 dB per wall/barrier).
   - In collapsed structures or subterranean basements, effective peer-to-peer communication range may drop from nominal line-of-sight (15–30 meters) to less than 2–5 meters.
2. **Channel Congestion & Interference**:
   - In dense metropolitan disaster evacuation centers where hundreds of devices attempt Nearby Connections discovery simultaneously, 2.4 GHz ISM band congestion may increase connection establishment latency or cause packet drop.
3. **Hardware Antenna Variance**:
   - Antenna performance varies significantly between budget Android smartphones and premium flagship devices. Low-gain consumer antennas may fail to discover peers that high-gain devices discover easily.

---

## 3. Delay-Tolerant Store-Carry-and-Forward Characteristics

1. **Human Mobility Dependent Propagation**:
   - Because BLACKOUT is an opportunistic mesh network, message forwarding across physical distances relies on the physical movement of human carriers ("data mules").
   - If no human moves between isolated pockets of survivors, emergency messages will remain stored in local Room databases until a carrier node enters proximity. Latency can range from minutes to hours.
2. **Hop Count & Expiration Ceiling**:
   - To prevent broadcast storms and infinite routing loops, messages are strictly capped at 7 hops and a 48-hour Time-to-Live (TTL).
   - Messages that fail to reach an internet-connected gateway within 48 hours or 7 hops are automatically pruned from the active relay queue.

---

## 4. Android Platform & OS Constraints

1. **Google Play Services Dependency**:
   - The current peer-to-peer transport leverages Google Play Services Nearby Connections API (`Strategy.P2P_CLUSTER`).
   - Devices lacking Google Play Services (e.g., de-Googled Android distributions, AOSP builds, Huawei devices running HarmonyOS) cannot participate in the Nearby Connections mesh.
2. **OEM Background Task Killers**:
   - Aggressive battery management implementations (such as Xiaomi MIUI/HyperOS, Samsung OneUI sleep policies, and OnePlus battery optimization) may suspend background WorkManager jobs and terminate foreground services if the user does not explicitly grant unrestricted battery permissions.
3. **Location Permission Requirement for BLE**:
   - On Android 8.0 through 11, the Android OS architecture requires `ACCESS_FINE_LOCATION` to perform Bluetooth LE scanning. If a civilian denies location access out of privacy concerns, Nearby discovery is disabled by the OS.

---

## 5. Security & Infrastructure Boundaries

1. **Public Key Bootstrap**:
   - Offline verification of Ed25519 public warning signatures depends on having the issuing authority's public key present in the device's local `trusted_key_registry`.
   - If an entirely fresh device is provisioned inside a disaster zone with zero prior internet connectivity, it must rely on the pre-bundled root public key in `BuildConfig`. If that root key is compromised and rotated while the device remains offline, the device cannot receive revocations until it encounters a trusted peer or gateway.
2. **Civilian Civilian Identity vs Official Dispatch**:
   - BLACKOUT provides decentralized civilian mesh networking and incident triage. It is not tied to government Computer-Aided Dispatch (CAD) systems unless explicitly bridged via backend APIs.

---

## 6. Build Environment & Testing Constraints

1. **Lack of Physical Multi-Device Hardware in CI/CD**:
   - Automated continuous integration and containerized build environments lack physical Bluetooth LE and Wi-Fi Direct hardware controllers.
   - While multi-hop relay logic, cryptographic verification, and serialization are rigorously tested via synthetic unit tests, real-world multi-device physical RF handoffs must be validated during field pilots.

