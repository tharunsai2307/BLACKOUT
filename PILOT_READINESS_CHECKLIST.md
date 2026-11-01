# BLACKOUT — Field Pilot Readiness & Deployment Checklist

## 1. Executive Disclaimer & Boundary of Authority

> [!CAUTION]
> **INDEPENDENT CIVILIAN PLATFORM DISCLAIMER**
> - **BLACKOUT is an independent civilian emergency communication and disaster coordination platform.**
> - It is **NOT** an official emergency services replacement (e.g., 911, 112, 999).
> - Submitting an emergency SOS within BLACKOUT **DOES NOT** automatically notify police, fire rescue, or municipal ambulance dispatchers unless a formal government integration and legal Memorandum of Understanding (MoU) has been enacted and verified.
> - The application must not be marketed or deployed to the public with claims of official civil defense status without explicit governmental certification.

---

## 2. Hardware & Device Qualification Checklist

| Requirement | Specification | Verification Method | Status |
|---|---|---|---|
| **Android OS Version** | Android 8.0 (API 26) through Android 14 (API 34) | Verify `Build.VERSION.SDK_INT >= 26` | Mandatory |
| **Google Play Services** | Version 21.0.0 or higher (Nearby Connections API support) | `GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)` | Mandatory |
| **Radio Hardware** | Bluetooth 4.2+ (BLE Advertising & Scanning) + 2.4/5GHz Wi-Fi | Hardware feature check: `FEATURE_BLUETOOTH_LE`, `FEATURE_WIFI_DIRECT` | Mandatory |
| **Location Hardware** | GNSS / GPS receiver capable of autonomous offline fix | Hardware feature check: `FEATURE_LOCATION_GPS` | Mandatory |
| **Memory & Storage** | Minimum 2 GB RAM, 100 MB free internal flash storage | Check available internal storage for Room SQLite database | Verified |

---

## 3. Operating System & OEM Battery Optimization Policy

Aggressive OEM background task termination can kill the Google Nearby Connections background service and delay store-carry-forward relays. The following device setup steps are required for pilot field devices:

- [ ] **Battery Optimization Exemption**: Prompt user to grant "Unrestricted Battery" mode (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
- [ ] **OEM-Specific Background Locking**:
  - **Samsung (OneUI)**: Settings $\to$ Apps $\to$ BLACKOUT $\to$ Battery $\to$ Select "Unrestricted". Add to "Never Sleeping Apps".
  - **Xiaomi (MIUI / HyperOS)**: Enable "Autostart", set Battery Saver to "No restrictions".
  - **Pixel / Stock Android**: Settings $\to$ Apps $\to$ BLACKOUT $\to$ App battery usage $\to$ "Allow background usage".
- [ ] **Foreground Service Notification**: Ensure permanent notification channel "BLACKOUT Active Mesh Service" remains visible with ongoing notification icon.

---

## 4. Runtime Permissions Audit

Before initiating pilot exercises, verify all runtime permissions are granted:

| Permission Name | System Purpose | Handling on Denial |
|---|---|---|
| `android.permission.ACCESS_FINE_LOCATION` | Required for hardware GPS coordinates and BLE beacon discovery on Android 8–11 | App falls back to coarse cell or manual location entry; Nearby discovery fails on older Android |
| `android.permission.NEARBY_WIFI_DEVICES` | Required on Android 13+ (API 33+) for high-speed Wi-Fi Direct data transfers | App falls back to low-throughput BLE transfer |
| `android.permission.BLUETOOTH_SCAN` | Required on Android 12+ (API 31+) for discovering neighboring mesh nodes | Disables peer discovery |
| `android.permission.BLUETOOTH_ADVERTISE` | Required on Android 12+ (API 31+) for broadcasting node presence | Disables node discoverability |
| `android.permission.BLUETOOTH_CONNECT` | Required on Android 12+ (API 31+) for pairing with Nearby peers | Disables mesh connection handshakes |
| `android.permission.POST_NOTIFICATIONS` | Required on Android 13+ (API 33+) to alert users of verified public emergency warnings | Warning saved to Room DB but head-up visual alert suppressed |

---

## 5. Operations Portal & Personnel Readiness

- [ ] **Operator Role Provisioning**:
  - At least 2 Incident Commanders registered and authenticated in Supabase Auth.
  - At least 1 Authorized Public Warning Publisher with verified Ed25519 signing access.
- [ ] **Portal Connectivity**:
  - Portal tested on Chrome 120+, Firefox 120+, and Safari 17+.
  - Responsive layout verified on tablet (iPad / Galaxy Tab) and desktop monitor (1920x1080).
  - Map tiles (OpenStreetMap / Mapbox) accessible over emergency backhaul or cached locally.
- [ ] **Incident Response Standard Operating Procedure (SOP)**:
  - Triage rule: High-priority Medical and Structural Collapse SOS alerts must be acknowledged within 3 minutes of upload.
  - Verification rule: Unverified alerts must be flagged as `UNDER_INVESTIGATION` before field teams deploy.
  - Public warnings require two-person rule: draft created by Incident Commander, signed and published by Authorized Publisher.

---

## 6. Pilot Execution Sign-Off Matrix

| Milestone | Target Date | Responsible Party | Sign-Off Status |
|---|---|---|---|
| Baseline APK compilation & testing | 2026-11-01 | Lead Mobile Engineer | **APPROVED** |
| Release Keystore Generation | TBD (Pre-Pilot) | Security Officer | Pending User Action |
| Supabase Production Instance Provisioning | TBD (Pre-Pilot) | DevOps Lead | Pending User Action |
| 3-Device Offline Mesh Field Exercise | TBD (Day 1) | Field Operations Coordinator | Pending Hardware |
| Gateway Upload Under Simulated Backhaul | TBD (Day 1) | Network Engineer | Pending Hardware |
| Formal Pilot Review & Debrief | TBD (Day 3) | Incident Commander | Pending Completion |

