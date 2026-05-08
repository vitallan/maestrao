package com.allanvital.maestrao.service;

import com.allanvital.maestrao.TestConfig;
import com.allanvital.maestrao.model.Credential;
import com.allanvital.maestrao.model.CredentialType;
import com.allanvital.maestrao.model.DecryptedCredential;
import com.allanvital.maestrao.repository.CredentialRepository;
import com.allanvital.maestrao.repository.HostRepository;
import com.allanvital.maestrao.security.CredentialCryptoService;
import com.allanvital.maestrao.service.ssh.SshClient;
import com.allanvital.maestrao.service.ssh.SshConnectionService;
import com.allanvital.maestrao.ssh.FakeSshClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public class SshConnectionServiceTest {

    private final CredentialCryptoService credentialCryptoService = mock(CredentialCryptoService.class);
    private final SshClient sshClient = mock(SshClient.class);

    private final String encryptedPassword = "encrypted";
    private final String decryptedPassword = "decrypted";

    private final String IP = "192.168.68.128";

    private final SshConnectionService sshConnectionService = new SshConnectionService(
            credentialCryptoService,
            sshClient
    );

    @BeforeEach
    public void setup() {
        when(credentialCryptoService.decrypt(encryptedPassword)).thenReturn(decryptedPassword);
        when(credentialCryptoService.encrypt(decryptedPassword)).thenReturn(encryptedPassword);
    }

    @Test
    public void shouldCallSshClientWithDecryptedPasswordCredential() {
        when(sshClient.test(eq(IP), eq(22), any(DecryptedCredential.class)))
                .thenReturn(HostConnectionTestResult.success("SSH connection successful"));

        Credential credential = credential(10L, encryptedPassword);
        HostConnectionTestResult result = sshConnectionService.test(IP, 22, credential);
        assertTrue(result.success());
        assertEquals("SSH connection successful", result.message());

        verify(credentialCryptoService, times(1)).decrypt(encryptedPassword);
        verify(sshClient, times(1)).test(IP, 22, decryptedCredential(credential, decryptedPassword));
        verifyNoMoreInteractions(credentialCryptoService, sshClient);
    }

    @Test
    public void shouldReturnSshClientFailureResult() {
        when(sshClient.test(eq(IP), eq(22), any(DecryptedCredential.class)))
                .thenReturn(HostConnectionTestResult.failure("SSH connection failed: timeout"));

        Credential credential = credential(10L, encryptedPassword);
        HostConnectionTestResult result = sshConnectionService.test(IP, 22, credential);
        assertFalse(result.success());
        assertEquals("SSH connection failed: timeout", result.message());

        verify(credentialCryptoService, times(1)).decrypt(encryptedPassword);
        verify(sshClient, times(1)).test(IP, 22, decryptedCredential(credential, decryptedPassword));
        verifyNoMoreInteractions(credentialCryptoService, sshClient);
    }

    private Credential credential(Long id, String encryptedSecret) {
        Credential credential = new Credential();
        credential.setId(id);
        credential.setName("root");
        credential.setType(CredentialType.PASSWORD);
        credential.setUsername("root");
        credential.setEncryptedSecret(encryptedSecret);
        credential.setDescription("cool description");
        return credential;
    }

    private DecryptedCredential decryptedCredential(Credential credential, String decryptedPassword) {
        return new DecryptedCredential(credential, decryptedPassword);
    }

}
