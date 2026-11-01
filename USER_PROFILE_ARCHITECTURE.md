# BLACKOUT — User Profile Architecture & Management (Phase 9.5)

## 1. Architectural Overview

The BLACKOUT User Profile subsystem bridges offline personal resilience with centralized civil defense response. It provides responders and citizens with an identity profile containing personal, medical, and contact details.

To ensure survival in black-sky scenarios, profile data follows a **Dual-Layer Persistence Pattern**:
1. **Local Persistent Cache (Tier 1 — Room Database v5)**: Immediate read/write access without requiring network or cloud connectivity. Changes take effect instantly on-device.
2. **Central Operational Store (Tier 2 — Supabase PostgreSQL `user_profiles` Table)**: Authoritative cloud registry synchronized asynchronously whenever internet or cellular connectivity is restored.

---

## 2. Schema Definition & Attribute Matrix

| Field | Room Attribute (`user_profiles`) | Supabase Column | Data Type | Purpose & Safety Relevance |
|---|---|---|---|---|
| Identity ID | `user_id` (Primary Key) | `id` (Primary Key) | `TEXT` / `UUID` | Unique identifier referencing Supabase Auth `auth.users.id` |
| Email | `email` | `email` | `TEXT` | Primary communication and account recovery address |
| Full Name | `full_name` | `full_name` | `TEXT` | Legal name used on field responder rosters and distress manifests |
| System Role | `role` | `role` | `TEXT` / `app_role_enum` | Access privilege: `'CITIZEN'`, `'CONTROL_ROOM_OPERATOR'`, `'FIELD_RESPONSE_COORDINATOR'`, `'PUBLIC_WARNING_PUBLISHER'`, `'SUPER_ADMIN'` |
| Emergency Phone | `phone` | `phone` | `TEXT` | Direct telephone number used for cellular reachability and SMS routing |
| Blood Group | `blood_type` | `blood_type` | `TEXT` | Life-critical field for emergency medical triage (e.g. `O+`, `A-`, `B+`) |
| Jurisdiction / Region | `country` | `country` | `TEXT` | Geographic jurisdiction used for localized public warning delivery |
| Native Language | `preferred_language` | `preferred_language` | `TEXT` | Language tag used for automated translation of civil defense broadcasts |
| Medical Directives | `medical_notes` | `medical_notes` | `TEXT` | Severe allergies, prescription dependencies, pacemaker/diabetic alerts |
| Last Updated | `updated_at` | `updated_at` | `BIGINT` / `TIMESTAMPTZ` | Timestamp used for conflict resolution during cloud synchronization |

---

## 3. Profile Completion & Dynamic Readiness Calculation

In the `ProfileFragment` UI, a dynamic completion progress bar assesses user preparedness based on life-safety criteria:
- **Base Account Identity (20%)**: Assigned upon registration or guest setup.
- **Emergency Phone Number (+20%)**: Required for SMS gateway dispatch and rescue coordination.
- **Medical & Blood Group Profile (+30%)**: Critical for field paramedics handling unconscious victims.
- **Emergency Contacts Configured (+30%)**: At least one verified In-Case-of-Emergency (ICE) contact configured.

A user achieving 100% preparedness provides first responders with the necessary medical and personal telemetry during emergency beacon reception.

---

## 4. UI Implementation Details

### 4.1 ProfileFragment (`fragment_profile.xml` & `ProfileFragment.java`)
- **Visual Avatar**: Automatically generates high-contrast initials badge (e.g., "JD" for John Doe, "GU" for Guest User).
- **Tactical Role Badge**: Color-coded status indicator:
  - Gold badge for `GUEST`.
  - Tactical cyan badge for `CITIZEN`.
  - Emerald green badge for `CONTROL_ROOM_OPERATOR`.
  - Crimson badge for `SUPER_ADMIN`.
- **ICE Quick-Launch Card**: Displays configured contact count and opens `EmergencyContactsActivity`.
- **Medical Summary Card**: Displays blood group and medical notes preview; opens `EditProfileActivity`.
- **Mesh Telemetry Card**: Summarizes active Nearby P2P mesh status (hop limit: 7, store-carry-and-forward active) and navigates to `NearbyFragment` radar.
- **Cloud Synchronization Button**: Triggers immediate sync with Supabase REST gateway when online.

### 4.2 EditProfileActivity (`activity_edit_profile.xml` & `EditProfileActivity.java`)
- Provides clean Material Design inputs with validation.
- Writes changes immediately to the local Room database via `EmergencyRepository.saveUserProfile()`.
- Dispatches asynchronous update request to Supabase GoTrue REST endpoint `/rest/v1/user_profiles` when online.
- Safely reports offline success if network is absent without interrupting the user.
