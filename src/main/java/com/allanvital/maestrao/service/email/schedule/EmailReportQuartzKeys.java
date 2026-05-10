package com.allanvital.maestrao.service.email.schedule;

import org.quartz.JobKey;
import org.quartz.TriggerKey;

final class EmailReportQuartzKeys {

    private static final String GROUP = "email";

    private EmailReportQuartzKeys() {
    }

    static JobKey jobKey() {
        return JobKey.jobKey("daily-email-report", GROUP);
    }

    static TriggerKey triggerKey() {
        return TriggerKey.triggerKey("daily-email-report-trigger", GROUP);
    }
}
