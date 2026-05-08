package com.allanvital.maestrao.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Service
public class CredentialCryptoService {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_SIZE_BYTES = 12;
    private static final int TAG_SIZE_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec secretKeySpec;

    public CredentialCryptoService(@Value("${maestrao.credentials.encryption-key}") String base64Key) {
        byte[] key = Base64.getDecoder().decode(base64Key);

        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalArgumentException("maestrao.credentials.encryption-key must decode to 16, 24 or 32 bytes");
        }

        this.secretKeySpec = new SecretKeySpec(key, "AES");
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            throw new IllegalArgumentException("plainText cannot be blank");
        }

        try {
            byte[] iv = new byte[IV_SIZE_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(TAG_SIZE_BITS, iv));

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt credential", e);
        }
    }

    public String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            throw new IllegalArgumentException("encryptedValue cannot be blank");
        }

        try {
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedValue);
            ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedBytes);

            byte[] iv = new byte[IV_SIZE_BYTES];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(TAG_SIZE_BITS, iv));

            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt credential", e);
        }
    }

}
