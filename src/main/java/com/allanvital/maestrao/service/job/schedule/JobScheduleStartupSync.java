package com.allanvital.maestrao.service.job.schedule;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Component
public class JobScheduleStartupSync {

    private static final Logger log = LoggerFactory.getLogger(JobScheduleStartupSync.class);

    private final JobScheduleService jobScheduleService;

    public JobScheduleStartupSync(JobScheduleService jobScheduleService) {
        this.jobScheduleService = jobScheduleService;
    }

    @PostConstruct
    public void syncOnStartup() {
        log.info("jobSchedule.startupSync begin");
        jobScheduleService.syncAllOnStartup();
        log.info("jobSchedule.startupSync done");
    }
}
