# BLACKOUT — Authentication, Profile & RBAC Test Results (Phase 9.5)

## 1. Test Summary

The Phase 9.5 validation programme executed automated test suites across the Native Android application and the React Emergency Operations Web Portal to verify authentication lifecycles, session persistence, role separation, and database schema migrations.

| Suite | Component | Tests Executed | Passed | Failed | Status |
|---|---|---|---|---|---|
| **Android Unit Suite** | Android Core, Relay, Warnings & Auth | 150 | 150 | 0 | **PASS** |
| **Web Portal Suite** | Vitest RBAC, Services, Formatters & Crypto | 46 | 46 | 0 | **PASS** |
| **Total Automated Tests** | Cross-Platform Ecosystem | **196** | **196** | **0** | **PASS** |

---

## 2. Android Authentication & Profile Unit Tests

### 2.1 `SessionManagerTest`
- **`testSaveSessionStoresAllAuthParameters`**: Verifies that saving an authenticated Supabase session stores the JWT access token, OAuth2 refresh token, user ID, email, full name, and system role (`CITIZEN`), while setting `auth_is_guest` to `false`.
- **`testGuestModeConfiguration`**: Verifies that choosing Guest mode assigns role `GUEST`, leaves access tokens null, and enables immediate offline SOS functionality.
- **`testLogoutClearsTokens`**: Verifies that logging out removes all access tokens, user details, and active session markers.
- **`testOnboardingStatusFlag`**: Confirms that educational onboarding completion is recorded and respected on subsequent app launches.

### 2.2 `RoomProfileAndContactTest`
- **`testUserProfileEntityInitializationAndDefaults`**: Confirms that `UserProfileEntity` stores emergency attributes (blood type `O+`, emergency phone, nationality, preferred language, medical notes) and updates timestamps.
- **`testEmergencyContactEntityPrimaryFlagAndFields`**: Confirms that `EmergencyContactEntity` accurately tracks primary status, relationships (`Spouse`, `Parent`, `Doctor`), and E.164 phone numbers.
- **`testEmergencyContactNonPrimary`**: Verifies handling of secondary emergency contacts.

---

## 3. Database Schema Migration Verification

- **`RoomMigrationTest`**:
  - `MIGRATION_1_2`: Preserves Phase 1–2 emergency messages while adding relay tracking.
  - `MIGRATION_2_3`: Adds `pending_uploads` queue table for internet gateway draining.
  - `MIGRATION_3_4`: Adds `public_warnings`, `warning_revisions`, `trusted_signing_keys`, `warning_relay_history`, `warning_sync_state`, and `warning_revocations`.
  - `MIGRATION_4_5`: Adds `user_profiles` and `emergency_contacts` tables with index on `user_id` and `is_primary`.
  - **Result**: Zero data loss across sequential version upgrades from Version 1 through Version 5.

---

## 4. Web Portal Access Control & RBAC Tests

Executed via Vitest (`web/src/test/rbac.test.ts`):
- **Super Administrator**: Full system access, warning authorization, user administration, and security configuration.
- **Control Room Operator**: Incident triage, map monitoring, team dispatch, and priority escalation.
- **Field Response Coordinator**: Field team status updates, rescue routing, and incident acknowledgment.
- **Public Warning Publisher**: Civil defense warning drafting, Ed25519 signing, and publication.
- **Citizen Account Enforcement**: Citizen accounts attempting to log into the Operations Portal are blocked with an explicit error message directing them to use the Android mobile application.

---

## 5. Security & Boundary Verification

1. **Guest SOS Guarantee**: Tests confirm that a user in Guest mode can generate, store, and relay distress signals over Google Nearby Connections without ever having registered.
2. **Token Security**: Tokens are stored strictly in private Android `SharedPreferences` and are never logged or exposed in unencrypted payloads.
3. **Database Integrity**: All SQLite foreign keys and indexes were verified to execute without syntax or type conflicts.
