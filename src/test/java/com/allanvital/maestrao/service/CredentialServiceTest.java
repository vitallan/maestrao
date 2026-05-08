package com.allanvital.maestrao.service;

import com.allanvital.maestrao.model.Credential;
import com.allanvital.maestrao.model.CredentialType;
import com.allanvital.maestrao.repository.CredentialRepository;
import com.allanvital.maestrao.security.CredentialCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:maestrao-credential-service-test;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "maestrao.credentials.encryption-key=MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI="
})
@Transactional
class CredentialServiceTest {

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private CredentialRepository credentialRepository;

    @Autowired
    private CredentialCryptoService credentialCryptoService;

    @BeforeEach
    void setUp() {
        credentialRepository.deleteAll();
        credentialRepository.flush();
    }

    @Test
    void shouldCreateCredentialWithEncryptedSecret() {
        Credential credential = credentialService.create(
                "  Production SSH  ",
                CredentialType.PASSWORD,
                "  root  ",
                "super-secret-password",
                "  Main production host  "
        );

        assertNotNull(credential.getId());
        assertEquals("Production SSH", credential.getName());
        assertEquals(CredentialType.PASSWORD, credential.getType());
        assertEquals("root", credential.getUsername());
        assertEquals("Main production host", credential.getDescription());
        assertNotNull(credential.getCreatedAt());
        assertNotNull(credential.getUpdatedAt());

        assertNotEquals("super-secret-password", credential.getEncryptedSecret());
        assertFalse(credential.getEncryptedSecret().contains("super-secret-password"));
        assertEquals("super-secret-password", credentialCryptoService.decrypt(credential.getEncryptedSecret()));
    }

    @Test
    void shouldCreateCredentialWithLongEncryptedSecret() {
        String longSecret = """
                -----BEGIN OPENSSH PRIVATE KEY-----
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000000000000000000000
                0000000000000000000000000000000000000000000000000000
                -----END OPENSSH PRIVATE KEY-----""";
        Credential credential = credentialService.create(
                "  Production SSH  ",
                CredentialType.SECRET_KEY,
                "root",
                longSecret,
                "  Main production host  "
        );

        assertNotNull(credential.getId());
        assertEquals("Production SSH", credential.getName());
        assertEquals(CredentialType.SECRET_KEY, credential.getType());
        assertEquals("root", credential.getUsername());
        assertEquals("Main production host", credential.getDescription());
        assertNotNull(credential.getCreatedAt());
        assertNotNull(credential.getUpdatedAt());

        assertNotEquals(longSecret, credential.getEncryptedSecret());
        assertEquals(longSecret, credentialCryptoService.decrypt(credential.getEncryptedSecret()));
    }

    @Test
    void shouldRevealSecret() {
        Credential credential = credentialService.create(
                "GitHub token",
                CredentialType.SECRET_KEY,
                "allan",
                "ghp_fake_secret_key",
                "Token used for tests"
        );

        String revealedSecret = credentialService.revealSecret(credential.getId());

        assertEquals("ghp_fake_secret_key", revealedSecret);
    }

    @Test
    void shouldUpdateCredentialKeepingCurrentSecretWhenPlainSecretIsBlank() {
        Credential credential = credentialService.create(
                "Old name",
                CredentialType.PASSWORD,
                "old-user",
                "original-secret",
                "old description"
        );
        String originalEncryptedSecret = credential.getEncryptedSecret();

        Credential updatedCredential = credentialService.update(
                credential.getId(),
                "New name",
                CredentialType.SECRET_KEY,
                "new-user",
                "   ",
                "new description"
        );

        assertEquals("New name", updatedCredential.getName());
        assertEquals(CredentialType.SECRET_KEY, updatedCredential.getType());
        assertEquals("new-user", updatedCredential.getUsername());
        assertEquals("new description", updatedCredential.getDescription());
        assertEquals(originalEncryptedSecret, updatedCredential.getEncryptedSecret());
        assertEquals("original-secret", credentialService.revealSecret(updatedCredential.getId()));
    }

    @Test
    void shouldUpdateCredentialChangingSecretWhenPlainSecretIsProvided() {
        Credential credential = credentialService.create(
                "SSH",
                CredentialType.PASSWORD,
                "root",
                "old-secret",
                null
        );
        String originalEncryptedSecret = credential.getEncryptedSecret();

        Credential updatedCredential = credentialService.update(
                credential.getId(),
                "SSH",
                CredentialType.PASSWORD,
                "root",
                "new-secret",
                null
        );

        assertNotEquals(originalEncryptedSecret, updatedCredential.getEncryptedSecret());
        assertEquals("new-secret", credentialService.revealSecret(updatedCredential.getId()));
    }

    @Test
    void shouldFindAllWithPagination() {
        credentialService.create("Credential C", CredentialType.PASSWORD, "user-c", "secret-c", null);
        credentialService.create("Credential A", CredentialType.PASSWORD, "user-a", "secret-a", null);
        credentialService.create("Credential B", CredentialType.SECRET_KEY, "user-b", "secret-b", null);

        Page<Credential> firstPage = credentialService.findAll(
                PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "name"))
        );
        Page<Credential> secondPage = credentialService.findAll(
                PageRequest.of(1, 2, Sort.by(Sort.Direction.ASC, "name"))
        );

        assertEquals(3, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());
        assertEquals(List.of("Credential A", "Credential B"), firstPage.getContent().stream().map(Credential::getName).toList());
        assertEquals(List.of("Credential C"), secondPage.getContent().stream().map(Credential::getName).toList());
    }

    @Test
    void shouldCountCredentials() {
        assertEquals(0, credentialService.count());

        credentialService.create("One", CredentialType.PASSWORD, null, "secret-one", null);
        credentialService.create("Two", CredentialType.SECRET_KEY, null, "secret-two", null);

        assertEquals(2, credentialService.count());
    }

    @Test
    void shouldDeleteCredential() {
        Credential credential = credentialService.create(
                "Temporary credential",
                CredentialType.PASSWORD,
                "tmp",
                "tmp-secret",
                null
        );

        credentialService.delete(credential.getId());

        assertEquals(0, credentialService.count());
        assertFalse(credentialRepository.findById(credential.getId()).isPresent());
    }

    @Test
    void shouldRejectCreationWithBlankRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> credentialService.create(
                " ", CredentialType.PASSWORD, "user", "secret", null
        ));

        assertThrows(IllegalArgumentException.class, () -> credentialService.create(
                "Name", CredentialType.PASSWORD, "user", " ", null
        ));
    }

    @Test
    void shouldFailWhenUpdatingMissingCredential() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> credentialService.update(999L, "Name", CredentialType.PASSWORD, "user", "secret", null)
        );

        assertTrue(exception.getMessage().contains("Credential not found"));
    }

    @Test
    void shouldFailWhenRevealingMissingCredential() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> credentialService.revealSecret(999L)
        );

        assertTrue(exception.getMessage().contains("Credential not found"));
    }
}
