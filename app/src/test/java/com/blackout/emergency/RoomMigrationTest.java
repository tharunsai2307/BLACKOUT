package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.sqlite.db.SupportSQLiteDatabase;
import com.blackout.emergency.database.BlackoutDatabase;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * Phase 8 Database Reliability & Migration Integrity Tests.
 * Verifies sequential migrations v1 -> v2 -> v3 -> v4 without destructive fallback,
 * ensuring no emergency alerts, relay queue records, or warnings are lost during upgrades.
 */
public class RoomMigrationTest {

    private List<String> executedSqlList;
    private SupportSQLiteDatabase mockDatabase;

    @Before
    public void setUp() {
        executedSqlList = new ArrayList<>();

        // Create dynamic proxy for SupportSQLiteDatabase to record all executed DDL queries
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getName().equals("execSQL") && args != null && args.length > 0) {
                    executedSqlList.add((String) args[0]);
                    return null;
                }
                return null;
            }
        };

        mockDatabase = (SupportSQLiteDatabase) Proxy.newProxyInstance(
                SupportSQLiteDatabase.class.getClassLoader(),
                new Class<?>[]{SupportSQLiteDatabase.class},
                handler
        );
    }

    @Test
    public void testMigrationVersionsSequentialOrder() {
        assertEquals(1, BlackoutDatabase.MIGRATION_1_2.startVersion);
        assertEquals(2, BlackoutDatabase.MIGRATION_1_2.endVersion);

        assertEquals(2, BlackoutDatabase.MIGRATION_2_3.startVersion);
        assertEquals(3, BlackoutDatabase.MIGRATION_2_3.endVersion);

        assertEquals(3, BlackoutDatabase.MIGRATION_3_4.startVersion);
        assertEquals(4, BlackoutDatabase.MIGRATION_3_4.endVersion);

        assertEquals(4, BlackoutDatabase.MIGRATION_4_5.startVersion);
        assertEquals(5, BlackoutDatabase.MIGRATION_4_5.endVersion);
    }

    @Test
    public void testMigration_1_2_CreatesRelayTablesAndIndexes() {
        executedSqlList.clear();
        BlackoutDatabase.MIGRATION_1_2.migrate(mockDatabase);

        assertFalse("Migration 1->2 must execute SQL statements", executedSqlList.isEmpty());

        boolean hasRelayQueueTable = false;
        boolean hasRelayHistoryTable = false;
        int indexCount = 0;

        for (String sql : executedSqlList) {
            String upper = sql.toUpperCase();
            if (upper.contains("CREATE TABLE") && upper.contains("RELAY_QUEUE")) {
                hasRelayQueueTable = true;
                assertTrue(upper.contains("MESSAGEID"));
                assertTrue(upper.contains("RELAYSTATUS"));
                assertTrue(upper.contains("PRIORITY"));
            }
            if (upper.contains("CREATE TABLE") && upper.contains("RELAY_HISTORY")) {
                hasRelayHistoryTable = true;
                assertTrue(upper.contains("TODEVICEID"));
                assertTrue(upper.contains("FROMDEVICEID"));
            }
            if (upper.contains("CREATE INDEX")) {
                indexCount++;
            }
        }

        assertTrue("Must create relay_queue table", hasRelayQueueTable);
        assertTrue("Must create relay_history table", hasRelayHistoryTable);
        assertTrue("Must create performance indexes for relay lookups", indexCount >= 4);
    }

    @Test
    public void testMigration_2_3_CreatesPendingUploadsTableAndIndexes() {
        executedSqlList.clear();
        BlackoutDatabase.MIGRATION_2_3.migrate(mockDatabase);

        assertFalse("Migration 2->3 must execute SQL statements", executedSqlList.isEmpty());

        boolean hasPendingUploadsTable = false;
        int indexCount = 0;

        for (String sql : executedSqlList) {
            String upper = sql.toUpperCase();
            if (upper.contains("CREATE TABLE") && upper.contains("PENDING_UPLOADS")) {
                hasPendingUploadsTable = true;
                assertTrue(upper.contains("UPLOADSTATUS"));
                assertTrue(upper.contains("GATEWAYDEVICEID"));
                assertTrue(upper.contains("ATTEMPTCOUNT"));
                assertTrue(upper.contains("NEXTATTEMPTAT"));
            }
            if (upper.contains("CREATE INDEX")) {
                indexCount++;
            }
        }

        assertTrue("Must create pending_uploads table", hasPendingUploadsTable);
        assertTrue("Must create indexes on uploadStatus and nextAttemptAt", indexCount >= 2);
    }

    @Test
    public void testMigration_3_4_CreatesPublicWarningsTablesAndIndexes() {
        executedSqlList.clear();
        BlackoutDatabase.MIGRATION_3_4.migrate(mockDatabase);

        assertFalse("Migration 3->4 must execute SQL statements", executedSqlList.isEmpty());

        boolean hasPublicWarnings = false;
        boolean hasTrustedSigningKeys = false;
        boolean hasWarningRelayHistory = false;
        boolean hasWarningRevisions = false;
        int indexCount = 0;

        for (String sql : executedSqlList) {
            String upper = sql.toUpperCase();
            if (upper.contains("CREATE TABLE") && upper.contains("PUBLIC_WARNINGS")) {
                hasPublicWarnings = true;
                assertTrue(upper.contains("WARNINGID"));
                assertTrue(upper.contains("SIGNINGKEYID"));
                assertTrue(upper.contains("SIGNATURE"));
                assertTrue(upper.contains("CANONICALPAYLOAD"));
            }
            if (upper.contains("CREATE TABLE") && upper.contains("TRUSTED_SIGNING_KEYS")) {
                hasTrustedSigningKeys = true;
                assertTrue(upper.contains("PUBLICKEYBASE64"));
                assertTrue(upper.contains("KEYSTATUS"));
            }
            if (upper.contains("CREATE TABLE") && upper.contains("WARNING_RELAY_HISTORY")) {
                hasWarningRelayHistory = true;
                assertTrue(upper.contains("PEERDEVICEID"));
            }
            if (upper.contains("CREATE TABLE") && upper.contains("WARNING_REVISIONS")) {
                hasWarningRevisions = true;
                assertTrue(upper.contains("REVISION"));
            }
            if (upper.contains("CREATE INDEX") || upper.contains("CREATE UNIQUE INDEX")) {
                indexCount++;
            }
        }

        assertTrue("Must create public_warnings table", hasPublicWarnings);
        assertTrue("Must create trusted_signing_keys table", hasTrustedSigningKeys);
        assertTrue("Must create warning_relay_history table", hasWarningRelayHistory);
        assertTrue("Must create warning_revisions table", hasWarningRevisions);
        assertTrue("Must create query indexes for active warnings and keys", indexCount >= 6);
    }


    @Test
    public void testMigration_4_5_CreatesProfileAndContactTablesAndIndexes() {
        executedSqlList.clear();
        BlackoutDatabase.MIGRATION_4_5.migrate(mockDatabase);

        assertFalse("Migration 4->5 must execute SQL statements", executedSqlList.isEmpty());

        boolean hasEmergencyContacts = false;
        boolean hasUserProfiles = false;
        int indexCount = 0;

        for (String sql : executedSqlList) {
            String upper = sql.toUpperCase();
            if (upper.contains("CREATE TABLE") && upper.contains("EMERGENCY_CONTACTS")) {
                hasEmergencyContacts = true;
                assertTrue(upper.contains("USERID"));
                assertTrue(upper.contains("CONTACTNAME"));
                assertTrue(upper.contains("PHONENUMBER"));
                assertTrue(upper.contains("ISPRIMARY"));
            }
            if (upper.contains("CREATE TABLE") && upper.contains("USER_PROFILES")) {
                hasUserProfiles = true;
                assertTrue(upper.contains("USERID"));
                assertTrue(upper.contains("EMAIL"));
                assertTrue(upper.contains("FULLNAME"));
                assertTrue(upper.contains("ROLE"));
                assertTrue(upper.contains("MEDICALNOTES"));
            }
            if (upper.contains("CREATE INDEX")) {
                indexCount++;
            }
        }

        assertTrue("Must create emergency_contacts table", hasEmergencyContacts);
        assertTrue("Must create user_profiles table", hasUserProfiles);
        assertTrue("Must create indexes on emergency_contacts", indexCount >= 2);
    }

    @Test
    public void testFullSequentialUpgradeChain_PreservesSchemaIntegrity() {
        executedSqlList.clear();

        // Simulate full migration chain v1 -> v2 -> v3 -> v4
        BlackoutDatabase.MIGRATION_1_2.migrate(mockDatabase);
        BlackoutDatabase.MIGRATION_2_3.migrate(mockDatabase);
        BlackoutDatabase.MIGRATION_3_4.migrate(mockDatabase);
        BlackoutDatabase.MIGRATION_4_5.migrate(mockDatabase);

        List<String> tablesCreated = new ArrayList<>();
        for (String sql : executedSqlList) {
            String upper = sql.toUpperCase();
            if (upper.contains("CREATE TABLE IF NOT EXISTS ")) {
                int start = upper.indexOf("CREATE TABLE IF NOT EXISTS ") + "CREATE TABLE IF NOT EXISTS ".length();
                int end = upper.indexOf(" (", start);
                if (end > start) {
                    tablesCreated.add(upper.substring(start, end).trim());
                }
            }
        }

        assertTrue("Must create relay_queue", tablesCreated.contains("RELAY_QUEUE"));
        assertTrue("Must create relay_history", tablesCreated.contains("RELAY_HISTORY"));
        assertTrue("Must create pending_uploads", tablesCreated.contains("PENDING_UPLOADS"));
        assertTrue("Must create public_warnings", tablesCreated.contains("PUBLIC_WARNINGS"));
        assertTrue("Must create trusted_signing_keys", tablesCreated.contains("TRUSTED_SIGNING_KEYS"));
        assertTrue("Must create warning_relay_history", tablesCreated.contains("WARNING_RELAY_HISTORY"));
        assertTrue("Must create warning_revisions", tablesCreated.contains("WARNING_REVISIONS"));
        assertTrue("Must create emergency_contacts", tablesCreated.contains("EMERGENCY_CONTACTS"));
        assertTrue("Must create user_profiles", tablesCreated.contains("USER_PROFILES"));

        // Verify none of the migrations execute DROP TABLE or destructive statements
        for (String sql : executedSqlList) {
            String upper = sql.toUpperCase();
            assertFalse("Migrations must never drop tables", upper.contains("DROP TABLE"));
            assertFalse("Migrations must never truncate data", upper.contains("TRUNCATE"));
        }
    }
}
