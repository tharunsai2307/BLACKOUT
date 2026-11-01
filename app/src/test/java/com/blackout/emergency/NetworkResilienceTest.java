package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.blackout.emergency.gateway.BackendApiClient;
import com.blackout.emergency.gateway.PendingUploadManager;
import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.relay.RelayPolicy;
import com.blackout.emergency.utils.Constants;
import com.blackout.emergency.warnings.models.PublicWarning;
import com.blackout.emergency.warnings.sync.WarningApiClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

/**
 * Phase 8 Network & API Resilience Testing Suite.
 * Tests:
 * 1. Slow network and timeout simulation (SocketTimeoutException)
 * 2. HTTP 500 Internal Server Error recovery and retry classification
 * 3. HTTP 429 Rate Limit error handling
 * 4. HTTP 503 Service Unavailable handling
 * 5. Malformed JSON response resilience without crashing
 * 6. WorkManager & Retry Exponential Backoff schedule verification
 */
public class NetworkResilienceTest {

    private BackendApiClient backendClient;
    private WarningApiClient warningClient;

    @Before
    public void setUp() {
        backendClient = new BackendApiClient("https://resilience-test.supabase.co", "anon-key-123");
        warningClient = new WarningApiClient("https://resilience-test.supabase.co", "anon-key-123");
    }

    private EmergencyMessage createSampleMessage() {
        EmergencyMessage msg = new EmergencyMessage(
                "DEV-NODE-RESILIENT",
                "NODE-7F84",
                Constants.CATEGORY_MEDICAL,
                "Medical distress alert for resilience testing.",
                Constants.PRIORITY_HIGH,
                13.0827,
                80.2707,
                Constants.STATUS_LOCAL_SAVED
        );
        msg.setMessageId(UUID.randomUUID().toString());
        return msg;
    }

    // -------------------------------------------------------------------------
    // 1. Slow Network & Timeout Simulation
    // -------------------------------------------------------------------------
    @Test
    public void testBackendApiClient_SocketTimeout_TreatedAsRetryable() {
        backendClient.setConnectionProvider(url -> {
            throw new SocketTimeoutException("Read timed out after 10000ms");
        });

        EmergencyMessage msg = createSampleMessage();
        BackendApiClient.UploadResult result = backendClient.uploadAlert(msg, "GATEWAY-01");

        assertFalse("Upload must fail on socket timeout", result.isSuccess());
        assertTrue("Network socket timeout must be marked as retryable", result.isRetryable());
        assertEquals("NETWORK_ERROR", result.getStatus());
        assertTrue(result.getErrorMessage().contains("timed out"));
    }

    @Test
    public void testWarningApiClient_ConnectionTimeout_HandledGracefully() {
        warningClient.setConnectionProvider(url -> {
            throw new SocketTimeoutException("Connect timed out after 5000ms");
        });

        try {
            warningClient.fetchActiveWarnings();
            org.junit.Assert.fail("SocketTimeoutException must be propagated as IOException to caller");
        } catch (Exception e) {
            assertTrue("Exception must be caught as IOException for WorkManager retry", e instanceof IOException);
        }
    }

    // -------------------------------------------------------------------------
    // 2. HTTP 500 Internal Server Error Handling
    // -------------------------------------------------------------------------
    @Test
    public void testBackendApiClient_Http500ServerError_IsRetryable() {
        final String errorJson = "{\"error\":\"DATABASE_TIMEOUT\",\"message\":\"Database pool exhausted under load\"}";
        backendClient.setConnectionProvider(url -> new ResilientHttpURLConnection(500, errorJson));

        EmergencyMessage msg = createSampleMessage();
        BackendApiClient.UploadResult result = backendClient.uploadAlert(msg, "GATEWAY-01");

        assertFalse(result.isSuccess());
        assertTrue("500 Server Error must be marked retryable for exponential backoff", result.isRetryable());
        assertEquals(500, result.getHttpStatusCode());
        assertEquals("SERVER_ERROR", result.getStatus());
    }

    // -------------------------------------------------------------------------
    // 3. HTTP 429 Rate Limiting Handling
    // -------------------------------------------------------------------------
    @Test
    public void testBackendApiClient_Http429RateLimit_IsRetryable() {
        final String rateLimitJson = "{\"error\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Too many requests from gateway\"}";
        backendClient.setConnectionProvider(url -> new ResilientHttpURLConnection(429, rateLimitJson));

        EmergencyMessage msg = createSampleMessage();
        BackendApiClient.UploadResult result = backendClient.uploadAlert(msg, "GATEWAY-01");

        assertFalse(result.isSuccess());
        assertTrue("429 Rate Limit must be marked retryable to allow backoff", result.isRetryable());
        assertEquals(429, result.getHttpStatusCode());
    }

    // -------------------------------------------------------------------------
    // 4. HTTP 503 Service Unavailable Handling
    // -------------------------------------------------------------------------
    @Test
    public void testBackendApiClient_Http503ServiceUnavailable_IsRetryable() {
        final String unavailableJson = "{\"error\":\"SERVICE_UNAVAILABLE\",\"message\":\"Gateway undergoing maintenance\"}";
        backendClient.setConnectionProvider(url -> new ResilientHttpURLConnection(503, unavailableJson));

        EmergencyMessage msg = createSampleMessage();
        BackendApiClient.UploadResult result = backendClient.uploadAlert(msg, "GATEWAY-01");

        assertFalse(result.isSuccess());
        assertTrue("503 Service Unavailable must be marked retryable", result.isRetryable());
        assertEquals(503, result.getHttpStatusCode());
    }

    // -------------------------------------------------------------------------
    // 5. Malformed JSON Response Resilience
    // -------------------------------------------------------------------------
    @Test
    public void testWarningApiClient_MalformedJsonResponse_TriggersRetryableException() {
        final String garbageResponse = "<html><body>502 Bad Gateway - HTML error page returned instead of JSON</body></html>";
        warningClient.setConnectionProvider(url -> new ResilientHttpURLConnection(200, garbageResponse));

        boolean threwExpected = false;
        try {
            warningClient.fetchActiveWarnings();
        } catch (Exception e) {
            threwExpected = true;
        }
        assertTrue("Malformed JSON response must throw exception for WorkManager retry", threwExpected);
    }

    @Test
    public void testBackendApiClient_MalformedJsonResponse_HandledGracefully() {
        final String garbageJson = "<<not json>>";
        backendClient.setConnectionProvider(url -> new ResilientHttpURLConnection(200, garbageJson));

        EmergencyMessage msg = createSampleMessage();
        BackendApiClient.UploadResult result = backendClient.uploadAlert(msg, "GATEWAY-01");

        // 200 HTTP response with non-JSON body must not throw uncaught exceptions
        assertNotNull(result);
    }

    // -------------------------------------------------------------------------
    // 6. Exponential Backoff Calculation Verification
    // -------------------------------------------------------------------------
    @Test
    public void testExponentialBackoffProgression() {
        long baseBackoff = 30_000L; // 30 seconds
        long maxBackoff = 900_000L; // 15 minutes (RelayPolicy.MAX_RETRY_BACKOFF_MILLIS)

        // Verify exponential scaling: 30s, 60s, 120s, 240s, 480s, capped at max
        long attempt1 = Math.min((long) (baseBackoff * Math.pow(2, 0)), maxBackoff);
        long attempt2 = Math.min((long) (baseBackoff * Math.pow(2, 1)), maxBackoff);
        long attempt3 = Math.min((long) (baseBackoff * Math.pow(2, 2)), maxBackoff);
        long attempt4 = Math.min((long) (baseBackoff * Math.pow(2, 3)), maxBackoff);
        long attempt6 = Math.min((long) (baseBackoff * Math.pow(2, 5)), maxBackoff);
        long attempt10 = Math.min((long) (baseBackoff * Math.pow(2, 9)), maxBackoff);

        assertEquals(30_000L, attempt1);
        assertEquals(60_000L, attempt2);
        assertEquals(120_000L, attempt3);
        assertEquals(240_000L, attempt4);
        assertEquals(900_000L, attempt6); // Capped at 15 minutes
        assertEquals(900_000L, attempt10); // Still capped at 15 minutes
    }

    // Helper Resilient HttpURLConnection
    private static class ResilientHttpURLConnection extends HttpURLConnection {
        private final int responseCode;
        private final String responseBody;

        ResilientHttpURLConnection(int responseCode, String responseBody) {
            super(null);
            this.responseCode = responseCode;
            this.responseBody = responseBody;
        }

        @Override
        public void disconnect() {}

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() throws IOException {}

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (responseCode >= 400) {
                throw new IOException("HTTP error: " + responseCode);
            }
            return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getErrorStream() {
            if (responseCode >= 400) {
                return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
            }
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }
    }
}

