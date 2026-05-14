package com.allanvital.maestrao.service.job;

import com.allanvital.maestrao.model.JobExecution;
import com.allanvital.maestrao.model.JobExecutionStatus;
import com.allanvital.maestrao.model.JobRun;
import com.allanvital.maestrao.model.JobRunStatus;
import com.allanvital.maestrao.repository.JobFailedExecutionRow;
import com.allanvital.maestrao.repository.JobExecutionRepository;
import com.allanvital.maestrao.repository.JobRunExecutionCountRow;
import com.allanvital.maestrao.repository.JobRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;


/**
 * @author Allan Vital (https://allanvital.com)
 */
@Service
public class JobQueryService {

    private final JobRunRepository jobRunRepository;
    private final JobExecutionRepository jobExecutionRepository;

    public JobQueryService(JobRunRepository jobRunRepository, JobExecutionRepository jobExecutionRepository) {
        this.jobRunRepository = jobRunRepository;
        this.jobExecutionRepository = jobExecutionRepository;
    }

    @Transactional(readOnly = true)
    public Map<Long, JobLastOutcome> getLastOutcomes(List<Long> jobDefinitionIds) {
        if (jobDefinitionIds == null || jobDefinitionIds.isEmpty()) {
            return Map.of();
        }

        List<Long> ids = jobDefinitionIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        List<JobRun> latestRuns = jobRunRepository.findLatestRunsByJobDefinitionIds(ids);
        if (latestRuns == null || latestRuns.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> jobIdToRunId = new HashMap<>();
        for (JobRun run : latestRuns) {
            if (run == null || run.getJobDefinition() == null || run.getJobDefinition().getId() == null || run.getId() == null) {
                continue;
            }
            jobIdToRunId.put(run.getJobDefinition().getId(), run.getId());
        }

        List<Long> runIds = jobIdToRunId.values().stream().distinct().toList();
        if (runIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> runIdToExecutionCount = new HashMap<>();
        List<JobRunExecutionCountRow> counts = jobExecutionRepository.countExecutionsByRunIds(runIds);
        if (counts != null) {
            for (JobRunExecutionCountRow row : counts) {
                if (row == null || row.runId() == null) {
                    continue;
                }
                runIdToExecutionCount.put(row.runId(), row.executionCount());
            }
        }

        List<JobExecutionStatus> failedStatuses = List.of(JobExecutionStatus.FAILED, JobExecutionStatus.TIMEOUT, JobExecutionStatus.ABORTED);
        List<JobFailedExecutionRow> failed = jobExecutionRepository.findFailedExecutionsByRunIds(runIds, failedStatuses);

        Map<Long, List<JobFailedExecutionRow>> failedByRun = new HashMap<>();
        if (failed != null) {
            for (JobFailedExecutionRow row : failed) {
                if (row == null || row.runId() == null) {
                    continue;
                }
                failedByRun.computeIfAbsent(row.runId(), k -> new ArrayList<>()).add(row);
            }
        }

        Map<Long, JobLastOutcome> outcomes = new HashMap<>();
        for (Map.Entry<Long, Long> e : jobIdToRunId.entrySet()) {
            Long jobId = e.getKey();
            Long runId = e.getValue();
            long executionCount = runIdToExecutionCount.getOrDefault(runId, 0L);

            if (executionCount <= 0) {
                outcomes.put(jobId, JobLastOutcome.none());
                continue;
            }

            List<JobFailedExecutionRow> failedRows = failedByRun.getOrDefault(runId, List.of());
            if (failedRows.isEmpty()) {
                outcomes.put(jobId, JobLastOutcome.ok());
                continue;
            }

            JobFailedExecutionRow first = failedRows.get(0);
            String host = normalizeOptional(first.hostName());
            if (host == null) {
                host = "-";
            }
            String reason = normalizeOptional(first.errorMessage());
            if (reason == null) {
                String status = first.status() == null ? "FAILED" : first.status().name();
                if (first.exitCode() != null) {
                    reason = status + " exit=" + first.exitCode();
                } else {
                    reason = status;
                }
            }
            outcomes.put(jobId, JobLastOutcome.failed(failedRows.size(), host, reason));
        }

        return outcomes;
    }

    @Transactional(readOnly = true)
    public Map<Long, Boolean> getRunningStates(List<Long> jobDefinitionIds) {
        if (jobDefinitionIds == null || jobDefinitionIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = jobDefinitionIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Boolean> states = new HashMap<>();
        for (Long id : ids) {
            states.put(id, false);
        }
        jobRunRepository.findRunningJobDefinitionIds(ids, JobRunStatus.RUNNING)
                .forEach(row -> {
                    if (row != null && row.jobDefinitionId() != null) {
                        states.put(row.jobDefinitionId(), true);
                    }
                });
        return states;
    }

    @Transactional(readOnly = true)
    public Page<JobRun> findRuns(Long jobDefinitionId, Pageable pageable) {
        return jobRunRepository.findAllByJobDefinitionIdOrderByIdDesc(jobDefinitionId, pageable);
    }

    @Transactional(readOnly = true)
    public long countRuns(Long jobDefinitionId) {
        return jobRunRepository.countByJobDefinitionId(jobDefinitionId);
    }

    @Transactional(readOnly = true)
    public Page<JobExecution> findExecutions(Long runId, Pageable pageable) {
        return jobExecutionRepository.findAllByJobRunIdOrderByIdAsc(runId, pageable);
    }

    @Transactional(readOnly = true)
    public long countExecutions(Long runId) {
        if (runId == null) {
            return 0;
        }
        return jobExecutionRepository.countByJobRunId(runId);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record JobLastOutcome(boolean failed, String summary) {
        public static JobLastOutcome none() {
            return new JobLastOutcome(false, "-");
        }

        public static JobLastOutcome ok() {
            return new JobLastOutcome(false, "OK");
        }

        public static JobLastOutcome failed(int count, String hostName, String reason) {
            int n = Math.max(1, count);
            String c = n + (n == 1 ? " failure" : " failures");
            return new JobLastOutcome(true, c + ": " + hostName + ": " + reason);
        }
    }
}
