package com.allanvital.maestrao.crypto;

import com.allanvital.maestrao.security.CredentialCryptoService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CredentialCryptoServiceTest {

    private static final String VALID_AES_256_KEY = Base64.getEncoder()
            .encodeToString("12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8));

    private final CredentialCryptoService credentialCryptoService = new CredentialCryptoService(VALID_AES_256_KEY);

    @Test
    void shouldEncryptAndDecryptSecret() {
        String plainSecret = "ssh-password-123";

        String encryptedSecret = credentialCryptoService.encrypt(plainSecret);
        String decryptedSecret = credentialCryptoService.decrypt(encryptedSecret);

        assertNotNull(encryptedSecret);
        assertNotEquals(plainSecret, encryptedSecret);
        assertEquals(plainSecret, decryptedSecret);
    }

    @Test
    void shouldGenerateDifferentEncryptedValuesForSamePlainText() {
        String plainSecret = "same-secret-value";

        String firstEncryptedValue = credentialCryptoService.encrypt(plainSecret);
        String secondEncryptedValue = credentialCryptoService.encrypt(plainSecret);

        assertNotEquals(firstEncryptedValue, secondEncryptedValue);
        assertEquals(plainSecret, credentialCryptoService.decrypt(firstEncryptedValue));
        assertEquals(plainSecret, credentialCryptoService.decrypt(secondEncryptedValue));
    }

    @Test
    void shouldRejectBlankPlainText() {
        assertThrows(IllegalArgumentException.class, () -> credentialCryptoService.encrypt(null));
        assertThrows(IllegalArgumentException.class, () -> credentialCryptoService.encrypt(""));
        assertThrows(IllegalArgumentException.class, () -> credentialCryptoService.encrypt("   "));
    }

    @Test
    void shouldRejectBlankEncryptedValue() {
        assertThrows(IllegalArgumentException.class, () -> credentialCryptoService.decrypt(null));
        assertThrows(IllegalArgumentException.class, () -> credentialCryptoService.decrypt(""));
        assertThrows(IllegalArgumentException.class, () -> credentialCryptoService.decrypt("   "));
    }

    @Test
    void shouldFailWhenEncryptedValueIsTampered() {
        String encryptedSecret = credentialCryptoService.encrypt("very-sensitive-secret");
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedSecret);
        encryptedBytes[encryptedBytes.length - 1] = (byte) (encryptedBytes[encryptedBytes.length - 1] ^ 1);
        String tamperedEncryptedSecret = Base64.getEncoder().encodeToString(encryptedBytes);

        assertThrows(IllegalStateException.class, () -> credentialCryptoService.decrypt(tamperedEncryptedSecret));
    }

    @Test
    void shouldRejectInvalidKeySize() {
        String invalidKey = Base64.getEncoder().encodeToString("short".getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new CredentialCryptoService(invalidKey)
        );

        assertTrue(exception.getMessage().contains("16, 24 or 32 bytes"));
    }
}
