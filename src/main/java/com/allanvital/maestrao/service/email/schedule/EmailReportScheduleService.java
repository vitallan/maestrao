package com.allanvital.maestrao.service.email.schedule;

import com.allanvital.maestrao.model.EmailSettings;
import com.allanvital.maestrao.service.email.EmailSettingsService;
import com.allanvital.maestrao.service.email.EmailSettingsValidator;
import jakarta.annotation.PostConstruct;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

@Service
public class EmailReportScheduleService {

    private static final Logger log = LoggerFactory.getLogger(EmailReportScheduleService.class);

    private final Scheduler scheduler;
    private final EmailSettingsService emailSettingsService;

    public EmailReportScheduleService(Scheduler scheduler, EmailSettingsService emailSettingsService) {
        this.scheduler = scheduler;
        this.emailSettingsService = emailSettingsService;
    }

    @PostConstruct
    public void syncOnStartup() {
        try {
            applyOrUnscheduleFromDb();
        } catch (RuntimeException e) {
            log.warn("emailReportSchedule.startupSyncFailed error={}", safeMessage(e));
            log.debug("emailReportSchedule.startupSyncFailedStack", e);
        }
    }

    public void applyOrUnscheduleFromDb() {
        EmailSettings settings = emailSettingsService.getOrCreate();

        if (!settings.isEnabled()) {
            unschedule();
            return;
        }

        EmailSettingsValidator.ValidationResult validation = EmailSettingsValidator.validate(settings);
        if (!validation.ok()) {
            // Don't schedule if settings are invalid.
            settings.setLastError(validation.message());
            emailSettingsService.save(settings);
            unschedule();
            return;
        }

        schedule(settings.getSendTime());
    }

    private void schedule(LocalTime sendTime) {
        if (sendTime == null) {
            unschedule();
            return;
        }

        JobKey jobKey = EmailReportQuartzKeys.jobKey();
        TriggerKey triggerKey = EmailReportQuartzKeys.triggerKey();

        String cron = "0 " + sendTime.getMinute() + " " + sendTime.getHour() + " * * ?";
        CronScheduleBuilder schedule = cronSchedule(cron)
                .inTimeZone(java.util.TimeZone.getTimeZone(ZoneId.systemDefault()))
                .withMisfireHandlingInstructionDoNothing();

        JobDetail detail = newJob(DailyEmailReportQuartzJob.class)
                .withIdentity(jobKey)
                .storeDurably(true)
                .build();

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
            throw new IllegalStateException("Failed to schedule email report: " + e.getMessage(), e);
        }

        log.info("emailReportSchedule.apply cron={} zone={}", cron, ZoneId.systemDefault());
    }

    public void unschedule() {
        JobKey jobKey = EmailReportQuartzKeys.jobKey();
        TriggerKey triggerKey = EmailReportQuartzKeys.triggerKey();
        try {
            if (scheduler.checkExists(triggerKey)) {
                scheduler.unscheduleJob(triggerKey);
            }
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to unschedule email report: " + e.getMessage(), e);
        }
        log.info("emailReportSchedule.unschedule");
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
}
