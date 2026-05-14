package com.allanvital.maestrao.service.job;

import com.allanvital.maestrao.model.*;
import com.allanvital.maestrao.repository.JobDefinitionRepository;
import com.allanvital.maestrao.repository.JobExecutionRepository;
import com.allanvital.maestrao.repository.JobRunRepository;
import com.allanvital.maestrao.security.CredentialCryptoService;
import com.allanvital.maestrao.service.job.schedule.JobScheduleService;
import com.allanvital.maestrao.service.ssh.SshClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Service
public class JobRunnerService {

    private static final Logger log = LoggerFactory.getLogger(JobRunnerService.class);

    private final JobDefinitionRepository jobDefinitionRepository;
    private final JobRunRepository jobRunRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final SshClient sshClient;
    private final JobScheduleService jobScheduleService;
    private final TransactionTemplate tx;

    private final long timeoutMillis;
    private final int maxOutputBytes;
    private final ConcurrentHashMap<Long, SshClient.SshExecHandle> activeRunHandles = new ConcurrentHashMap<>();

    public JobRunnerService(JobDefinitionRepository jobDefinitionRepository,
                            JobRunRepository jobRunRepository,
                            JobExecutionRepository jobExecutionRepository,
                            CredentialCryptoService credentialCryptoService,
                            SshClient sshClient,
                            JobScheduleService jobScheduleService,
                            PlatformTransactionManager transactionManager,
                            @Value("${maestrao.jobs.timeout-ms:60000}") long timeoutMillis,
                            @Value("${maestrao.jobs.max-output-bytes:262144}") int maxOutputBytes) {
        this.jobDefinitionRepository = jobDefinitionRepository;
        this.jobRunRepository = jobRunRepository;
        this.jobExecutionRepository = jobExecutionRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.sshClient = sshClient;
        this.jobScheduleService = jobScheduleService;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tx = template;
        this.timeoutMillis = timeoutMillis;
        this.maxOutputBytes = maxOutputBytes;
    }

    public Long startRun(Long jobDefinitionId) {
        if (jobDefinitionId == null) {
            throw new IllegalArgumentException("job is required");
        }

        Long runId = createRun(jobDefinitionId);
        // Queue execution via Quartz (non-overlapping per job definition).
        jobScheduleService.triggerNow(jobDefinitionId, runId);
        log.info("jobRun.request jobId={} runId={}", jobDefinitionId, runId);
        return runId;
    }

    /**
     * Used by Quartz for scheduled executions (creates its own run).
     */
    public void runOnce(Long jobDefinitionId) {
        Long runId = createRun(jobDefinitionId);
        log.info("jobRun.startScheduled jobId={} runId={}", jobDefinitionId, runId);
        runSequential(runId);
    }

    /**
     * Used by Quartz when a run was pre-created (manual "Run").
     */
    public void runExistingRun(Long jobDefinitionId, Long runId) {
        if (jobDefinitionId == null || runId == null) {
            throw new IllegalArgumentException("job and run are required");
        }
        Long runJobId = tx.execute(status -> jobRunRepository.findById(runId)
                .map(r -> r.getJobDefinition() == null ? null : r.getJobDefinition().getId())
                .orElse(null));

        if (runJobId == null) {
            throw new IllegalArgumentException("Run not found: " + runId);
        }
        if (!runJobId.equals(jobDefinitionId)) {
            throw new IllegalArgumentException("Run does not belong to job: " + jobDefinitionId);
        }

        log.info("jobRun.startExisting jobId={} runId={}", jobDefinitionId, runId);
        runSequential(runId);
    }

    public void requestAbortRun(Long runId) {
        if (runId == null) {
            throw new IllegalArgumentException("run is required");
        }
        tx.execute(status -> {
            JobRun run = jobRunRepository.findById(runId)
                    .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
            if (run.getStatus() == JobRunStatus.RUNNING) {
                run.setAbortRequested(true);
                jobRunRepository.save(run);
            }
            return null;
        });
        SshClient.SshExecHandle handle = activeRunHandles.get(runId);
        if (handle != null) {
            handle.close();
        }
        log.info("jobRun.abortRequested runId={}", runId);
    }

    private Long createRun(Long jobDefinitionId) {
        Long runId = tx.execute(status -> {
            JobDefinition job = jobDefinitionRepository.findWithHostsById(jobDefinitionId)
                    .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobDefinitionId));

            List<Host> hosts = job.getHosts().stream()
                    .sorted(Comparator.comparing(Host::getId))
                    .toList();

            if (hosts.isEmpty()) {
                throw new IllegalArgumentException("Job has no hosts assigned");
            }

            JobRun run = new JobRun();
            run.setJobDefinition(job);
            run.setStatus(JobRunStatus.RUNNING);
            run.setAbortRequested(false);
            run.setStartedAt(Instant.now());
            run = jobRunRepository.save(run);

            List<JobExecution> executions = new ArrayList<>();
            for (Host host : hosts) {
                JobExecution exec = new JobExecution();
                exec.setJobRun(run);
                exec.setHost(host);
                exec.setStatus(JobExecutionStatus.PENDING);
                executions.add(exec);
            }
            jobExecutionRepository.saveAll(executions);
            return run.getId();
        });

        if (runId != null) {
            int hostCount = tx.execute(status -> jobDefinitionRepository.findWithHostsById(jobDefinitionId)
                    .map(j -> j.getHosts() == null ? 0 : j.getHosts().size())
                    .orElse(0));
            log.info("jobRun.created jobId={} runId={} hosts={}", jobDefinitionId, runId, hostCount);
        }
        return runId;
    }

    private void runSequential(Long runId) {
        Instant runStart = Instant.now();
        List<Long> executionIds = tx.execute(status -> jobExecutionRepository.findAllByJobRunIdOrderByIdAsc(runId)
                .stream()
                .map(JobExecution::getId)
                .toList());

        if (executionIds == null || executionIds.isEmpty()) {
            return;
        }

        log.info("jobRun.start runId={} executions={}", runId, executionIds.size());

        final long[] success = {0};
        final long[] failed = {0};
        final long[] timeout = {0};

        try {
            for (Long executionId : executionIds) {
                if (isAbortRequested(runId)) {
                    markExecutionAbortedIfActive(executionId, "run abort requested");
                    continue;
                }
                tx.execute(status -> {
                    JobExecution exec = jobExecutionRepository.findById(executionId)
                            .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

                    if (log.isDebugEnabled()) {
                        Host h = exec.getHost();
                        log.debug("jobExec.start execId={} runId={} host={} ip={}",
                                executionId, runId,
                                h == null ? null : h.getName(),
                                h == null ? null : h.getIp());
                    }

                    exec.setStatus(JobExecutionStatus.RUNNING);
                    exec.setStartedAt(Instant.now());
                    jobExecutionRepository.save(exec);
                    return null;
                });

                runSingleExecution(executionId);

                tx.execute(status -> {
                    JobExecution exec = jobExecutionRepository.findById(executionId).orElse(null);
                    if (exec == null) {
                        return null;
                    }
                    Host h = exec.getHost();
                    long durationMs = exec.getStartedAt() == null || exec.getFinishedAt() == null ? -1
                            : Duration.between(exec.getStartedAt(), exec.getFinishedAt()).toMillis();

                    if (exec.getStatus() == JobExecutionStatus.SUCCESS) {
                        success[0]++;
                        log.info("jobExec.success execId={} host={} exitCode={} durationMs={}",
                                exec.getId(), h == null ? null : h.getName(), exec.getExitCode(), durationMs);
                    } else if (exec.getStatus() == JobExecutionStatus.TIMEOUT) {
                        timeout[0]++;
                        log.warn("jobExec.timeout execId={} host={} durationMs={}",
                                exec.getId(), h == null ? null : h.getName(), durationMs);
                    } else {
                        failed[0]++;
                        log.warn("jobExec.failed execId={} host={} exitCode={} error={}",
                                exec.getId(), h == null ? null : h.getName(), exec.getExitCode(), summarize(exec.getErrorMessage()));
                    }
                    return null;
                });
            }
        } catch (RuntimeException e) {
            tx.execute(status -> {
                JobExecution exec = jobExecutionRepository.findAllByJobRunIdOrderByIdAsc(runId)
                        .stream()
                        .filter(x -> x.getStatus() == JobExecutionStatus.RUNNING)
                        .findFirst()
                        .orElse(null);
                if (exec != null) {
                    exec.setStatus(JobExecutionStatus.FAILED);
                    exec.setFinishedAt(Instant.now());
                    exec.setErrorMessage(safeMessage(e));
                    jobExecutionRepository.save(exec);
                }
                return null;
            });
            failed[0]++;
            log.warn("jobRun.failed runId={} error={}", runId, safeMessage(e));
            log.debug("jobRun.failedStack runId={}", runId, e);
        } finally {
            finalizeRun(runId);
        }

        long durationMs = Duration.between(runStart, Instant.now()).toMillis();
        log.info("jobRun.finish runId={} durationMs={} success={} failed={} timeout={}",
                runId, durationMs, success[0], failed[0], timeout[0]);
    }

    private void finalizeRun(Long runId) {
        tx.execute(status -> {
            JobRun run = jobRunRepository.findById(runId).orElse(null);
            if (run == null) {
                return null;
            }
            if (run.getFinishedAt() == null) {
                run.setFinishedAt(Instant.now());
            }
            run.setStatus(run.isAbortRequested() ? JobRunStatus.ABORTED : JobRunStatus.COMPLETED);
            jobRunRepository.save(run);
            return null;
        });
    }

    private boolean isAbortRequested(Long runId) {
        return tx.execute(status -> jobRunRepository.findById(runId).map(JobRun::isAbortRequested).orElse(false));
    }

    private void markExecutionAbortedIfActive(Long executionId, String reason) {
        tx.execute(status -> {
            JobExecution exec = jobExecutionRepository.findById(executionId).orElse(null);
            if (exec == null) {
                return null;
            }
            if (exec.getStatus() == JobExecutionStatus.SUCCESS
                    || exec.getStatus() == JobExecutionStatus.FAILED
                    || exec.getStatus() == JobExecutionStatus.TIMEOUT
                    || exec.getStatus() == JobExecutionStatus.ABORTED) {
                return null;
            }
            exec.setStatus(JobExecutionStatus.ABORTED);
            exec.setFinishedAt(Instant.now());
            exec.setErrorMessage(reason);
            jobExecutionRepository.save(exec);
            return null;
        });
    }

    private void runSingleExecution(Long executionId) {
        ExecutionContext ctx = tx.execute(status -> {
            JobExecution exec = jobExecutionRepository.findById(executionId)
                    .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
            JobRun run = exec.getJobRun();
            JobDefinition job = run.getJobDefinition();
            Host host = exec.getHost();
            Credential credential = host.getCredential();
            String secret = credentialCryptoService.decrypt(credential.getEncryptedSecret());
            DecryptedCredential decryptedCredential = new DecryptedCredential(credential, secret);
            ExecSpec spec = buildExecSpec(job);
            return new ExecutionContext(exec.getId(), run.getId(), host, decryptedCredential, spec);
        });
        if (ctx == null) {
            throw new IllegalStateException("execution context missing: " + executionId);
        }
        runStreamingExecution(ctx.executionId(), ctx.runId(), ctx.host(), ctx.decryptedCredential(), ctx.spec());
    }

    private void runStreamingExecution(Long executionId,
                                       Long runId,
                                       Host host,
                                       DecryptedCredential decryptedCredential,
                                       ExecSpec spec) {
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        CaptureState outState = new CaptureState(maxOutputBytes);
        CaptureState errState = new CaptureState(maxOutputBytes);

        long deadline = System.currentTimeMillis() + Math.max(1_000, timeoutMillis);
        long nextFlushAt = System.currentTimeMillis() + 800;

        SshClient.SshExecHandle handle = sshClient.exec(host.getIp(), host.getSshPort(), decryptedCredential, spec.command, spec.stdin);
        activeRunHandles.put(runId, handle);
        try (handle) {
            while (true) {
                drain(handle.stdout(), stdout, outState);
                drain(handle.stderr(), stderr, errState);

                long now = System.currentTimeMillis();
                if (now >= nextFlushAt) {
                    flushPartial(executionId, stdout, stderr, outState, errState);
                    nextFlushAt = now + 800;
                }

                boolean abort = isAbortRequested(runId);
                if (abort) {
                    handle.close();
                    finalizeExecution(executionId, null, stdout, stderr, outState, errState, JobExecutionStatus.ABORTED, "run abort requested");
                    return;
                }

                if (now > deadline) {
                    handle.close();
                    finalizeExecution(executionId, null, stdout, stderr, outState, errState, JobExecutionStatus.TIMEOUT, "execution timed out");
                    return;
                }

                if (handle.isClosed()) {
                    drainRemaining(handle.stdout(), stdout, outState);
                    drainRemaining(handle.stderr(), stderr, errState);
                    Integer exit = handle.exitStatus();
                    JobExecutionStatus finalStatus = (exit != null && exit == 0) ? JobExecutionStatus.SUCCESS : JobExecutionStatus.FAILED;
                    finalizeExecution(executionId, exit, stdout, stderr, outState, errState, finalStatus, null);
                    return;
                }

                sleepShort();
            }
        } finally {
            activeRunHandles.remove(runId);
        }
    }

    private void finalizeExecution(Long execId,
                                   Integer exitCode,
                                   StringBuilder stdout,
                                   StringBuilder stderr,
                                   CaptureState outState,
                                   CaptureState errState,
                                   JobExecutionStatus status,
                                   String errorMessage) {
        tx.execute(s -> {
            JobExecution latest = jobExecutionRepository.findById(execId)
                    .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + execId));
            latest.setStdout(stdout.toString());
            latest.setStderr(stderr.toString());
            latest.setTruncatedStdout(outState.truncated);
            latest.setTruncatedStderr(errState.truncated);
            latest.setExitCode(exitCode);
            latest.setStatus(status);
            latest.setErrorMessage(errorMessage);
            latest.setFinishedAt(Instant.now());
            jobExecutionRepository.save(latest);
            return null;
        });
    }

    private void flushPartial(Long execId,
                              StringBuilder stdout,
                              StringBuilder stderr,
                              CaptureState outState,
                              CaptureState errState) {
        tx.execute(s -> {
            JobExecution latest = jobExecutionRepository.findById(execId).orElse(null);
            if (latest == null || latest.getStatus() != JobExecutionStatus.RUNNING) {
                return null;
            }
            latest.setStdout(stdout.toString());
            latest.setStderr(stderr.toString());
            latest.setTruncatedStdout(outState.truncated);
            latest.setTruncatedStderr(errState.truncated);
            jobExecutionRepository.save(latest);
            return null;
        });
    }

    private void drain(InputStream input, StringBuilder target, CaptureState state) {
        if (input == null) {
            return;
        }
        try {
            while (input.available() > 0) {
                int maxRead = Math.min(4096, input.available());
                byte[] buf = new byte[maxRead];
                int read = input.read(buf);
                if (read <= 0) {
                    return;
                }
                if (state.bytes >= state.maxBytes) {
                    state.truncated = true;
                    continue;
                }
                int allowed = Math.min(read, state.maxBytes - state.bytes);
                target.append(new String(buf, 0, allowed, StandardCharsets.UTF_8));
                state.bytes += allowed;
                if (allowed < read) {
                    state.truncated = true;
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void sleepShort() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    private void drainRemaining(InputStream input, StringBuilder target, CaptureState state) {
        if (input == null) {
            return;
        }
        try {
            byte[] buf = new byte[4096];
            while (true) {
                int read = input.read(buf);
                if (read <= 0) {
                    return;
                }
                if (state.bytes >= state.maxBytes) {
                    state.truncated = true;
                    continue;
                }
                int allowed = Math.min(read, state.maxBytes - state.bytes);
                target.append(new String(buf, 0, allowed, StandardCharsets.UTF_8));
                state.bytes += allowed;
                if (allowed < read) {
                    state.truncated = true;
                }
            }
        } catch (IOException ignored) {
        }
    }

    private String summarize(String msg) {
        if (msg == null || msg.isBlank()) {
            return null;
        }
        msg = msg.trim();
        if (msg.length() > 200) {
            return msg.substring(0, 200);
        }
        return msg;
    }

    private ExecSpec buildExecSpec(JobDefinition job) {
        String shellProgram = job.getShell().getProgram();
        boolean useSudo = job.isUseSudo();

        String cmd = shellProgram + " -se";
        if (useSudo) {
            cmd = "sudo -n -- " + cmd;
        }
        return new ExecSpec(cmd, job.getContent().getBytes(StandardCharsets.UTF_8));
    }

    private String safeMessage(Throwable t) {
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            return t.getClass().getSimpleName();
        }
        if (msg.length() > 500) {
            return msg.substring(0, 500);
        }
        return msg;
    }

    private static class ExecSpec {
        private final String command;
        private final byte[] stdin;

        private ExecSpec(String command, byte[] stdin) {
            this.command = command;
            this.stdin = stdin;
        }
    }

    private static class CaptureState {
        private final int maxBytes;
        private int bytes;
        private boolean truncated;

        private CaptureState(int maxBytes) {
            this.maxBytes = Math.max(0, maxBytes);
            this.bytes = 0;
            this.truncated = false;
        }
    }

    private record ExecutionContext(Long executionId,
                                    Long runId,
                                    Host host,
                                    DecryptedCredential decryptedCredential,
                                    ExecSpec spec) {
    }
}
