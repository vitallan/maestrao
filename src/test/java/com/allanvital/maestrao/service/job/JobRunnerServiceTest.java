package com.allanvital.maestrao.service.job;

import com.allanvital.maestrao.TestConfig;
import com.allanvital.maestrao.model.*;
import com.allanvital.maestrao.repository.CredentialRepository;
import com.allanvital.maestrao.repository.HostRepository;
import com.allanvital.maestrao.repository.JobDefinitionRepository;
import com.allanvital.maestrao.repository.JobExecutionRepository;
import com.allanvital.maestrao.repository.JobRunRepository;
import com.allanvital.maestrao.service.CredentialService;
import com.allanvital.maestrao.service.HostService;
import com.allanvital.maestrao.ssh.FakeSshClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@SpringBootTest
@Import(TestConfig.class)
@TestPropertySource(properties = {
        "maestrao.jobs.timeout-ms=1000",
        "maestrao.jobs.max-output-bytes=262144"
})
public class JobRunnerServiceTest {

    @Autowired
    private JobRunnerService jobRunnerService;

    @Autowired
    private JobDefinitionService jobDefinitionService;

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private HostService hostService;

    @Autowired
    private FakeSshClient fakeSshClient;

    @Autowired
    private JobExecutionRepository jobExecutionRepository;

    @Autowired
    private JobRunRepository jobRunRepository;

    @Autowired
    private JobDefinitionRepository jobDefinitionRepository;

    @Autowired
    private HostRepository hostRepository;

    @Autowired
    private CredentialRepository credentialRepository;

    @BeforeEach
    void setUp() {
        jobExecutionRepository.deleteAll();
        jobExecutionRepository.flush();

        jobRunRepository.deleteAll();
        jobRunRepository.flush();

        jobDefinitionRepository.deleteAll();
        jobDefinitionRepository.flush();

        hostRepository.deleteAll();
        hostRepository.flush();

        credentialRepository.deleteAll();
        credentialRepository.flush();

        fakeSshClient.reset();
    }

    @Test
    void shouldRunCommandSequentiallyAndPersistOutput() {
        Long hostA = createHost("Host A", "10.0.0.1");
        Long hostB = createHost("Host B", "10.0.0.2");

        JobDefinition job = jobDefinitionService.create(
                "Echo",
                JobShell.BASH,
                true,
                "echo hello\n",
                setOf(hostA, hostB),
                false,
                null
        );

        fakeSshClient.enqueueExecResult().exitCode(0).stdout("hello\n");
        fakeSshClient.enqueueExecResult().exitCode(0).stdout("hello\n");

        Long runId = jobRunnerService.startRun(job.getId());

        awaitRunCompleted(runId, 5000);

        List<JobExecution> executions = jobExecutionRepository.findAllByJobRunIdOrderByIdAsc(runId);
        assertEquals(2, executions.size());
        assertEquals(JobExecutionStatus.SUCCESS, executions.get(0).getStatus());
        assertEquals("hello\n", executions.get(0).getStdout());
        assertEquals(JobExecutionStatus.SUCCESS, executions.get(1).getStatus());
        assertEquals("hello\n", executions.get(1).getStdout());

        JobRun run = jobRunRepository.findById(runId).orElseThrow();
        assertEquals(JobRunStatus.COMPLETED, run.getStatus());
        assertNotNull(run.getFinishedAt());

        assertEquals("sudo -n -- bash -se", fakeSshClient.getLastCommand());
        assertEquals("echo hello\n", new String(fakeSshClient.getLastStdin(), StandardCharsets.UTF_8));
    }

    @Test
    void shouldRunScriptViaStdinUsingSelectedShell() {
        Long hostId = createHost("Host A", "10.0.0.1");

        JobDefinition job = jobDefinitionService.create(
                "Script",
                JobShell.SH,
                false,
                "echo one\necho two\n",
                setOf(hostId),
                false,
                null
        );

        fakeSshClient.enqueueExecResult().exitCode(0).stdout("one\ntwo\n");

        Long runId = jobRunnerService.startRun(job.getId());
        awaitRunCompleted(runId, 5000);

        assertEquals("sh -se", fakeSshClient.getLastCommand());
        assertNotNull(fakeSshClient.getLastStdin());
        assertEquals(job.getContent(), new String(fakeSshClient.getLastStdin(), StandardCharsets.UTF_8));
    }

    @Test
    void shouldRejectRunIfJobHasNoHostsAssigned() {
        JobDefinition job = jobDefinitionService.create(
                "Empty",
                JobShell.BASH,
                false,
                "echo hi\n",
                null,
                false,
                null
        );

        assertThrows(IllegalArgumentException.class, () -> jobRunnerService.startRun(job.getId()));
    }

    private Long createHost(String name, String ip) {
        Credential credential = credentialService.create("cred", CredentialType.PASSWORD, "root", "pw", null);
        Host host = hostService.create(name, ip, 22, null, credential.getId());
        return host.getId();
    }

    private java.util.Set<Long> setOf(Long... ids) {
        if (ids == null || ids.length == 0) {
            return java.util.Set.of();
        }
        return java.util.Set.of(ids);
    }

    private void awaitRunCompleted(Long runId, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            JobRun run = jobRunRepository.findById(runId).orElse(null);
            if (run != null && run.getStatus() == JobRunStatus.COMPLETED) {
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted");
            }
        }
        fail("Timed out waiting for run completion: " + runId);
    }
}
