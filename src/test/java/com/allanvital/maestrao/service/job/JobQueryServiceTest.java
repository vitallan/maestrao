package com.allanvital.maestrao.service.job;

import com.allanvital.maestrao.TestConfig;
import com.allanvital.maestrao.model.*;
import com.allanvital.maestrao.repository.JobDefinitionRepository;
import com.allanvital.maestrao.repository.JobExecutionRepository;
import com.allanvital.maestrao.repository.JobRunRepository;
import com.allanvital.maestrao.service.CredentialService;
import com.allanvital.maestrao.service.HostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestConfig.class)
@Transactional
class JobQueryServiceTest {

    @Autowired
    private JobQueryService jobQueryService;

    @Autowired
    private JobDefinitionService jobDefinitionService;

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private HostService hostService;

    @Autowired
    private JobDefinitionRepository jobDefinitionRepository;

    @Autowired
    private JobRunRepository jobRunRepository;

    @Autowired
    private JobExecutionRepository jobExecutionRepository;

    @BeforeEach
    void setUp() {
        jobExecutionRepository.deleteAll();
        jobExecutionRepository.flush();

        jobRunRepository.deleteAll();
        jobRunRepository.flush();

        jobDefinitionRepository.deleteAll();
        jobDefinitionRepository.flush();
    }

    @Test
    void lastOutcomeShowsFailureCountAndHostnameAndReason() {
        Host h1 = createHost("web-01", "10.0.0.1");
        Host h2 = createHost("db-01", "10.0.0.2");

        JobDefinition job = jobDefinitionService.create(
                "MyJob",
                JobShell.BASH,
                false,
                "echo hi\n",
                java.util.Set.of(h1.getId(), h2.getId()),
                false,
                null
        );

        JobRun run = new JobRun();
        run.setJobDefinition(job);
        run.setStatus(JobRunStatus.COMPLETED);
        run.setStartedAt(Instant.parse("2026-05-05T10:00:00Z"));
        run.setFinishedAt(Instant.parse("2026-05-05T10:01:00Z"));
        JobRun savedRun = jobRunRepository.saveAndFlush(run);

        JobExecution e1 = new JobExecution();
        e1.setJobRun(savedRun);
        e1.setHost(h1);
        e1.setStatus(JobExecutionStatus.FAILED);
        e1.setExitCode(1);
        e1.setErrorMessage("boom");
        jobExecutionRepository.save(e1);

        JobExecution e2 = new JobExecution();
        e2.setJobRun(savedRun);
        e2.setHost(h2);
        e2.setStatus(JobExecutionStatus.TIMEOUT);
        jobExecutionRepository.save(e2);
        jobExecutionRepository.flush();

        Map<Long, JobQueryService.JobLastOutcome> outcomes = jobQueryService.getLastOutcomes(List.of(job.getId()));
        JobQueryService.JobLastOutcome outcome = outcomes.get(job.getId());
        assertNotNull(outcome);
        assertTrue(outcome.failed());
        assertTrue(outcome.summary().startsWith("2 failures: "));
        assertTrue(outcome.summary().contains("web-01"));
        assertTrue(outcome.summary().contains("boom"));
    }

    @Test
    void lastOutcomeIsDashWhenLastRunHasNoExecutions() {
        Host h1 = createHost("web-01", "10.0.0.1");
        JobDefinition job = jobDefinitionService.create(
                "MyJob",
                JobShell.BASH,
                false,
                "echo hi\n",
                java.util.Set.of(h1.getId()),
                false,
                null
        );

        JobRun run = new JobRun();
        run.setJobDefinition(job);
        run.setStatus(JobRunStatus.COMPLETED);
        run.setStartedAt(Instant.parse("2026-05-05T10:00:00Z"));
        run.setFinishedAt(Instant.parse("2026-05-05T10:01:00Z"));
        jobRunRepository.saveAndFlush(run);

        Map<Long, JobQueryService.JobLastOutcome> outcomes = jobQueryService.getLastOutcomes(List.of(job.getId()));
        JobQueryService.JobLastOutcome outcome = outcomes.get(job.getId());
        assertNotNull(outcome);
        assertEquals("-", outcome.summary());
    }

    private Host createHost(String name, String ip) {
        Credential credential = credentialService.create("cred-" + name, CredentialType.PASSWORD, "root", "pw", null);
        return hostService.create(name, ip, 22, null, credential.getId(), false);
    }
}
