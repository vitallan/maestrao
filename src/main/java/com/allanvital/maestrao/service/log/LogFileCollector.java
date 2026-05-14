package com.allanvital.maestrao.service.log;

import com.allanvital.maestrao.model.*;
import com.allanvital.maestrao.security.CredentialCryptoService;
import com.allanvital.maestrao.service.ssh.SshClient;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Component
public class LogFileCollector implements LogCollectorRunnerFactory {

    private final CredentialCryptoService credentialCryptoService;
    private final SshClient sshClient;
    private final LogIngestionService logIngestionService;

    public LogFileCollector(CredentialCryptoService credentialCryptoService,
                            SshClient sshClient,
                            LogIngestionService logIngestionService) {
        this.credentialCryptoService = credentialCryptoService;
        this.sshClient = sshClient;
        this.logIngestionService = logIngestionService;
    }

    @Override
    public LogType type() {
        return LogType.LOG_FILE;
    }

    @Override
    public LogCollectorManager.LogCollectorRunner create(LogSource source, LogCollectorManager.CollectorControl control) {
        return () -> run(source, control);
    }

    private void run(LogSource source, LogCollectorManager.CollectorControl control) {
        String filePath = normalizeRequired(source.getFilePath(), "file path");

        Host host = source.getHost();
        Credential credential = host.getCredential();
        String secret = credentialCryptoService.decrypt(credential.getEncryptedSecret());
        DecryptedCredential decryptedCredential = new DecryptedCredential(credential, secret);

        String command = "tail -n 0 -F -- '" + escapeSingleQuotes(filePath) + "'";

        try (SshClient.SshExecHandle handle = sshClient.exec(host.getIp(), host.getSshPort(), decryptedCredential, command, null)) {
            control.setResource(handle);

            BufferedReader reader = new BufferedReader(new InputStreamReader(handle.stdout(), StandardCharsets.UTF_8));
            String line;
            while (!control.isStopped() && (line = reader.readLine()) != null) {
                logIngestionService.appendLine(source.getId(), line);
            }
        } catch (Exception e) {
            throw new RuntimeException("Log file collector failed: " + e.getMessage(), e);
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private String escapeSingleQuotes(String value) {
        return value.replace("'", "'\\''");
    }
}
