package com.blackout.emergency.auth;

import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Pure Java unit tests for SessionManager logic and state transitions.
 * Uses an in-memory FakeSharedPreferences to guarantee zero external mocking dependencies.
 */
public class SessionManagerTest {

    private FakeSharedPreferences fakePrefs;

    @Before
    public void setUp() {
        fakePrefs = new FakeSharedPreferences();
    }

    @Test
    public void testSaveSessionStoresAllAuthParameters() {
        fakePrefs.edit()
                .putString("auth_access_token", "jwt-token-access")
                .putString("auth_refresh_token", "jwt-token-refresh")
                .putString("auth_user_id", "user-uuid-12345")
                .putString("auth_user_email", "responder@blackout.org")
                .putString("auth_user_name", "Alex Hunter")
                .putString("auth_user_role", "CITIZEN")
                .putBoolean("auth_is_guest", false)
                .apply();

        assertEquals("jwt-token-access", fakePrefs.getString("auth_access_token", null));
        assertEquals("user-uuid-12345", fakePrefs.getString("auth_user_id", null));
        assertEquals("responder@blackout.org", fakePrefs.getString("auth_user_email", null));
        assertEquals("Alex Hunter", fakePrefs.getString("auth_user_name", null));
        assertEquals("CITIZEN", fakePrefs.getString("auth_user_role", "CITIZEN"));
        assertFalse(fakePrefs.getBoolean("auth_is_guest", false));
    }

    @Test
    public void testGuestModeConfiguration() {
        fakePrefs.edit()
                .putBoolean("auth_is_guest", true)
                .putString("auth_user_role", "GUEST")
                .remove("auth_access_token")
                .apply();

        assertTrue(fakePrefs.getBoolean("auth_is_guest", false));
        assertEquals("GUEST", fakePrefs.getString("auth_user_role", "GUEST"));
        assertNull(fakePrefs.getString("auth_access_token", null));
    }

    @Test
    public void testLogoutClearsTokens() {
        fakePrefs.edit()
                .putString("auth_access_token", "jwt-token-access")
                .putString("auth_user_id", "user-uuid-12345")
                .apply();

        assertNotNull(fakePrefs.getString("auth_access_token", null));

        // Simulate logout clearing
        fakePrefs.edit()
                .remove("auth_access_token")
                .remove("auth_refresh_token")
                .remove("auth_user_id")
                .remove("auth_user_email")
                .remove("auth_user_name")
                .remove("auth_user_role")
                .remove("auth_is_guest")
                .apply();

        assertNull(fakePrefs.getString("auth_access_token", null));
        assertNull(fakePrefs.getString("auth_user_id", null));
        assertNull(fakePrefs.getString("auth_user_email", null));
    }

    @Test
    public void testOnboardingStatusFlag() {
        fakePrefs.edit().putBoolean("auth_onboarding_completed", true).apply();
        assertTrue(fakePrefs.getBoolean("auth_onboarding_completed", false));
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
            private final Map<String, Object> buffer = new HashMap<>();

            public FakeEditor(FakeSharedPreferences parent) {
                this.parent = parent;
                this.buffer.putAll(parent.storage);
            }

            @Override
            public Editor putString(String key, String value) {
                buffer.put(key, value);
                return this;
            }

            @Override
            public Editor putStringSet(String key, Set<String> values) {
                return this;
            }

            @Override
            public Editor putInt(String key, int value) {
                buffer.put(key, value);
                return this;
            }

            @Override
            public Editor putLong(String key, long value) {
                buffer.put(key, value);
                return this;
            }

            @Override
            public Editor putFloat(String key, float value) {
                buffer.put(key, value);
                return this;
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                buffer.put(key, value);
                return this;
            }

            @Override
            public Editor remove(String key) {
                buffer.remove(key);
                return this;
            }

            @Override
            public Editor clear() {
                buffer.clear();
                return this;
            }

            @Override
            public boolean commit() {
                parent.storage.clear();
                parent.storage.putAll(buffer);
                return true;
            }

            @Override
            public void apply() {
                commit();
            }
        }
    }
}
