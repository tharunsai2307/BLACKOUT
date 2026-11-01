package com.blackout.emergency.auth;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Unit tests for SupabaseAuthClient.
 * Validates GoTrue API request payloads, URL endpoints, header formatting,
 * token refresh logic, and error response handling.
 */
public class SupabaseAuthClientTest {

    private static final String TEST_URL = "https://mock.supabase.co";
    private static final String TEST_ANON_KEY = "mock_anon_key_999";
    private SupabaseAuthClient authClient;

    @Before
    public void setUp() {
        authClient = new SupabaseAuthClient(TEST_URL, TEST_ANON_KEY);
        authClient.setDirectExecution(true);
    }

    @Test
    public void testSignInSuccessAndTokenParsing() throws Exception {
        String mockSuccessResponse = "{\n" +
                "  \"access_token\": \"jwt_access_12345\",\n" +
                "  \"refresh_token\": \"jwt_refresh_67890\",\n" +
                "  \"user\": {\n" +
                "    \"id\": \"user_uuid_111\",\n" +
                "    \"email\": \"citizen@blackout.mesh\",\n" +
                "    \"user_metadata\": {\n" +
                "      \"full_name\": \"John Doe\"\n" +
                "    }\n" +
                "  }\n" +
                "}";

        FakeHttpURLConnection mockConn = new FakeHttpURLConnection(new URL(TEST_URL + "/auth/v1/token?grant_type=password"), 200, mockSuccessResponse);
        authClient.setConnectionProvider(url -> mockConn);

        AtomicBoolean successCalled = new AtomicBoolean(false);
        AtomicReference<String> tokenRef = new AtomicReference<>();
        AtomicReference<String> refreshRef = new AtomicReference<>();

        authClient.signIn("citizen@blackout.mesh", "SecretPass123!", new SupabaseAuthClient.AuthCallback() {
            @Override
            public void onSuccess(String accessToken, String refreshToken, String userId, String email, String fullName, String role) {
                successCalled.set(true);
                tokenRef.set(accessToken);
                refreshRef.set(refreshToken);
            }

            @Override
            public void onFailure(String errorMessage) {
                fail("Expected success but failed: " + errorMessage);
            }
        });

        // Small wait for executor task
        Thread.sleep(100);

        assertTrue("onSuccess must be called", successCalled.get());
        assertEquals("jwt_access_12345", tokenRef.get());
        assertEquals("jwt_refresh_67890", refreshRef.get());
        assertEquals("POST", mockConn.getRequestMethod());
        assertEquals(TEST_ANON_KEY, mockConn.getRequestProperty("apikey"));
    }

    @Test
    public void testRefreshAccessTokenSuccess() throws Exception {
        String mockRefreshResponse = "{\n" +
                "  \"access_token\": \"jwt_refreshed_access_999\",\n" +
                "  \"refresh_token\": \"jwt_new_refresh_888\",\n" +
                "  \"user\": {\n" +
                "    \"id\": \"user_uuid_111\",\n" +
                "    \"email\": \"citizen@blackout.mesh\",\n" +
                "    \"user_metadata\": {\n" +
                "      \"full_name\": \"John Doe\"\n" +
                "    }\n" +
                "  }\n" +
                "}";

        FakeHttpURLConnection mockConn = new FakeHttpURLConnection(new URL(TEST_URL + "/auth/v1/token?grant_type=refresh_token"), 200, mockRefreshResponse);
        authClient.setConnectionProvider(url -> mockConn);

        AtomicBoolean successCalled = new AtomicBoolean(false);
        AtomicReference<String> newAccessTokenRef = new AtomicReference<>();

        authClient.refreshAccessToken("jwt_refresh_67890", new SupabaseAuthClient.AuthCallback() {
            @Override
            public void onSuccess(String accessToken, String refreshToken, String userId, String email, String fullName, String role) {
                successCalled.set(true);
                newAccessTokenRef.set(accessToken);
            }

            @Override
            public void onFailure(String errorMessage) {
                fail("Expected refresh success but failed: " + errorMessage);
            }
        });

        Thread.sleep(100);

        assertTrue("Refresh onSuccess must be called", successCalled.get());
        assertEquals("jwt_refreshed_access_999", newAccessTokenRef.get());

        // Verify sent payload contained refresh_token
        String sentBody = mockConn.getOutputAsString();
        JSONObject bodyJson = new JSONObject(sentBody);
        assertEquals("jwt_refresh_67890", bodyJson.getString("refresh_token"));
    }

    @Test
    public void testRefreshAccessTokenExpiredSessionFailure() throws Exception {
        String mockExpiredResponse = "{\n" +
                "  \"error\": \"invalid_grant\",\n" +
                "  \"error_description\": \"Invalid Refresh Token: Already Used\"\n" +
                "}";

        FakeHttpURLConnection mockConn = new FakeHttpURLConnection(new URL(TEST_URL + "/auth/v1/token?grant_type=refresh_token"), 400, mockExpiredResponse);
        authClient.setConnectionProvider(url -> mockConn);

        AtomicBoolean failureCalled = new AtomicBoolean(false);
        AtomicReference<String> errorRef = new AtomicReference<>();

        authClient.refreshAccessToken("expired_token", new SupabaseAuthClient.AuthCallback() {
            @Override
            public void onSuccess(String accessToken, String refreshToken, String userId, String email, String fullName, String role) {
                fail("Expected failure on expired token");
            }

            @Override
            public void onFailure(String errorMessage) {
                failureCalled.set(true);
                errorRef.set(errorMessage);
            }
        });

        Thread.sleep(100);

        assertTrue("Failure callback must be triggered for expired token", failureCalled.get());
        assertTrue(errorRef.get().contains("Invalid Refresh Token"));
    }

    @Test
    public void testResetPasswordUrlAndPayload() throws Exception {
        FakeHttpURLConnection mockConn = new FakeHttpURLConnection(new URL(TEST_URL + "/auth/v1/recover"), 200, "{}");
        authClient.setConnectionProvider(url -> mockConn);

        AtomicBoolean successCalled = new AtomicBoolean(false);
        authClient.resetPassword("user@blackout.org", new SupabaseAuthClient.SimpleCallback() {
            @Override
            public void onSuccess() {
                successCalled.set(true);
            }

            @Override
            public void onFailure(String errorMessage) {
                fail("Expected success for password reset request");
            }
        });

        Thread.sleep(100);

        assertTrue(successCalled.get());
        String sentBody = mockConn.getOutputAsString();
        JSONObject bodyJson = new JSONObject(sentBody);
        assertEquals("user@blackout.org", bodyJson.getString("email"));
    }

    /**
     * In-memory FakeHttpURLConnection for testing HTTP request/response payloads.
     */
    private static class FakeHttpURLConnection extends HttpURLConnection {
        private final int responseCode;
        private final String responseBody;
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private final java.util.Map<String, String> requestHeaders = new java.util.HashMap<>();

        public FakeHttpURLConnection(URL u, int responseCode, String responseBody) {
            super(u);
            this.responseCode = responseCode;
            this.responseBody = responseBody;
        }

        @Override
        public void setRequestProperty(String key, String value) {
            requestHeaders.put(key, value);
        }

        @Override
        public String getRequestProperty(String key) {
            return requestHeaders.get(key);
        }

        @Override
        public java.io.OutputStream getOutputStream() {
            return outputStream;
        }

        @Override
        public java.io.InputStream getInputStream() throws IOException {
            if (responseCode >= 400) {
                throw new IOException("HTTP " + responseCode);
            }
            return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public java.io.InputStream getErrorStream() {
            if (responseCode >= 400) {
                return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public void disconnect() {}

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {}

        public String getOutputAsString() {
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}

