package com.allanvital.maestrao.service.ssh;

import com.allanvital.maestrao.model.Credential;
import com.allanvital.maestrao.model.DecryptedCredential;
import com.allanvital.maestrao.service.HostConnectionTestResult;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public interface SshClient {

    int CONNECT_TIMEOUT_MILLIS = 10_000;

    HostConnectionTestResult test(String ip, Integer sshPort, DecryptedCredential credential);

    SshExecHandle exec(String ip, Integer sshPort, DecryptedCredential credential, String command, byte[] stdin);

    SshExecResult execBlocking(String ip,
                              Integer sshPort,
                              DecryptedCredential credential,
                              String command,
                              byte[] stdin,
                              long timeoutMillis,
                              int maxStdoutBytes,
                              int maxStderrBytes);

    record SshExecResult(Integer exitCode,
                         String stdout,
                         String stderr,
                         boolean truncatedStdout,
                         boolean truncatedStderr,
                         boolean timedOut) {
    }

    interface SshExecHandle extends AutoCloseable {
        java.io.InputStream stdout();

        java.io.InputStream stderr();

        boolean isClosed();

        Integer exitStatus();

        @Override
        void close();
    }

}
