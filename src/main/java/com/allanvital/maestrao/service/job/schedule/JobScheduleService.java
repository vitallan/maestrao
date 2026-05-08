package com.allanvital.maestrao.service.job.schedule;

import com.allanvital.maestrao.model.JobDefinition;
import com.allanvital.maestrao.repository.JobDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.quartz.*;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * Keeps Quartz schedules in sync with JobDefinition rows.
 *
 * @author Allan Vital (https://allanvital.com)
 */
@Service
public class JobScheduleService {

    private static final Logger log = LoggerFactory.getLogger(JobScheduleService.class);

    private final Scheduler scheduler;
    private final JobDefinitionRepository jobDefinitionRepository;
    private final Cron5ToQuartzCronConverter converter = new Cron5ToQuartzCronConverter();

    public JobScheduleService(Scheduler scheduler, JobDefinitionRepository jobDefinitionRepository) {
        this.scheduler = scheduler;
        this.jobDefinitionRepository = jobDefinitionRepository;
    }

    public void syncAllOnStartup() {
        List<JobDefinition> jobs = jobDefinitionRepository.findAll();
        long invalid = 0;
        for (JobDefinition job : jobs) {
            try {
                applySchedule(job.getId());
            } catch (RuntimeException e) {
                // Ignore invalid schedules on startup; user can fix in UI.
                invalid++;
                log.warn("jobSchedule.invalid jobId={} cron5={} reason={}", job.getId(), job.getCron5(), safeMessage(e));
                log.debug("jobSchedule.invalidStack jobId={}", job.getId(), e);
            }
        }

        log.info("jobSchedule.sync startup jobs={} invalid={}", jobs.size(), invalid);
    }

    public void applySchedule(Long jobDefinitionId) {
        if (jobDefinitionId == null) {
            return;
        }
        JobDefinition job = jobDefinitionRepository.findWithHostsById(jobDefinitionId).orElse(null);
        if (job == null) {
            unschedule(jobDefinitionId);
            return;
        }

        boolean hasHosts = job.getHosts() != null && !job.getHosts().isEmpty();
        boolean enabled = job.isScheduleEnabled();
        String cron5 = normalizeOptional(job.getCron5());

        if (!enabled || cron5 == null || !hasHosts) {
            // Auto-unschedule if hosts become empty or schedule disabled.
            unschedule(jobDefinitionId);
            return;
        }

        String quartzCron = converter.toQuartzCron(cron5);
        log.info("jobSchedule.apply jobId={} cron5={} quartzCron={}", jobDefinitionId, cron5, quartzCron);

        JobKey jobKey = QuartzJobKeys.jobKey(jobDefinitionId);
        TriggerKey triggerKey = QuartzJobKeys.triggerKey(jobDefinitionId);

        JobDetail detail = newJob(RunJobDefinitionQuartzJob.class)
                .withIdentity(jobKey)
                .usingJobData(QuartzJobKeys.JOB_ID_KEY, jobDefinitionId)
                .storeDurably(true)
                .build();

        CronScheduleBuilder schedule = cronSchedule(quartzCron)
                .inTimeZone(java.util.TimeZone.getTimeZone(ZoneId.systemDefault()))
                .withMisfireHandlingInstructionDoNothing();

        Trigger trigger = newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobKey)
                .withSchedule(schedule)
                .build();

        try {
            scheduler.addJob(detail, true);
            if (scheduler.checkExists(triggerKey)) {
                scheduler.rescheduleJob(triggerKey, trigger);
            } else {
                scheduler.scheduleJob(trigger);
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to apply schedule: " + e.getMessage(), e);
        }
    }

    public void unschedule(Long jobDefinitionId) {
        JobKey jobKey = QuartzJobKeys.jobKey(jobDefinitionId);
        TriggerKey triggerKey = QuartzJobKeys.triggerKey(jobDefinitionId);
        try {
            if (scheduler.checkExists(triggerKey)) {
                scheduler.unscheduleJob(triggerKey);
            }
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to unschedule job: " + e.getMessage(), e);
        }

        log.info("jobSchedule.unschedule jobId={}", jobDefinitionId);
    }

    public void triggerNow(Long jobDefinitionId, Long runId) {
        if (jobDefinitionId == null) {
            return;
        }
        JobKey jobKey = QuartzJobKeys.jobKey(jobDefinitionId);
        JobDataMap data = new JobDataMap();
        data.put(QuartzJobKeys.JOB_ID_KEY, jobDefinitionId);
        if (runId != null) {
            data.put(QuartzJobKeys.RUN_ID_KEY, runId);
        }
        try {
            if (!scheduler.checkExists(jobKey)) {
                JobDetail detail = JobBuilder.newJob(RunJobDefinitionQuartzJob.class)
                        .withIdentity(jobKey)
                        .usingJobData(QuartzJobKeys.JOB_ID_KEY, jobDefinitionId)
                        .storeDurably(true)
                        .build();
                scheduler.addJob(detail, true);
            }
            scheduler.triggerJob(jobKey, data);
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to trigger job: " + e.getMessage(), e);
        }

        log.info("jobSchedule.triggerNow jobId={} runId={}", jobDefinitionId, runId);
    }

    private String safeMessage(Throwable t) {
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            return t.getClass().getSimpleName();
        }
        msg = msg.trim();
        if (msg.length() > 300) {
            return msg.substring(0, 300);
        }
        return msg;
    }

    public Instant getNextFireTime(Long jobDefinitionId) {
        if (jobDefinitionId == null) {
            return null;
        }
        TriggerKey triggerKey = QuartzJobKeys.triggerKey(jobDefinitionId);
        try {
            Trigger trigger = scheduler.getTrigger(triggerKey);
            if (trigger == null) {
                return null;
            }
            Date next = trigger.getNextFireTime();
            return next == null ? null : next.toInstant();
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to read next fire time: " + e.getMessage(), e);
        }
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
