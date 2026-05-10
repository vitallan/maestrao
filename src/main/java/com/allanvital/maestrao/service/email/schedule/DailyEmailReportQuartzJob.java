package com.allanvital.maestrao.service.email.schedule;

import com.allanvital.maestrao.service.email.DailyEmailReportService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quartz entrypoint for sending the daily email report.
 */
public class DailyEmailReportQuartzJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(DailyEmailReportQuartzJob.class);

    private final DailyEmailReportService reportService;

    public DailyEmailReportQuartzJob(DailyEmailReportService reportService) {
        this.reportService = reportService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("emailReportQuartz.execute");
        reportService.sendYesterdayReport();
    }
}
