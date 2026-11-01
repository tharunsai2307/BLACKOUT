package com.blackout.emergency.database;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Main Room Database for the BLACKOUT offline-first emergency platform.
 * Version 5: Added Phase 9.5 Local User Profiles & Emergency Contacts.
 */
@Database(
        entities = {
                EmergencyMessageEntity.class,
                RelayQueueEntity.class,
                RelayHistoryEntity.class,
                PendingUploadEntity.class,
                PublicWarningEntity.class,
                WarningRevisionEntity.class,
                TrustedSigningKeyEntity.class,
                WarningRelayHistoryEntity.class,
                WarningSyncStateEntity.class,
                WarningRevocationEntity.class,
                EmergencyContactEntity.class,
                UserProfileEntity.class
        },
        version = 5,
        exportSchema = false
)
public abstract class BlackoutDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "blackout_emergency.db";
    private static volatile BlackoutDatabase instance;

    public abstract EmergencyMessageDao emergencyMessageDao();
    public abstract RelayQueueDao relayQueueDao();
    public abstract RelayHistoryDao relayHistoryDao();
    public abstract PendingUploadDao pendingUploadDao();

    // Phase 7 DAOs
    public abstract PublicWarningDao publicWarningDao();
    public abstract WarningRevisionDao warningRevisionDao();
    public abstract TrustedSigningKeyDao trustedSigningKeyDao();
    public abstract WarningRelayHistoryDao warningRelayHistoryDao();
    public abstract WarningSyncStateDao warningSyncStateDao();
    public abstract WarningRevocationDao warningRevocationDao();

    // Phase 9.5 DAOs
    public abstract EmergencyContactDao emergencyContactDao();
    public abstract UserProfileDao userProfileDao();

    /**
     * Migration from Version 1 to Version 2:
     * Adds Phase 4 multi-hop store-carry-and-forward relay queue and delivery history.
     */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS relay_queue ("
                    + "messageId TEXT NOT NULL PRIMARY KEY, "
                    + "relayStatus TEXT, "
                    + "queuedAt INTEGER NOT NULL, "
                    + "nextAttemptAt INTEGER NOT NULL, "
                    + "attemptCount INTEGER NOT NULL, "
                    + "lastAttemptAt INTEGER NOT NULL, "
                    + "lastError TEXT, "
                    + "priority TEXT)");

            db.execSQL("CREATE INDEX IF NOT EXISTS index_relay_queue_relayStatus ON relay_queue (relayStatus)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_relay_queue_nextAttemptAt ON relay_queue (nextAttemptAt)");

            db.execSQL("CREATE TABLE IF NOT EXISTS relay_history ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "messageId TEXT NOT NULL, "
                    + "fromDeviceId TEXT, "
                    + "toDeviceId TEXT, "
                    + "hopNumber INTEGER NOT NULL, "
                    + "transferStatus TEXT, "
                    + "timestamp INTEGER NOT NULL)");

            db.execSQL("CREATE INDEX IF NOT EXISTS index_relay_history_messageId ON relay_history (messageId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_relay_history_toDeviceId ON relay_history (toDeviceId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_relay_history_messageId_toDeviceId ON relay_history (messageId, toDeviceId)");
        }
    };

    /**
     * Migration from Version 2 to Version 3:
     * Adds Phase 5 internet gateway upload queue.
     */
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS pending_uploads ("
                    + "messageId TEXT NOT NULL PRIMARY KEY, "
                    + "uploadStatus TEXT, "
                    + "gatewayDeviceId TEXT, "
                    + "attemptCount INTEGER NOT NULL, "
                    + "nextAttemptAt INTEGER NOT NULL, "
                    + "lastAttemptAt INTEGER NOT NULL, "
                    + "uploadedAt INTEGER NOT NULL, "
                    + "backendReference TEXT, "
                    + "lastErrorCode TEXT)");

            db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_uploads_uploadStatus ON pending_uploads (uploadStatus)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_uploads_nextAttemptAt ON pending_uploads (nextAttemptAt)");
        }
    };

    /**
     * Migration from Version 3 to Version 4:
     * Adds Phase 7 public warnings and digital signature infrastructure.
     */
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // 1. Table: public_warnings
            db.execSQL("CREATE TABLE IF NOT EXISTS public_warnings ("
                    + "warningId TEXT NOT NULL PRIMARY KEY, "
                    + "protocolVersion INTEGER NOT NULL, "
                    + "revision INTEGER NOT NULL, "
                    + "title TEXT, "
                    + "description TEXT, "
                    + "category TEXT, "
                    + "severity TEXT, "
                    + "issuingAuthority TEXT, "
                    + "issuedAt INTEGER NOT NULL, "
                    + "effectiveAt INTEGER NOT NULL, "
                    + "expiresAt INTEGER NOT NULL, "
                    + "affectedRegionsJson TEXT, "
                    + "latitude REAL, "
                    + "longitude REAL, "
                    + "radiusMeters REAL, "
                    + "language TEXT, "
                    + "status TEXT, "
                    + "signingKeyId TEXT, "
                    + "signatureAlgorithm TEXT, "
                    + "signature TEXT, "
                    + "canonicalPayload TEXT, "
                    + "verificationStatus TEXT, "
                    + "lastReceivedAt INTEGER NOT NULL, "
                    + "lastRelayedAt INTEGER NOT NULL, "
                    + "sourceDeviceId TEXT, "
                    + "payloadHash TEXT)");

            db.execSQL("CREATE INDEX IF NOT EXISTS index_public_warnings_status ON public_warnings (status)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_public_warnings_verificationStatus ON public_warnings (verificationStatus)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_public_warnings_expiresAt ON public_warnings (expiresAt)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_public_warnings_signingKeyId ON public_warnings (signingKeyId)");

            // 2. Table: warning_revisions
            db.execSQL("CREATE TABLE IF NOT EXISTS warning_revisions ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "warningId TEXT NOT NULL, "
                    + "revision INTEGER NOT NULL, "
                    + "title TEXT, "
                    + "description TEXT, "
                    + "category TEXT, "
                    + "severity TEXT, "
                    + "issuingAuthority TEXT, "
                    + "issuedAt INTEGER NOT NULL, "
                    + "effectiveAt INTEGER NOT NULL, "
                    + "expiresAt INTEGER NOT NULL, "
                    + "affectedRegionsJson TEXT, "
                    + "latitude REAL, "
                    + "longitude REAL, "
                    + "radiusMeters REAL, "
                    + "language TEXT, "
                    + "status TEXT, "
                    + "signingKeyId TEXT, "
                    + "signatureAlgorithm TEXT, "
                    + "signature TEXT, "
                    + "canonicalPayload TEXT, "
                    + "verificationStatus TEXT, "
                    + "archivedAt INTEGER NOT NULL, "
                    + "payloadHash TEXT)");

            db.execSQL("CREATE INDEX IF NOT EXISTS index_warning_revisions_warningId_revision ON warning_revisions (warningId, revision)");

            // 3. Table: trusted_signing_keys
            db.execSQL("CREATE TABLE IF NOT EXISTS trusted_signing_keys ("
                    + "keyId TEXT NOT NULL PRIMARY KEY, "
                    + "publicKeyBase64 TEXT NOT NULL, "
                    + "algorithm TEXT, "
                    + "authorityId TEXT, "
                    + "validFrom INTEGER NOT NULL, "
                    + "validUntil INTEGER NOT NULL, "
                    + "keyStatus TEXT, "
                    + "revokedAt INTEGER, "
                    + "revocationReason TEXT, "
                    + "createdAt INTEGER NOT NULL)");

            db.execSQL("CREATE INDEX IF NOT EXISTS index_trusted_signing_keys_keyStatus ON trusted_signing_keys (keyStatus)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_trusted_signing_keys_authorityId ON trusted_signing_keys (authorityId)");

            // 4. Table: warning_relay_history
            db.execSQL("CREATE TABLE IF NOT EXISTS warning_relay_history ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "warningId TEXT NOT NULL, "
                    + "revision INTEGER NOT NULL, "
                    + "peerDeviceId TEXT, "
                    + "relayAction TEXT, "
                    + "timestamp INTEGER NOT NULL, "
                    + "details TEXT)");

            db.execSQL("CREATE INDEX IF NOT EXISTS index_warning_relay_history_warningId_peerDeviceId ON warning_relay_history (warningId, peerDeviceId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_warning_relay_history_timestamp ON warning_relay_history (timestamp)");

            // 5. Table: warning_sync_state
            db.execSQL("CREATE TABLE IF NOT EXISTS warning_sync_state ("
                    + "syncKey TEXT NOT NULL PRIMARY KEY, "
                    + "lastSyncTimestamp INTEGER NOT NULL, "
                    + "lastItemCount INTEGER NOT NULL, "
                    + "status TEXT, "
                    + "lastError TEXT)");

            // 6. Table: warning_revocations
            db.execSQL("CREATE TABLE IF NOT EXISTS warning_revocations ("
                    + "warningId TEXT NOT NULL PRIMARY KEY, "
                    + "revision INTEGER NOT NULL, "
                    + "revocationReason TEXT, "
                    + "revocationSignature TEXT, "
                    + "revokedAt INTEGER NOT NULL)");
        }
    };

    /**
     * Migration from Version 4 to Version 5:
     * Adds Phase 9.5 emergency contacts and user profile cache.
     * Preserves all existing SOS alerts, public warnings, and upload records.
     */
    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // 1. Table: emergency_contacts
            db.execSQL("CREATE TABLE IF NOT EXISTS emergency_contacts ("
                    + "id TEXT NOT NULL PRIMARY KEY, "
                    + "userId TEXT NOT NULL, "
                    + "contactName TEXT NOT NULL, "
                    + "phoneNumber TEXT NOT NULL, "
                    + "relationship TEXT, "
                    + "isPrimary INTEGER NOT NULL DEFAULT 0, "
                    + "syncStatus TEXT NOT NULL DEFAULT 'SYNCED', "
                    + "updatedAt INTEGER NOT NULL)");

            db.execSQL("CREATE INDEX IF NOT EXISTS index_emergency_contacts_userId ON emergency_contacts (userId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_emergency_contacts_isPrimary ON emergency_contacts (isPrimary)");

            // 2. Table: user_profiles
            db.execSQL("CREATE TABLE IF NOT EXISTS user_profiles ("
                    + "userId TEXT NOT NULL PRIMARY KEY, "
                    + "fullName TEXT NOT NULL, "
                    + "email TEXT NOT NULL, "
                    + "phoneNumber TEXT, "
                    + "countryCode TEXT, "
                    + "countryRegion TEXT, "
                    + "profilePhotoUrl TEXT, "
                    + "preferredLanguage TEXT, "
                    + "medicalNotes TEXT, "
                    + "isProfileCompleted INTEGER NOT NULL DEFAULT 0, "
                    + "role TEXT NOT NULL DEFAULT 'CITIZEN', "
                    + "updatedAt INTEGER NOT NULL)");
        }
    };

    public static synchronized BlackoutDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                    context.getApplicationContext(),
                    BlackoutDatabase.class,
                    DATABASE_NAME
            )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build();
        }
        return instance;
    }
}
