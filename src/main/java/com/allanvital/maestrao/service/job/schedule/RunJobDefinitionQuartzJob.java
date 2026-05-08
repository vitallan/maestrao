package com.allanvital.maestrao.service.job.schedule;

import com.allanvital.maestrao.service.job.JobRunnerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.quartz.*;
import org.springframework.stereotype.Component;

/**
 * Quartz entrypoint for running a job definition.
 *
 * @author Allan Vital (https://allanvital.com)
 */
@Component
@DisallowConcurrentExecution
public class RunJobDefinitionQuartzJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(RunJobDefinitionQuartzJob.class);

    private final JobRunnerService jobRunnerService;

    public RunJobDefinitionQuartzJob(JobRunnerService jobRunnerService) {
        this.jobRunnerService = jobRunnerService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap merged = context.getMergedJobDataMap();
        Long jobDefinitionId = toLong(merged.get(QuartzJobKeys.JOB_ID_KEY));
        Long runId = toLong(merged.get(QuartzJobKeys.RUN_ID_KEY));

        log.info("jobQuartz.execute jobId={} runId={}", jobDefinitionId, runId);

        if (jobDefinitionId == null) {
            log.warn("jobQuartz.executeMissingJobId");
            throw new IllegalArgumentException("jobDefinitionId is required");
        }

        if (runId != null) {
            jobRunnerService.runExistingRun(jobDefinitionId, runId);
        } else {
            jobRunnerService.runOnce(jobDefinitionId);
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Integer i) {
            return i.longValue();
        }
        if (value instanceof String s) {
            if (s.isBlank()) {
                return null;
            }
            return Long.parseLong(s.trim());
        }
        throw new IllegalArgumentException("Invalid numeric value: " + value);
    }
}
