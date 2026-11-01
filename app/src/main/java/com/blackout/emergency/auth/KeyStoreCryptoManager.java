package com.blackout.emergency.auth;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Manages AES-256-GCM authenticated encryption for sensitive credentials
 * backed by hardware-isolated Android Keystore.
 *
 * Guarantees confidentiality and integrity:
 * - Cipher: AES/GCM/NoPadding
 * - Key Size: 256 bits
 * - IV: 12-byte cryptographically secure random bytes prepended to ciphertext
 * - Tag: 128-bit authentication tag verified by AEAD
 */
public class KeyStoreCryptoManager {

    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String DEFAULT_KEY_ALIAS = "blackout_aes_auth_key";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int AES_KEY_SIZE_BITS = 256;

    public static class CryptoException extends Exception {
        public CryptoException(String message) {
            super(message);
        }

        public CryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public interface SecretKeyProvider {
        @NonNull
        SecretKey getOrCreateKey() throws GeneralSecurityException, IOException;
        void deleteKey() throws GeneralSecurityException, IOException;
    }

    private final String keyAlias;
    private final SecretKeyProvider keyProvider;
    private final SecureRandom secureRandom;

    private static volatile KeyStoreCryptoManager defaultInstance;

    public static KeyStoreCryptoManager getInstance() {
        if (defaultInstance == null) {
            synchronized (KeyStoreCryptoManager.class) {
                if (defaultInstance == null) {
                    defaultInstance = new KeyStoreCryptoManager(DEFAULT_KEY_ALIAS);
                }
            }
        }
        return defaultInstance;
    }

    public KeyStoreCryptoManager() {
        this(DEFAULT_KEY_ALIAS);
    }

    public KeyStoreCryptoManager(@NonNull String keyAlias) {
        this.keyAlias = keyAlias;
        this.secureRandom = new SecureRandom();
        this.keyProvider = new AndroidKeyStoreProvider(keyAlias);
    }

    @VisibleForTesting
    public KeyStoreCryptoManager(@NonNull SecretKeyProvider keyProvider) {
        this.keyAlias = "test_key";
        this.secureRandom = new SecureRandom();
        this.keyProvider = keyProvider;
    }

    /**
     * Encrypts plaintext using AES-256-GCM.
     * The resulting payload is Base64 encoded as [12-byte IV || Ciphertext + 16-byte Tag].
     *
     * @param plainText The unencrypted sensitive string.
     * @return Base64-encoded authenticated ciphertext.
     * @throws CryptoException if encryption fails or key is inaccessible.
     */
    @NonNull
    public String encrypt(@NonNull String plainText) throws CryptoException {
        if (plainText.isEmpty()) {
            return "";
        }

        try {
            SecretKey secretKey = keyProvider.getOrCreateKey();

            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);
            byte[] cipherBytes = cipher.doFinal(plainBytes);

            // Prepend IV to ciphertext
            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException | IOException e) {
            throw new CryptoException("Failed to encrypt sensitive data with Keystore key: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts Base64-encoded AES-256-GCM payload.
     * Extracts 12-byte IV, verifies 128-bit authentication tag, and returns decrypted plaintext.
     *
     * @param base64Encrypted Base64-encoded [12-byte IV || Ciphertext + Tag].
     * @return Decrypted plaintext string.
     * @throws CryptoException if decryption fails, ciphertext is corrupted, or authentication tag mismatch.
     */
    @NonNull
    public String decrypt(@Nullable String base64Encrypted) throws CryptoException {
        if (base64Encrypted == null || base64Encrypted.trim().isEmpty()) {
            return "";
        }

        try {
            byte[] combined;
            try {
                combined = Base64.getDecoder().decode(base64Encrypted.trim());
            } catch (IllegalArgumentException e) {
                throw new CryptoException("Invalid Base64 format in encrypted payload", e);
            }

            if (combined.length < GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)) {
                throw new CryptoException("Ciphertext payload is truncated or corrupted (insufficient length for GCM IV + Tag)");
            }

            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH_BYTES);
            byte[] cipherBytes = Arrays.copyOfRange(combined, GCM_IV_LENGTH_BYTES, combined.length);

            SecretKey secretKey = keyProvider.getOrCreateKey();
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] decryptedBytes = cipher.doFinal(cipherBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IOException e) {
            throw new CryptoException("Authentication tag verification or decryption failed (corrupted ciphertext or invalid key): " + e.getMessage(), e);
        }
    }

    /**
     * Deletes the key from Keystore.
     */
    public void deleteKey() throws CryptoException {
        try {
            keyProvider.deleteKey();
        } catch (Exception e) {
            throw new CryptoException("Failed to delete Keystore key: " + e.getMessage(), e);
        }
    }

    /**
     * Default AndroidKeyStore provider implementation.
     */
    private static class AndroidKeyStoreProvider implements SecretKeyProvider {
        private final String keyAlias;

        AndroidKeyStoreProvider(String keyAlias) {
            this.keyAlias = keyAlias;
        }

        @NonNull
        @Override
        public SecretKey getOrCreateKey() throws GeneralSecurityException, IOException {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
            keyStore.load(null);

            if (keyStore.containsAlias(keyAlias)) {
                KeyStore.Entry entry = keyStore.getEntry(keyAlias, null);
                if (entry instanceof KeyStore.SecretKeyEntry) {
                    return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
                }
            }

            // Key does not exist; generate a new AES-256 key in AndroidKeyStore
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);

            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(AES_KEY_SIZE_BITS)
                    .setRandomizedEncryptionRequired(true)
                    .build();

            keyGenerator.init(spec);
            return keyGenerator.generateKey();
        }

        @Override
        public void deleteKey() throws GeneralSecurityException, IOException {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
            keyStore.load(null);
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias);
            }
        }
    }
}

