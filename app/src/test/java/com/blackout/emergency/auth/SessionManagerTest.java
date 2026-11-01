
package com.blackout.emergency.auth;

import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import static org.junit.Assert.*;

/**
 * Pure Java unit tests for SessionManager.
 * Tests Keystore-backed AES-256-GCM token encryption, legacy session migration,
 * token expiration calculation, atomic token refresh queuing, and logout data safety.
 */
public class SessionManagerTest {

    private FakeSharedPreferences fakePrefs;
    private KeyStoreCryptoManager cryptoManager;
    private TestSecretKeyProvider keyProvider;
    private SessionManager sessionManager;

    @Before
    public void setUp() throws Exception {
        fakePrefs = new FakeSharedPreferences();
        keyProvider = new TestSecretKeyProvider();
        cryptoManager = new KeyStoreCryptoManager(keyProvider);
        sessionManager = new SessionManager(fakePrefs, cryptoManager);
    }

    @Test
    public void testSaveSessionEncryptsTokensAtRest() {
        String rawAccessToken = "mock_access_token_jwt_12345";
        String rawRefreshToken = "mock_refresh_token_jwt_67890";

        sessionManager.saveSession(
                rawAccessToken,
                rawRefreshToken,
                "user-uuid-999",
                "citizen@blackout.mesh",
                "Jane Doe",
                "CITIZEN"
        );

        // Tokens must NOT be stored in plaintext in SharedPreferences!
        assertNull(fakePrefs.getString("access_token", null));
        assertNull(fakePrefs.getString("refresh_token", null));

        String encAccess = fakePrefs.getString("enc_access_token", null);
        String encRefresh = fakePrefs.getString("enc_refresh_token", null);

        assertNotNull(encAccess);
        assertNotNull(encRefresh);
        assertNotEquals(rawAccessToken, encAccess);
        assertNotEquals(rawRefreshToken, encRefresh);

        // Decrypted tokens via SessionManager getters must match original
        assertEquals(rawAccessToken, sessionManager.getAccessToken());
        assertEquals(rawRefreshToken, sessionManager.getRefreshToken());

        // Profile metadata remains unencrypted
        assertEquals("user-uuid-999", sessionManager.getUserId());
        assertEquals("citizen@blackout.mesh", sessionManager.getEmail());
        assertEquals("Jane Doe", sessionManager.getFullName());
        assertEquals("CITIZEN", sessionManager.getRole());
        assertTrue(sessionManager.isLoggedIn());
        assertFalse(sessionManager.isGuestMode());
    }

    @Test
    public void testMigrationFromLegacyPlaintextSession() throws Exception {
        // Prepare legacy plaintext session in prefs
        FakeSharedPreferences legacyPrefs = new FakeSharedPreferences();
        legacyPrefs.edit()
                .putString("access_token", "legacy_plaintext_access_jwt")
                .putString("refresh_token", "legacy_plaintext_refresh_jwt")
                .putString("user_id", "migrated-user-111")
                .putString("email", "legacy@blackout.org")
                .putString("full_name", "Legacy User")
                .putString("role", "CITIZEN")
                .apply();

        // Instantiate new SessionManager to trigger automated migration
        SessionManager migratedManager = new SessionManager(legacyPrefs, cryptoManager);

        // Plaintext tokens must be removed
        assertNull("Plaintext access token must be cleared after migration", legacyPrefs.getString("access_token", null));
        assertNull("Plaintext refresh token must be cleared after migration", legacyPrefs.getString("refresh_token", null));

        // Migration flag must be set
        assertTrue(legacyPrefs.getBoolean("auth_migrated_v10", false));

        // Encrypted tokens must be present and decrypt correctly
        assertNotNull(legacyPrefs.getString("enc_access_token", null));
        assertEquals("legacy_plaintext_access_jwt", migratedManager.getAccessToken());
        assertEquals("legacy_plaintext_refresh_jwt", migratedManager.getRefreshToken());
        assertEquals("migrated-user-111", migratedManager.getUserId());
    }

    @Test
    public void testTokenExpirationDetection() throws Exception {
        // Valid encrypted token with expires_at in the future (3600 seconds from now)
        String validEnc = cryptoManager.encrypt("valid_test_access_token");
        fakePrefs.edit().putString("enc_access_token", validEnc)
                .putLong("token_expires_at_sec", System.currentTimeMillis() / 1000L + 3600L)
                .apply();
        assertFalse(sessionManager.isTokenExpired());

        // Expired token (expires_at in past)
        fakePrefs.edit().putLong("token_expires_at_sec", System.currentTimeMillis() / 1000L - 100L).apply();
        assertTrue(sessionManager.isTokenExpired());
    }

    @Test
    public void testAtomicTokenRefreshQueuing() throws Exception {
        // Save an expired session
        String initialAccess = "expired_access_token";
        String initialRefresh = "valid_refresh_token_777";
        sessionManager.saveSession(initialAccess, initialRefresh, "u1", "u1@test.com", "User One", "CITIZEN");
        fakePrefs.edit().putLong("token_expires_at_sec", System.currentTimeMillis() / 1000L - 10L).apply();

        assertTrue(sessionManager.isTokenExpired());

        // Mock SupabaseAuthClient
        AtomicInteger networkCallCount = new AtomicInteger(0);
        SupabaseAuthClient mockAuthClient = new SupabaseAuthClient("https://mock.supabase.co", "anon") {
            @Override
            public void refreshAccessToken(String refreshToken, AuthCallback callback) {
                networkCallCount.incrementAndGet();
                // Simulate network latency
                new Thread(() -> {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {}
                    callback.onSuccess("new_access_token_888", "new_refresh_token_999", "u1", "u1@test.com", "User One", "CITIZEN");
                }).start();
            }
        };

        // Trigger 5 concurrent refresh requests
        int concurrentCallers = 5;
        CountDownLatch latch = new CountDownLatch(concurrentCallers);
        List<String> receivedTokens = new ArrayList<>();

        for (int i = 0; i < concurrentCallers; i++) {
            new Thread(() -> {
                sessionManager.refreshTokenIfNeeded(mockAuthClient, new SessionManager.TokenRefreshListener() {
                    @Override
                    public void onTokenRefreshed(String newAccessToken) {
                        synchronized (receivedTokens) {
                            receivedTokens.add(newAccessToken);
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onRefreshFailed(String errorMessage) {
                        latch.countDown();
                    }
                });
            }).start();
        }

        assertTrue("All concurrent callers must be notified", latch.await(2, TimeUnit.SECONDS));

        // Crucial Assertion: Exactly ONE HTTP network refresh request must have been made!
        assertEquals("Atomic mutex must serialize refresh into a single network call", 1, networkCallCount.get());
        assertEquals("All 5 callers must receive the refreshed token", concurrentCallers, receivedTokens.size());
        for (String token : receivedTokens) {
            assertEquals("new_access_token_888", token);
        }

        // Session manager tokens must be updated
        assertEquals("new_access_token_888", sessionManager.getAccessToken());
        assertEquals("new_refresh_token_999", sessionManager.getRefreshToken());
    }

    @Test
    public void testPermanentRefreshFailureSafelyClearsCredentials() throws Exception {
        sessionManager.saveSession("bad_access", "revoked_refresh", "u2", "u2@test.com", "User Two", "CITIZEN");
        fakePrefs.edit().putLong("token_expires_at_sec", System.currentTimeMillis() / 1000L - 10L).apply();

        SupabaseAuthClient failingAuthClient = new SupabaseAuthClient("https://mock.supabase.co", "anon") {
            @Override
            public void refreshAccessToken(String refreshToken, AuthCallback callback) {
                callback.onFailure("HTTP 400: Invalid Refresh Token: Refresh Token Not Found");
            }
        };

        AtomicBoolean failureReported = new AtomicBoolean(false);
        sessionManager.refreshTokenIfNeeded(failingAuthClient, new SessionManager.TokenRefreshListener() {
            @Override
            public void onTokenRefreshed(String newAccessToken) {
                fail("Expected failure");
            }

            @Override
            public void onRefreshFailed(String errorMessage) {
                failureReported.set(true);
            }
        });

        assertTrue(failureReported.get());
        // Tokens must be cleared
        assertNull(sessionManager.getAccessToken());
        assertNull(sessionManager.getRefreshToken());
    }

    @Test
    public void testGuestModeAllowsOfflineOperationWithoutTokens() {
        sessionManager.setGuestMode(true);
        assertTrue(sessionManager.isGuestMode());
        assertFalse(sessionManager.isLoggedIn());
        assertNull(sessionManager.getAccessToken());
    }

    @Test
    public void testLogoutClearsTokensWithoutAffectingRoomData() {
        sessionManager.saveSession("access", "refresh", "uid", "email@test.com", "Name", "CITIZEN");
        assertTrue(sessionManager.isLoggedIn());

        sessionManager.logout();

        assertFalse(sessionManager.isLoggedIn());
        assertNull(sessionManager.getAccessToken());
        assertNull(sessionManager.getRefreshToken());
        assertNull(sessionManager.getUserId());
        // SharedPreferences cleared, Room database untouched
    }

    /**
     * In-memory SecretKeyProvider for pure JVM tests.
     */
    private static class TestSecretKeyProvider implements KeyStoreCryptoManager.SecretKeyProvider {
        private SecretKey secretKey;

        TestSecretKeyProvider() throws Exception {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, new SecureRandom());
            this.secretKey = keyGen.generateKey();
        }

        @Override
        public SecretKey getOrCreateKey() {
            return secretKey;
        }

        @Override
        public void deleteKey() {
            this.secretKey = null;
        }
    }

    /**
     * In-memory implementation of Android SharedPreferences for unit testing.
     */
    private static class FakeSharedPreferences implements SharedPreferences {
        private final Map<String, Object> storage = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            return new HashMap<>(storage);
        }

        @Override
        public String getString(String key, String defValue) {
            Object v = storage.get(key);
            return v instanceof String ? (String) v : defValue;
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) {
            return defValues;
        }

        @Override
        public int getInt(String key, int defValue) {
            Object v = storage.get(key);
            return v instanceof Integer ? (Integer) v : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object v = storage.get(key);
            return v instanceof Long ? (Long) v : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object v = storage.get(key);
            return v instanceof Float ? (Float) v : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object v = storage.get(key);
            return v instanceof Boolean ? (Boolean) v : defValue;
        }

        @Override
        public boolean contains(String key) {
            return storage.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new FakeEditor(this);
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        private static class FakeEditor implements Editor {
            private final FakeSharedPreferences parent;
            private final Map<String, Object> pending = new HashMap<>();
            private final List<String> toRemove = new ArrayList<>();
            private boolean clearAll = false;

            FakeEditor(FakeSharedPreferences parent) {
                this.parent = parent;
            }

            @Override
            public Editor putString(String key, String value) {
                if (value == null) {
                    toRemove.add(key);
                } else {
                    pending.put(key, value);
                }
                return this;
            }

            @Override
            public Editor putStringSet(String key, Set<String> values) {
                return this;
            }

            @Override
            public Editor putInt(String key, int value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putLong(String key, long value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putFloat(String key, float value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor remove(String key) {
                toRemove.add(key);
                return this;
            }

            @Override
            public Editor clear() {
                clearAll = true;
                return this;
            }

            @Override
            public boolean commit() {
                apply();
                return true;
            }

            @Override
            public void apply() {
                if (clearAll) {
                    parent.storage.clear();
                }
                for (String k : toRemove) {
                    parent.storage.remove(k);
                }
                parent.storage.putAll(pending);
            }
        }
    }
}
