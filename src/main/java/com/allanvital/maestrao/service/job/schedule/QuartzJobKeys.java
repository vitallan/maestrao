package com.allanvital.maestrao.service.job.schedule;

import org.quartz.JobKey;
import org.quartz.TriggerKey;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public class QuartzJobKeys {

    public static final String GROUP = "job-def";
    public static final String JOB_ID_KEY = "jobDefinitionId";
    public static final String RUN_ID_KEY = "runId";

    private QuartzJobKeys() {
    }

    public static JobKey jobKey(Long jobDefinitionId) {
        return JobKey.jobKey("job-def-" + jobDefinitionId, GROUP);
    }

    public static TriggerKey triggerKey(Long jobDefinitionId) {
        return TriggerKey.triggerKey("trigger-" + jobDefinitionId, GROUP);
    }
}
