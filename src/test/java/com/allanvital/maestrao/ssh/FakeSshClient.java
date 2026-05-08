package com.allanvital.maestrao.ssh;

import com.allanvital.maestrao.model.DecryptedCredential;
import com.allanvital.maestrao.service.HostConnectionTestResult;
import com.allanvital.maestrao.service.ssh.SshClient;

import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public class FakeSshClient implements SshClient {

    private HostConnectionTestResult nextResult = HostConnectionTestResult.success("OK");
    private String lastIp;
    private Integer lastSshPort;
    private DecryptedCredential lastCredential;
    private String lastCommand;
    private byte[] lastStdin;

    private final Queue<FakeExecHandle> nextExecHandles = new ArrayDeque<>();
    private final Queue<FakeExecResult> nextExecResults = new ArrayDeque<>();

    @Override
    public HostConnectionTestResult test(String ip, Integer sshPort, DecryptedCredential credential) {
        this.lastIp = ip;
        this.lastSshPort = sshPort;
        this.lastCredential = credential;
        return nextResult;
    }

    @Override
    public SshExecHandle exec(String ip, Integer sshPort, DecryptedCredential credential, String command) {
        this.lastIp = ip;
        this.lastSshPort = sshPort;
        this.lastCredential = credential;
        this.lastCommand = command;
        this.lastStdin = null;

        FakeExecHandle handle = nextExecHandles.poll();
        if (handle == null) {
            throw new IllegalStateException("No FakeExecHandle enqueued");
        }
        return handle;
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
        this.lastIp = ip;
        this.lastSshPort = sshPort;
        this.lastCredential = credential;
        this.lastCommand = command;
        this.lastStdin = stdin;

        FakeExecResult result = nextExecResults.poll();
        if (result == null) {
            throw new IllegalStateException("No FakeExecResult enqueued");
        }
        return result.toResult();
    }

    public void reset() {
        this.nextResult = HostConnectionTestResult.success("OK");
        this.lastIp = null;
        this.lastSshPort = null;
        this.lastCredential = null;
        this.lastCommand = null;
        this.lastStdin = null;
        this.nextExecHandles.clear();
        this.nextExecResults.clear();
    }

    public FakeExecHandle enqueueExecHandle() {
        FakeExecHandle handle = new FakeExecHandle();
        nextExecHandles.add(handle);
        return handle;
    }

    public FakeExecResult enqueueExecResult() {
        FakeExecResult result = new FakeExecResult();
        nextExecResults.add(result);
        return result;
    }

    public HostConnectionTestResult getNextResult() {
        return this.nextResult;
    }

    public void setNextResult(HostConnectionTestResult nextResult) {
        this.nextResult = nextResult;
    }

    public String getLastIp() {
        return lastIp;
    }

    public void setLastIp(String lastIp) {
        this.lastIp = lastIp;
    }

    public Integer getLastSshPort() {
        return lastSshPort;
    }

    public void setLastSshPort(Integer lastSshPort) {
        this.lastSshPort = lastSshPort;
    }

    public DecryptedCredential getLastCredential() {
        return lastCredential;
    }

    public String getLastCommand() {
        return lastCommand;
    }

    public byte[] getLastStdin() {
        return lastStdin;
    }

    public void setLastCredential(DecryptedCredential lastCredential) {
        this.lastCredential = lastCredential;
    }

    public static class FakeExecHandle implements SshExecHandle {
        private final PipedInputStream stdoutIn;
        private final PipedOutputStream stdoutOut;
        private volatile boolean closed;

        public FakeExecHandle() {
            try {
                this.stdoutIn = new PipedInputStream();
                this.stdoutOut = new PipedOutputStream(stdoutIn);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public InputStream stdout() {
            return stdoutIn;
        }

        @Override
        public InputStream stderr() {
            return InputStream.nullInputStream();
        }

        public void emitLine(String line) {
            if (closed) {
                throw new IllegalStateException("Handle is closed");
            }
            try {
                stdoutOut.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                stdoutOut.flush();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void close() {
            closed = true;
            try {
                stdoutOut.close();
            } catch (Exception ignored) {
            }
            try {
                stdoutIn.close();
            } catch (Exception ignored) {
            }
        }
    }

    public static class FakeExecResult {
        private Integer exitCode = 0;
        private String stdout = "";
        private String stderr = "";
        private boolean truncatedStdout;
        private boolean truncatedStderr;
        private boolean timedOut;

        public FakeExecResult exitCode(Integer exitCode) {
            this.exitCode = exitCode;
            return this;
        }

        public FakeExecResult stdout(String stdout) {
            this.stdout = stdout;
            return this;
        }

        public FakeExecResult stderr(String stderr) {
            this.stderr = stderr;
            return this;
        }

        public FakeExecResult truncatedStdout(boolean truncatedStdout) {
            this.truncatedStdout = truncatedStdout;
            return this;
        }

        public FakeExecResult truncatedStderr(boolean truncatedStderr) {
            this.truncatedStderr = truncatedStderr;
            return this;
        }

        public FakeExecResult timedOut(boolean timedOut) {
            this.timedOut = timedOut;
            return this;
        }

        private SshExecResult toResult() {
            return new SshExecResult(exitCode, stdout, stderr, truncatedStdout, truncatedStderr, timedOut);
        }
    }
}
