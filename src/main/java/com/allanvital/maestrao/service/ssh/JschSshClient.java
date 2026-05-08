package com.allanvital.maestrao.service.ssh;

import com.allanvital.maestrao.model.CredentialType;
import com.allanvital.maestrao.model.DecryptedCredential;
import com.allanvital.maestrao.service.HostConnectionTestResult;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Component
public class JschSshClient implements SshClient {

    @Override
    public HostConnectionTestResult test(String ip, Integer sshPort, DecryptedCredential credential) {
        if (credential == null) {
            return HostConnectionTestResult.failure("Credential is required");
        }

        String username = normalize(credential.getUsername());
        if (username == null) {
            return HostConnectionTestResult.failure("Credential username is required for SSH connection test");
        }

        int port = sshPort == null ? 22 : sshPort;

        JSch jsch = new JSch();
        Session session = null;
        String secret = credential.getDecryptedSecret();

        try {
            if (credential.getType() == CredentialType.SECRET_KEY) {
                jsch.addIdentity(
                        "maestrao-credential-" + credential.getId(),
                        secret.getBytes(StandardCharsets.UTF_8),
                        null,
                        null
                );
            }

            session = jsch.getSession(username, ip, port);
            session.setConfig(sshConfig());

            if (credential.getType() == CredentialType.PASSWORD) {
                session.setPassword(secret);
            }

            session.connect(CONNECT_TIMEOUT_MILLIS);
            return HostConnectionTestResult.success("SSH connection successful");
        } catch (JSchException e) {
            return HostConnectionTestResult.failure("SSH connection failed: " + e.getMessage());
        } catch (RuntimeException e) {
            return HostConnectionTestResult.failure("SSH connection test failed: " + e.getMessage());
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    @Override
    public SshExecHandle exec(String ip, Integer sshPort, DecryptedCredential credential, String command) {
        if (credential == null) {
            throw new IllegalArgumentException("Credential is required");
        }
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command is required");
        }

        String username = normalize(credential.getUsername());
        if (username == null) {
            throw new IllegalArgumentException("Credential username is required for SSH exec");
        }

        int port = sshPort == null ? 22 : sshPort;
        String secret = credential.getDecryptedSecret();

        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;

        try {
            if (credential.getType() == CredentialType.SECRET_KEY) {
                jsch.addIdentity(
                        "maestrao-credential-" + credential.getId(),
                        secret.getBytes(StandardCharsets.UTF_8),
                        null,
                        null
                );
            }

            session = jsch.getSession(username, ip, port);
            session.setConfig(sshConfig());

            if (credential.getType() == CredentialType.PASSWORD) {
                session.setPassword(secret);
            }

            session.connect(CONNECT_TIMEOUT_MILLIS);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.connect(CONNECT_TIMEOUT_MILLIS);

            final InputStream stdout = channel.getInputStream();
            final InputStream stderr;
            InputStream tmpErr;
            try {
                tmpErr = channel.getExtInputStream();
            } catch (IOException e) {
                tmpErr = InputStream.nullInputStream();
            }
            stderr = tmpErr;

            Session finalSession = session;
            ChannelExec finalChannel = channel;
            return new SshExecHandle() {
                @Override
                public InputStream stdout() {
                    return stdout;
                }

                @Override
                public InputStream stderr() {
                    return stderr;
                }

                @Override
                public void close() {
                    try {
                        if (finalChannel.isConnected()) {
                            finalChannel.disconnect();
                        }
                    } catch (RuntimeException ignored) {
                    }
                    try {
                        if (finalSession.isConnected()) {
                            finalSession.disconnect();
                        }
                    } catch (RuntimeException ignored) {
                    }
                }
            };
        } catch (JSchException | IOException e) {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            throw new IllegalStateException("SSH exec failed: " + e.getMessage(), e);
        }
    }

    @Override
    public SshExecResult execBlocking(String ip,
                                      Integer sshPort,
                                      DecryptedCredential credential,
                                      String command,
                                      byte[] stdin,
                                      long timeoutMillis,
                                      int maxStdoutBytes,
                                      int maxStderrBytes) {
        if (credential == null) {
            throw new IllegalArgumentException("Credential is required");
        }
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command is required");
        }

        String username = normalize(credential.getUsername());
        if (username == null) {
            throw new IllegalArgumentException("Credential username is required for SSH exec");
        }

        int port = sshPort == null ? 22 : sshPort;
        String secret = credential.getDecryptedSecret();

        long safeTimeoutMillis = timeoutMillis <= 0 ? 60_000 : timeoutMillis;
        int safeMaxStdoutBytes = Math.max(0, maxStdoutBytes);
        int safeMaxStderrBytes = Math.max(0, maxStderrBytes);

        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;

        try {
            if (credential.getType() == CredentialType.SECRET_KEY) {
                jsch.addIdentity(
                        "maestrao-credential-" + credential.getId(),
                        secret.getBytes(StandardCharsets.UTF_8),
                        null,
                        null
                );
            }

            session = jsch.getSession(username, ip, port);
            session.setConfig(sshConfig());

            if (credential.getType() == CredentialType.PASSWORD) {
                session.setPassword(secret);
            }

            session.connect(CONNECT_TIMEOUT_MILLIS);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(stdin == null ? null : new ByteArrayInputStream(stdin));
            channel.connect(CONNECT_TIMEOUT_MILLIS);

            InputStream stdout = channel.getInputStream();
            InputStream stderr;
            try {
                stderr = channel.getExtInputStream();
            } catch (IOException e) {
                stderr = InputStream.nullInputStream();
            }

            ByteArrayOutputStream stdoutBuf = new ByteArrayOutputStream(Math.min(8192, safeMaxStdoutBytes));
            ByteArrayOutputStream stderrBuf = new ByteArrayOutputStream(Math.min(8192, safeMaxStderrBytes));

            boolean truncatedOut = false;
            boolean truncatedErr = false;
            long deadline = System.currentTimeMillis() + safeTimeoutMillis;

            byte[] tmp = new byte[4096];
            while (true) {
                if (System.currentTimeMillis() > deadline) {
                    try {
                        channel.disconnect();
                    } catch (RuntimeException ignored) {
                    }
                    try {
                        session.disconnect();
                    } catch (RuntimeException ignored) {
                    }
                    return new SshExecResult(null,
                            stdoutBuf.toString(StandardCharsets.UTF_8),
                            stderrBuf.toString(StandardCharsets.UTF_8),
                            truncatedOut,
                            truncatedErr,
                            true);
                }

                truncatedOut |= drainAvailable(stdout, stdoutBuf, safeMaxStdoutBytes, tmp);
                truncatedErr |= drainAvailable(stderr, stderrBuf, safeMaxStderrBytes, tmp);

                if (channel.isClosed()) {
                    // Drain whatever is still buffered after close.
                    truncatedOut |= drainAvailable(stdout, stdoutBuf, safeMaxStdoutBytes, tmp);
                    truncatedErr |= drainAvailable(stderr, stderrBuf, safeMaxStderrBytes, tmp);
                    if (safeAvailable(stdout) == 0 && safeAvailable(stderr) == 0) {
                        break;
                    }
                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            Integer exitCode = channel.getExitStatus();
            return new SshExecResult(exitCode,
                    stdoutBuf.toString(StandardCharsets.UTF_8),
                    stderrBuf.toString(StandardCharsets.UTF_8),
                    truncatedOut,
                    truncatedErr,
                    false);
        } catch (JSchException | IOException e) {
            throw new IllegalStateException("SSH exec failed: " + e.getMessage(), e);
        } finally {
            if (channel != null && channel.isConnected()) {
                try {
                    channel.disconnect();
                } catch (RuntimeException ignored) {
                }
            }
            if (session != null && session.isConnected()) {
                try {
                    session.disconnect();
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private int safeAvailable(InputStream in) {
        try {
            return in == null ? 0 : Math.max(0, in.available());
        } catch (IOException e) {
            return 0;
        }
    }

    private boolean drainAvailable(InputStream in, ByteArrayOutputStream out, int maxBytes, byte[] tmp) throws IOException {
        if (in == null || maxBytes <= 0) {
            // Still drain to keep channel flowing, but discard.
            if (in == null) {
                return false;
            }
            while (in.available() > 0) {
                int n = in.read(tmp, 0, Math.min(tmp.length, in.available()));
                if (n <= 0) {
                    break;
                }
            }
            return true;
        }

        boolean truncated = false;
        while (in.available() > 0) {
            int remaining = maxBytes - out.size();
            if (remaining <= 0) {
                // Discard the rest.
                truncated = true;
                int toRead = Math.min(tmp.length, in.available());
                int n = in.read(tmp, 0, toRead);
                if (n <= 0) {
                    break;
                }
                continue;
            }
            int toRead = Math.min(Math.min(tmp.length, in.available()), remaining);
            int n = in.read(tmp, 0, toRead);
            if (n <= 0) {
                break;
            }
            out.write(tmp, 0, n);
        }
        return truncated;
    }

    private Properties sshConfig() {
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        config.put("PreferredAuthentications", "publickey,password");
        return config;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

}
