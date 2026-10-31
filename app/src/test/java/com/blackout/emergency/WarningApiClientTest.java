package com.blackout.emergency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.blackout.emergency.warnings.models.PublicWarning;
import com.blackout.emergency.warnings.models.TrustedSigningKey;
import com.blackout.emergency.warnings.sync.WarningApiClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for WarningApiClient:
 * - Parsing Edge Function /functions/v1/warnings/active responses
 * - Parsing Supabase REST /rest/v1/public_warnings fallback responses
 * - Parsing /functions/v1/warnings/keys key registry responses
 * - Handling camelCase vs snake_case JSON field variants
 * - Mocking HttpURLConnection for end-to-end client responses
 */
public class WarningApiClientTest {

    private WarningApiClient apiClient;

    @Before
    public void setUp() {
        apiClient = new WarningApiClient("https://example.supabase.co", "test-anon-key");
    }

    @Test
    public void testParseWarningsResponseEdgeFunctionFormat() throws Exception {
        String json = "{\n" +
                "  \"warnings\": [\n" +
                "    {\n" +
                "      \"warningId\": \"w-101\",\n" +
                "      \"protocolVersion\": 1,\n" +
                "      \"revision\": 2,\n" +
                "      \"title\": \"Flash Flood Watch\",\n" +
                "      \"description\": \"Water rising in Sector 4.\",\n" +
                "      \"category\": \"FLOOD\",\n" +
                "      \"severity\": \"WARNING\",\n" +
                "      \"issuingAuthority\": \"Civil Defense\",\n" +
                "      \"issuedAt\": 1700000000000,\n" +
                "      \"effectiveAt\": 1700000000000,\n" +
                "      \"expiresAt\": 1800000000000,\n" +
                "      \"affectedRegions\": [\"Sector 4\", \"Basin\"],\n" +
                "      \"latitude\": 13.0827,\n" +
                "      \"longitude\": 80.2707,\n" +
                "      \"radiusMeters\": 5000.0,\n" +
                "      \"language\": \"en\",\n" +
                "      \"status\": \"PUBLISHED\",\n" +
                "      \"signingKeyId\": \"KEY-ROOT-2026\",\n" +
                "      \"signatureAlgorithm\": \"Ed25519\",\n" +
                "      \"signature\": \"sig123\",\n" +
                "      \"canonicalPayload\": \"canonical-content\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        List<PublicWarning> list = apiClient.parseWarningsResponse(json);
        assertNotNull(list);
        assertEquals(1, list.size());

        PublicWarning w = list.get(0);
        assertEquals("w-101", w.getWarningId());
        assertEquals(2, w.getRevision());
        assertEquals("Flash Flood Watch", w.getTitle());
        assertEquals("FLOOD", w.getCategory());
        assertEquals("WARNING", w.getSeverity());
        assertEquals("Civil Defense", w.getIssuingAuthority());
        assertEquals(2, w.getAffectedRegions().size());
        assertTrue(w.getAffectedRegions().contains("Sector 4"));
        assertEquals(13.0827, w.getLatitude(), 0.0001);
        assertEquals(80.2707, w.getLongitude(), 0.0001);
        assertEquals(5000.0, w.getRadiusMeters(), 0.1);
        assertEquals("KEY-ROOT-2026", w.getSigningKeyId());
        assertEquals("sig123", w.getSignature());
    }

    @Test
    public void testParseWarningsResponseSnakeCaseFormat() throws Exception {
        String json = "[\n" +
                "  {\n" +
                "    \"id\": \"w-202\",\n" +
                "    \"protocol_version\": 1,\n" +
                "    \"revision\": 1,\n" +
                "    \"title\": \"Cyclone Warning\",\n" +
                "    \"description\": \"Category 3 cyclone landfall.\",\n" +
                "    \"category\": \"CYCLONE\",\n" +
                "    \"severity\": \"CRITICAL\",\n" +
                "    \"issuing_authority\": \"Meteorological Dept\",\n" +
                "    \"published_at\": \"2026-11-01T00:00:00Z\",\n" +
                "    \"effective_at\": \"2026-11-01T00:00:00Z\",\n" +
                "    \"expires_at\": \"2026-11-02T00:00:00Z\",\n" +
                "    \"affected_regions\": [\"Coastal Corridor\"],\n" +
                "    \"latitude\": 12.98,\n" +
                "    \"longitude\": 80.22,\n" +
                "    \"radius_km\": 15.0,\n" +
                "    \"status\": \"PUBLISHED\",\n" +
                "    \"signing_key_id\": \"KEY-MET-01\",\n" +
                "    \"signature_algorithm\": \"Ed25519\",\n" +
                "    \"signature\": \"sig456\"\n" +
                "  }\n" +
                "]";

        List<PublicWarning> list = apiClient.parseWarningsJsonArray(new org.json.JSONArray(json));
        assertNotNull(list);
        assertEquals(1, list.size());

        PublicWarning w = list.get(0);
        assertEquals("w-202", w.getWarningId());
        assertEquals("Cyclone Warning", w.getTitle());
        assertEquals("CYCLONE", w.getCategory());
        assertEquals("CRITICAL", w.getSeverity());
        assertEquals(15000.0, w.getRadiusMeters(), 0.1); // Converted from radius_km
        assertEquals("KEY-MET-01", w.getSigningKeyId());
        assertTrue(w.getIssuedAt() > 0);
    }

    @Test
    public void testParseKeysResponse() throws Exception {
        String json = "{\n" +
                "  \"keys\": [\n" +
                "    {\n" +
                "      \"keyId\": \"KEY-BLACKOUT-ROOT-2026\",\n" +
                "      \"publicKey\": \"k5EwtLq5Md0tNFqNxdqdm2vL6c/q1ZgHcb6Uku32/Xc=\",\n" +
                "      \"algorithm\": \"Ed25519\",\n" +
                "      \"authorityId\": \"BLACKOUT Civil Defense\",\n" +
                "      \"validFrom\": 1700000000000,\n" +
                "      \"validUntil\": 1900000000000,\n" +
                "      \"keyStatus\": \"ACTIVE\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        List<TrustedSigningKey> keys = apiClient.parseKeysResponse(json);
        assertNotNull(keys);
        assertEquals(1, keys.size());

        TrustedSigningKey k = keys.get(0);
        assertEquals("KEY-BLACKOUT-ROOT-2026", k.getKeyId());
        assertEquals("k5EwtLq5Md0tNFqNxdqdm2vL6c/q1ZgHcb6Uku32/Xc=", k.getPublicKeyBase64());
        assertEquals("Ed25519", k.getAlgorithm());
        assertEquals("ACTIVE", k.getKeyStatus());
        assertEquals(1700000000000L, k.getValidFrom());
        assertEquals(1900000000000L, k.getValidUntil());
        assertTrue(k.isCurrentlyValid());
    }

    @Test
    public void testFetchActiveWarningsHttpConnection() throws Exception {
        final String mockResponseBody = "{\"warnings\":[]}";

        apiClient.setConnectionProvider(new WarningApiClient.HttpConnectionProvider() {
            @Override
            public HttpURLConnection openConnection(URL url) throws IOException {
                return new MockHttpURLConnection(url, 200, mockResponseBody);
            }
        });

        List<PublicWarning> result = apiClient.fetchActiveWarnings();
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    // Helper mock HttpURLConnection for testing network client without external dependencies
    private static class MockHttpURLConnection extends HttpURLConnection {
        private final int responseCode;
        private final String responseBody;

        protected MockHttpURLConnection(URL u, int responseCode, String responseBody) {
            super(u);
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
        public InputStream getInputStream() {
            return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }
    }
}
