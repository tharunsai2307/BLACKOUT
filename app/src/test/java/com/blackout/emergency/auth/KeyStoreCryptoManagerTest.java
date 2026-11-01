package com.blackout.emergency.auth;

import org.junit.Before;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.*;

/**
 * Unit tests for KeyStoreCryptoManager.
 * Tests AES-256-GCM authenticated encryption, random IV generation,
 * AEAD authentication tag validation, corrupted ciphertext rejection,
 * and key lifecycle.
 */
public class KeyStoreCryptoManagerTest {

    private KeyStoreCryptoManager cryptoManager;
    private TestSecretKeyProvider keyProvider;

    @Before
    public void setUp() throws Exception {
        keyProvider = new TestSecretKeyProvider();
        cryptoManager = new KeyStoreCryptoManager(keyProvider);
    }

    @Test
    public void testEncryptAndDecryptRoundTrip() throws Exception {
        String originalToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ";
        String encrypted = cryptoManager.encrypt(originalToken);

        assertNotNull(encrypted);
        assertNotEquals(originalToken, encrypted);

        String decrypted = cryptoManager.decrypt(encrypted);
        assertEquals(originalToken, decrypted);
    }

    @Test
    public void testRandomIvGeneratesDistinctCiphertexts() throws Exception {
        String token = "super_secret_refresh_token_xyz987";
        String enc1 = cryptoManager.encrypt(token);
        String enc2 = cryptoManager.encrypt(token);

        assertNotEquals("Fresh 12-byte IV must ensure non-deterministic ciphertext for identical plaintexts", enc1, enc2);
        assertEquals(token, cryptoManager.decrypt(enc1));
        assertEquals(token, cryptoManager.decrypt(enc2));
    }

    @Test(expected = KeyStoreCryptoManager.CryptoException.class)
    public void testCorruptedCiphertextFailsAuthenticationTag() throws Exception {
        String token = "secure_access_token_12345";
        String encrypted = cryptoManager.encrypt(token);

        byte[] raw = Base64.getDecoder().decode(encrypted);
        // Tamper with the last byte (part of the GCM authentication tag)
        raw[raw.length - 1] ^= 0xFF;
        String corruptedBase64 = Base64.getEncoder().encodeToString(raw);

        // Decryption must throw CryptoException due to AEAD tag verification failure
        cryptoManager.decrypt(corruptedBase64);
    }

    @Test(expected = KeyStoreCryptoManager.CryptoException.class)
    public void testCorruptedIvFailsDecryption() throws Exception {
        String token = "sensitive_payload";
        String encrypted = cryptoManager.encrypt(token);

        byte[] raw = Base64.getDecoder().decode(encrypted);
        // Tamper with first byte (part of 12-byte IV)
        raw[0] ^= 0xAA;
        String corruptedIv = Base64.getEncoder().encodeToString(raw);

        cryptoManager.decrypt(corruptedIv);
    }

    @Test(expected = KeyStoreCryptoManager.CryptoException.class)
    public void testTruncatedPayloadFailsDecryption() throws Exception {
        // Less than 12 bytes IV + 16 bytes tag
        byte[] truncated = new byte[10];
        String truncatedBase64 = Base64.getEncoder().encodeToString(truncated);
        cryptoManager.decrypt(truncatedBase64);
    }

    @Test
    public void testEmptyPayloadHandling() throws Exception {
        assertEquals("", cryptoManager.encrypt(""));
        assertEquals("", cryptoManager.decrypt(""));
        assertEquals("", cryptoManager.decrypt(null));
    }

    @Test
    public void testKeyDeletionAndRecreation() throws Exception {
        String token = "auth_token_before_key_rotation";
        String enc = cryptoManager.encrypt(token);
        assertEquals(token, cryptoManager.decrypt(enc));

        // Delete key and recreate with different key bytes
        cryptoManager.deleteKey();
        keyProvider.generateNewKey();

        // Previous ciphertext cannot be decrypted with the new key
        try {
            cryptoManager.decrypt(enc);
            fail("Decryption with new key must fail");
        } catch (KeyStoreCryptoManager.CryptoException expected) {
            // Success: AEAD verification failed
        }
    }

    /**
     * In-memory SecretKeyProvider simulating AndroidKeyStore in pure JVM tests.
     */
    private static class TestSecretKeyProvider implements KeyStoreCryptoManager.SecretKeyProvider {
        private SecretKey secretKey;

        TestSecretKeyProvider() throws Exception {
            generateNewKey();
        }

        void generateNewKey() throws Exception {
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
}

