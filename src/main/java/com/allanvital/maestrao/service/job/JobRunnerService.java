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
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

        for (Long executionId : executionIds) {
            try {
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
            } catch (RuntimeException e) {
                tx.execute(status -> {
                    JobExecution exec = jobExecutionRepository.findById(executionId).orElse(null);
                    if (exec != null) {
                        exec.setStatus(JobExecutionStatus.FAILED);
                        exec.setFinishedAt(Instant.now());
                        exec.setErrorMessage(safeMessage(e));
                        jobExecutionRepository.save(exec);

                        Host h = exec.getHost();
                        failed[0]++;
                        log.warn("jobExec.failed execId={} host={} exitCode={} error={}",
                                exec.getId(), h == null ? null : h.getName(), exec.getExitCode(), safeMessage(e));
                        log.debug("jobExec.failedStack execId={}", exec.getId(), e);
                    }
                    return null;
                });
            }
        }

        tx.execute(status -> {
            JobRun run = jobRunRepository.findById(runId).orElse(null);
            if (run != null) {
                run.setStatus(JobRunStatus.COMPLETED);
                run.setFinishedAt(Instant.now());
                jobRunRepository.save(run);
            }
            return null;
        });

        long durationMs = Duration.between(runStart, Instant.now()).toMillis();
        log.info("jobRun.finish runId={} durationMs={} success={} failed={} timeout={}",
                runId, durationMs, success[0], failed[0], timeout[0]);
    }

    private void runSingleExecution(Long executionId) {
        tx.execute(status -> {
            JobExecution exec = jobExecutionRepository.findById(executionId)
                    .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

            JobRun run = exec.getJobRun();
            JobDefinition job = run.getJobDefinition();
            Host host = exec.getHost();
            Credential credential = host.getCredential();

            String secret = credentialCryptoService.decrypt(credential.getEncryptedSecret());
            DecryptedCredential decryptedCredential = new DecryptedCredential(credential, secret);

            ExecSpec spec = buildExecSpec(job);
            Instant execStart = Instant.now();
            SshClient.SshExecResult result = sshClient.execBlocking(
                    host.getIp(),
                    host.getSshPort(),
                    decryptedCredential,
                    spec.command,
                    spec.stdin,
                    timeoutMillis,
                    maxOutputBytes,
                    maxOutputBytes
            );

            exec.setStdout(result.stdout());
            exec.setStderr(result.stderr());
            exec.setTruncatedStdout(result.truncatedStdout());
            exec.setTruncatedStderr(result.truncatedStderr());
            exec.setExitCode(result.exitCode());
            exec.setFinishedAt(Instant.now());

            if (result.timedOut()) {
                exec.setStatus(JobExecutionStatus.TIMEOUT);
            } else if (result.exitCode() != null && result.exitCode() == 0) {
                exec.setStatus(JobExecutionStatus.SUCCESS);
            } else {
                exec.setStatus(JobExecutionStatus.FAILED);
            }

            jobExecutionRepository.save(exec);
            return null;
        });
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
}
