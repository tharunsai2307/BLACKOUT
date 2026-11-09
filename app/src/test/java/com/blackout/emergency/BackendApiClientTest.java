package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.blackout.emergency.gateway.BackendApiClient;
import com.blackout.emergency.models.EmergencyMessage;
import com.blackout.emergency.utils.Constants;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for BackendApiClient:
 * - JSON serialization
 * - Client-side schema validation
 * - HTTP request / response parsing (Success, Duplicate, Client Error, Server Error, Network Error)
 * - Health check endpoint handling
 */
public class BackendApiClientTest {

    private BackendApiClient client;

    @Before
    public void setUp() {
        client = new BackendApiClient("https://test-project.supabase.co", "test-anon-key");
    }

    private EmergencyMessage createValidMessage() {
        EmergencyMessage msg = new EmergencyMessage(
                "DEV-PHONE-A",
                "NODE-7F84",
                Constants.CATEGORY_MEDICAL,
                "Patient collapsed, unconscious. Immediate medical assistance required.",
                Constants.PRIORITY_CRITICAL,
                37.7749,
                -122.4194,
                Constants.STATUS_LOCAL_SAVED
        );
        msg.setMessageId(UUID.randomUUID().toString());
        msg.setHopCount(2);
        return msg;
    }

    @Test
    public void testBuildJsonPayloadContainsAllRequiredFields() throws Exception {
        EmergencyMessage msg = createValidMessage();
        String gatewayId = "GATEWAY-NODE-01";

        JSONObject json = client.buildJsonPayload(msg, gatewayId);

        assertEquals(1, json.getInt("protocolVersion"));
        assertEquals("SOS", json.getString("messageType"));
        assertEquals(msg.getMessageId(), json.getString("messageId"));
        assertEquals("DEV-PHONE-A", json.getString("originalSenderDeviceId"));
        assertEquals(gatewayId, json.getString("gatewayDeviceId"));
        assertEquals(Constants.CATEGORY_MEDICAL, json.getString("category"));
        assertEquals(msg.getDescription(), json.getString("description"));
        assertEquals(Constants.PRIORITY_CRITICAL, json.getString("priority"));
        assertEquals(37.7749, json.getDouble("latitude"), 0.0001);
        assertEquals(-122.4194, json.getDouble("longitude"), 0.0001);
        assertEquals(2, json.getInt("hopCount"));
        assertTrue(json.has("createdAt"));
        assertTrue(json.has("expiresAt"));
    }

    @Test
    public void testValidateMessageSuccess() {
        EmergencyMessage msg = createValidMessage();
        String error = client.validateMessage(msg);
        assertNull("Valid message should have no validation error", error);
    }

    @Test
    public void testValidateMessageInvalidUuid() {
        EmergencyMessage msg = createValidMessage();
        msg.setMessageId("invalid-uuid-1234");
        String error = client.validateMessage(msg);
        assertNotNull(error);
        assertTrue(error.contains("not a valid UUID"));
    }

    @Test
    public void testValidateMessageInvalidCategory() {
        EmergencyMessage msg = createValidMessage();
        msg.setEmergencyCategory("InvalidCategory");
        String error = client.validateMessage(msg);
        assertNotNull(error);
        assertTrue(error.contains("Invalid emergency category"));
    }

    @Test
    public void testValidateMessageEmptyDescription() {
        EmergencyMessage msg = createValidMessage();
        msg.setDescription("   ");
        String error = client.validateMessage(msg);
        assertNotNull(error);
        assertTrue(error.contains("Description cannot be empty"));
    }

    @Test
    public void testValidateMessageExcessiveDescriptionLength() {
        EmergencyMessage msg = createValidMessage();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1005; i++) sb.append('x');
        msg.setDescription(sb.toString());

        String error = client.validateMessage(msg);
        assertNotNull(error);
        assertTrue(error.contains("Description exceeds limit"));
    }

    @Test
    public void testValidateMessageInvalidLatitude() {
        EmergencyMessage msg = createValidMessage();
        msg.setLatitude(95.0);
        String error = client.validateMessage(msg);
        assertNotNull(error);
        assertTrue(error.contains("Latitude out of range"));
    }

    @Test
    public void testValidateMessageInvalidLongitude() {
        EmergencyMessage msg = createValidMessage();
        msg.setLongitude(-190.0);
        String error = client.validateMessage(msg);
        assertNotNull(error);
        assertTrue(error.contains("Longitude out of range"));
    }

    @Test
    public void testValidateMessageExceededHopCount() {
        EmergencyMessage msg = createValidMessage();
        msg.setHopCount(8);
        String error = client.validateMessage(msg);
        assertNotNull(error);
        assertTrue(error.contains("Hop count exceeds allowable limit"));
    }

    @Test
    public void testUploadAlertHttp201Success() {
        final String responseJson = "{\"status\":\"SUCCESS\",\"backendReference\":\"alert-uuid-1234\"}";
        final FakeHttpURLConnection fakeConn = new FakeHttpURLConnection(201, responseJson);

        client.setConnectionProvider(url -> fakeConn);

        EmergencyMessage msg = createValidMessage();
        BackendApiClient.UploadResult result = client.uploadAlert(msg, "GATEWAY-01");

        assertTrue(result.isSuccess());
        assertFalse(result.isDuplicate());
        assertFalse(result.isRetryable());
        assertEquals(201, result.getHttpStatusCode());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("alert-uuid-1234", result.getBackendReference());
    }

    @Test
    public void testUploadAlertHttp200DuplicateAccepted() {
        final String responseJson = "{\"status\":\"DUPLICATE_ACCEPTED\",\"backendReference\":\"existing-ref-5678\"}";
        final FakeHttpURLConnection fakeConn = new FakeHttpURLConnection(200, responseJson);

        client.setConnectionProvider(url -> fakeConn);

        EmergencyMessage msg = createValidMessage();
        BackendApiClient.UploadResult result = client.uploadAlert(msg, "GATEWAY-01");

        assertTrue(result.isSuccess());
        assertTrue(result.isDuplicate());
        assertFalse(result.isRetryable());
        assertEquals(200, result.getHttpStatusCode());
        assertEquals(Constants.UPLOAD_STATUS_DUPLICATE_ACCEPTED, result.getStatus());
        assertEquals("existing-ref-5678", result.getBackendReference());
    }

    @Test
    public void testUploadAlertHttp400Rejection() {
        final String responseJson = "{\"error\":\"INVALID_CATEGORY\",\"message\":\"Invalid emergency category\"}";
        final FakeHttpURLConnection fakeConn = new FakeHttpURLConnection(400, responseJson);

        client.setConnectionProvider(url -> fakeConn);

        EmergencyMessage msg = createValidMessage();
        BackendApiClient.UploadResult result = client.uploadAlert(msg, "GATEWAY-01");

        assertFalse(result.isSuccess());
        assertFalse(result.isRetryable());
        assertEquals(400, result.getHttpStatusCode());
        assertEquals(Constants.UPLOAD_STATUS_REJECTED, result.getStatus());
        assertTrue(result.getErrorMessage().contains("INVALID_CATEGORY"));
    }

    @Test
    public void testUploadAlertHttp409Conflict() {
        final String responseJson = "{\"error\":\"CONFLICTING_PAYLOAD\",\"message\":\"Payload hash mismatch\"}";
        final FakeHttpURLConnection fakeConn = new FakeHttpURLConnection(409, responseJson);

        client.setConnectionProvider(url -> fakeConn);

        EmergencyMessage msg = createValidMessage();
        BackendApiClient.UploadResult result = client.uploadAlert(msg, "GATEWAY-01");

        assertFalse(result.isSuccess());
        assertFalse(result.isRetryable());
        assertEquals(409, result.getHttpStatusCode());
        assertEquals("CONFLICT", result.getStatus());
    }

    @Test
    public void testUploadAlertHttp500ServerErrorIsRetryable() {
        final String responseJson = "{\"error\":\"INTERNAL_SERVER_ERROR\"}";
        final FakeHttpURLConnection fakeConn = new FakeHttpURLConnection(500, responseJson);

        client.setConnectionProvider(url -> fakeConn);

        EmergencyMessage msg = createValidMessage();
        BackendApiClient.UploadResult result = client.uploadAlert(msg, "GATEWAY-01");

        assertFalse(result.isSuccess());
        assertTrue(result.isRetryable());
        assertEquals(500, result.getHttpStatusCode());
    }

    @Test
    public void testUploadAlertNetworkExceptionIsRetryable() {
        client.setConnectionProvider(url -> {
            throw new IOException("Connection refused: no internet");
        });

        EmergencyMessage msg = createValidMessage();
        BackendApiClient.UploadResult result = client.uploadAlert(msg, "GATEWAY-01");

        assertFalse(result.isSuccess());
        assertTrue(result.isRetryable());
        assertEquals(0, result.getHttpStatusCode());
        assertEquals("NETWORK_ERROR", result.getStatus());
        assertTrue(result.getErrorMessage().contains("Connection refused"));
    }

    @Test
    public void testCheckHealthSuccess() {
        final String healthJson = "{\"status\":\"HEALTHY\",\"service\":\"blackout-emergency-gateway\"}";
        final FakeHttpURLConnection fakeConn = new FakeHttpURLConnection(200, healthJson);

        client.setConnectionProvider(url -> fakeConn);

        BackendApiClient.HealthResult health = client.checkHealth();
        assertTrue(health.isHealthy());
        assertEquals(200, health.getHttpStatusCode());
        assertTrue(health.getResponseBody().contains("HEALTHY"));
    }

    @Test
    public void testBuildDirectRestPayloadContainsSnakeCaseFields() throws Exception {
        EmergencyMessage msg = createValidMessage();
        String gatewayId = "GATEWAY-NODE-01";

        JSONObject json = client.buildDirectRestPayload(msg, gatewayId);

        assertEquals(msg.getMessageId(), json.getString("message_id"));
        assertEquals("DEV-PHONE-A", json.getString("original_sender_device_id"));
        assertEquals(gatewayId, json.getString("gateway_device_id"));
        assertEquals(Constants.CATEGORY_MEDICAL, json.getString("category"));
        assertEquals(msg.getDescription(), json.getString("description"));
        assertEquals(Constants.PRIORITY_CRITICAL, json.getString("priority"));
        assertEquals(37.7749, json.getDouble("latitude"), 0.0001);
        assertEquals(-122.4194, json.getDouble("longitude"), 0.0001);
        assertEquals(2, json.getInt("hop_count"));
        assertEquals("ACCEPTED", json.getString("ingestion_status"));
        assertEquals("NEW", json.getString("operator_status"));
        assertTrue(json.has("created_at"));
        assertTrue(json.has("expires_at"));
        assertTrue(json.has("payload_hash"));
    }

    @Test
    public void testUploadAlertViaRpcSuccess() {
        final String rpcSuccessJson = "{\"status\":\"SUCCESS\",\"backendReference\":\"alert-rpc-uuid-101\",\"messageId\":\"msg-uuid-101\",\"ingestionStatus\":\"ACCEPTED\"}";
        client.setConnectionProvider(url -> new FakeHttpURLConnection(200, rpcSuccessJson));

        EmergencyMessage msg = createValidMessage();
        BackendApiClient.UploadResult result = client.uploadAlertViaRpc(msg, "GATEWAY-01");

        assertTrue("Upload via RPC should succeed", result.isSuccess());
        assertFalse(result.isDuplicate());
        assertEquals(200, result.getHttpStatusCode());
        assertEquals("alert-rpc-uuid-101", result.getBackendReference());
    }

    @Test
    public void testUploadAlertViaRpcDuplicateAccepted() {
        final String rpcDuplicateJson = "{\"status\":\"DUPLICATE_ACCEPTED\",\"backendReference\":\"alert-rpc-dup-102\",\"messageId\":\"msg-uuid-102\"}";
        client.setConnectionProvider(url -> new FakeHttpURLConnection(200, rpcDuplicateJson));

        EmergencyMessage msg = createValidMessage();
        BackendApiClient.UploadResult result = client.uploadAlertViaRpc(msg, "GATEWAY-01");

        assertTrue("Duplicate via RPC should be accepted", result.isSuccess());
        assertTrue(result.isDuplicate());
        assertEquals(200, result.getHttpStatusCode());
        assertEquals("alert-rpc-dup-102", result.getBackendReference());
    }

    @Test
    public void testUploadAlertCascadesFromEdgeFunctionToRpc() {
        final String notFoundJson = "{\"code\":\"NOT_FOUND\",\"message\":\"Requested function was not found\"}";
        final String rpcSuccessJson = "{\"status\":\"SUCCESS\",\"backendReference\":\"alert-rpc-uuid-202\"}";

        client.setConnectionProvider(url -> {
            if (url.toString().contains("/functions/v1/")) {
                return new FakeHttpURLConnection(404, notFoundJson);
            } else if (url.toString().contains("/rest/v1/rpc/")) {
                return new FakeHttpURLConnection(200, rpcSuccessJson);
            }
            return new FakeHttpURLConnection(500, "{}");
        });

        EmergencyMessage msg = createValidMessage();
        BackendApiClient.UploadResult result = client.uploadAlert(msg, "GATEWAY-01");

        assertTrue("Upload should cascade from missing Edge Function to RPC", result.isSuccess());
        assertEquals(200, result.getHttpStatusCode());
        assertEquals("alert-rpc-uuid-202", result.getBackendReference());
    }

    @Test
    public void testUploadAlertCascadesFromRpcToDirectRestWhenRpcMissing() {
        final String notFoundJson = "{\"code\":\"NOT_FOUND\",\"message\":\"Requested function was not found\"}";
        final String rpcNotFoundJson = "{\"code\":\"PGRST202\",\"message\":\"Could not find function public.submit_emergency_alert in schema\"}";
        final String restSuccessJson = "";

        client.setConnectionProvider(url -> {
            if (url.toString().contains("/functions/v1/")) {
                return new FakeHttpURLConnection(404, notFoundJson);
            } else if (url.toString().contains("/rest/v1/rpc/")) {
                return new FakeHttpURLConnection(404, rpcNotFoundJson);
            } else if (url.toString().contains("/rest/v1/emergency_alerts")) {
                FakeHttpURLConnection conn = new FakeHttpURLConnection(201, restSuccessJson);
                conn.setHeaderField("Location", "/rest/v1/emergency_alerts?id=eq.alert-direct-uuid-303");
                return conn;
            }
            return new FakeHttpURLConnection(500, "{}");
        });

        EmergencyMessage msg = createValidMessage();
        BackendApiClient.UploadResult result = client.uploadAlert(msg, "GATEWAY-01");

        assertTrue("Upload should cascade to direct PostgREST when RPC is not found", result.isSuccess());
        assertEquals(201, result.getHttpStatusCode());
    }

    @Test
    public void testUploadAlertRlsViolationYieldsSecurityRejectedNonRetryable() {
        final String rlsErrorJson = "{\"code\":\"42501\",\"message\":\"new row violates row-level security policy for table emergency_alerts\"}";
        client.setConnectionProvider(url -> new FakeHttpURLConnection(401, rlsErrorJson));

        EmergencyMessage msg = createValidMessage();
        BackendApiClient.UploadResult result = client.uploadAlertDirectRest(msg, "GATEWAY-01");

        assertFalse("RLS violation should not be considered success", result.isSuccess());
        assertFalse("RLS violation should NOT be retryable to avoid infinite loops", result.isRetryable());
        assertEquals(401, result.getHttpStatusCode());
        assertEquals(Constants.UPLOAD_STATUS_REJECTED, result.getStatus());
        assertTrue(result.getErrorMessage().contains("42501"));
    }

    @Test
    public void testApplyAuthHeadersUsesCitizenJwtWhenAvailable() throws Exception {
        client.setAuthTokenProvider(() -> "citizen-test-jwt-token-xyz");

        URL testUrl = new URL("https://test-project.supabase.co/rest/v1/emergency_alerts");
        FakeHttpURLConnection conn = new FakeHttpURLConnection(200, "{}");
        client.applyAuthHeaders(conn);

        assertEquals("Bearer citizen-test-jwt-token-xyz", conn.getRequestProperty("Authorization"));
        assertEquals("test-anon-key", conn.getRequestProperty("apikey"));
    }

    @Test
    public void testApplyAuthHeadersFallsBackToAnonKeyWhenNoSession() throws Exception {
        client.setAuthTokenProvider(() -> null);

        URL testUrl = new URL("https://test-project.supabase.co/rest/v1/emergency_alerts");
        FakeHttpURLConnection conn = new FakeHttpURLConnection(200, "{}");
        client.applyAuthHeaders(conn);

        assertEquals("Bearer test-anon-key", conn.getRequestProperty("Authorization"));
        assertEquals("test-anon-key", conn.getRequestProperty("apikey"));
    }

    @Test
    public void testUploadAlertDirectRestConflictIsDuplicateAccepted() {
        final String conflictJson = "{\"code\":\"23505\",\"message\":\"duplicate key value violates unique constraint\"}";
        client.setConnectionProvider(url -> new FakeHttpURLConnection(409, conflictJson));

        EmergencyMessage msg = createValidMessage();
        BackendApiClient.UploadResult result = client.uploadAlertDirectRest(msg, "GATEWAY-01");

        assertTrue("Duplicate conflict should be treated as duplicate accepted", result.isSuccess());
        assertTrue(result.isDuplicate());
        assertEquals(409, result.getHttpStatusCode());
        assertEquals(msg.getMessageId(), result.getBackendReference());
    }

    // Helper fake HttpURLConnection for testing
    private static class FakeHttpURLConnection extends HttpURLConnection {
        private final int responseCode;
        private final String responseBody;
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private final java.util.Map<String, String> requestProperties = new java.util.HashMap<>();
        private final java.util.Map<String, String> headerFields = new java.util.HashMap<>();

        FakeHttpURLConnection(int responseCode, String responseBody) {
            super(null);
            this.responseCode = responseCode;
            this.responseBody = responseBody;
        }

        @Override
        public void setRequestProperty(String key, String value) {
            requestProperties.put(key, value);
        }

        @Override
        public String getRequestProperty(String key) {
            return requestProperties.get(key);
        }

        public void setHeaderField(String key, String value) {
            headerFields.put(key, value);
        }

        @Override
        public String getHeaderField(String key) {
            return headerFields.get(key);
        }

        @Override
        public void disconnect() {}

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {}

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public OutputStream getOutputStream() {
            return outputStream;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
        }
    }
}
