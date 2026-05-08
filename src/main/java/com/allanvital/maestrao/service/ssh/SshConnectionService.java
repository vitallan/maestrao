package com.allanvital.maestrao.service.ssh;

import com.allanvital.maestrao.model.Credential;
import com.allanvital.maestrao.model.DecryptedCredential;
import com.allanvital.maestrao.security.CredentialCryptoService;
import com.allanvital.maestrao.service.HostConnectionTestResult;
import org.springframework.stereotype.Service;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Service
public class SshConnectionService {

    private final CredentialCryptoService credentialCryptoService;
    private final SshClient sshClient;

    public SshConnectionService(CredentialCryptoService credentialCryptoService, SshClient sshClient) {
        this.credentialCryptoService = credentialCryptoService;
        this.sshClient = sshClient;
    }

    public HostConnectionTestResult test(String ip, Integer sshPort, Credential credential) {
        String secret = credentialCryptoService.decrypt(credential.getEncryptedSecret());
        return sshClient.test(ip, sshPort, new DecryptedCredential(credential, secret));
    }

}
