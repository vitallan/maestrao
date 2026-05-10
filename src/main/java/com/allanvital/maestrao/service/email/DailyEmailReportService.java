package com.allanvital.maestrao.service.email;

import com.allanvital.maestrao.model.EmailSettings;
import com.allanvital.maestrao.model.JobExecutionStatus;
import com.allanvital.maestrao.model.JobRun;
import com.allanvital.maestrao.repository.*;
import com.allanvital.maestrao.security.CredentialCryptoService;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DailyEmailReportService {

    private static final Logger log = LoggerFactory.getLogger(DailyEmailReportService.class);

    private final EmailSettingsService emailSettingsService;
    private final CredentialCryptoService crypto;
    private final LogSourceRepository logSourceRepository;
    private final LogLineRepository logLineRepository;
    private final JobRunRepository jobRunRepository;
    private final JobExecutionRepository jobExecutionRepository;

    public DailyEmailReportService(EmailSettingsService emailSettingsService,
                                  CredentialCryptoService crypto,
                                  LogSourceRepository logSourceRepository,
                                  LogLineRepository logLineRepository,
                                  JobRunRepository jobRunRepository,
                                  JobExecutionRepository jobExecutionRepository) {
        this.emailSettingsService = emailSettingsService;
        this.crypto = crypto;
        this.logSourceRepository = logSourceRepository;
        this.logLineRepository = logLineRepository;
        this.jobRunRepository = jobRunRepository;
        this.jobExecutionRepository = jobExecutionRepository;
    }

    /**
     * Sends the report for "yesterday" based on server timezone.
     */
    @Transactional
    public void sendYesterdayReport() {
        EmailSettings settings = emailSettingsService.getOrCreate();
        if (!settings.isEnabled()) {
            return;
        }

        EmailSettingsValidator.ValidationResult validation = EmailSettingsValidator.validate(settings);
        if (!validation.ok()) {
            settings.setLastError(validation.message());
            emailSettingsService.save(settings);
            log.warn("emailReport.skip reason={}", validation.message());
            return;
        }

        ZoneId zone = ZoneId.systemDefault();
        LocalDate yesterday = LocalDate.now(zone).minusDays(1);
        Instant from = yesterday.atStartOfDay(zone).toInstant();
        Instant to = yesterday.plusDays(1).atStartOfDay(zone).toInstant();

        try {
            String subject = buildSubject(settings, yesterday);
            String body = buildBody(zone, yesterday, from, to);

            JavaMailSender sender = buildMailSender(settings);
            sendPlainText(sender, settings, subject, body);

            settings.setLastSentAt(Instant.now());
            settings.setLastError(null);
            emailSettingsService.save(settings);

            log.info("emailReport.sent date={} toCount={}", yesterday, EmailSettingsValidator.splitToAddresses(settings.getToAddresses()).size());
        } catch (RuntimeException e) {
            String msg = safeMessage(e);
            settings.setLastError(msg);
            emailSettingsService.save(settings);
            log.warn("emailReport.failed error={}", msg);
            log.debug("emailReport.failedStack", e);
        }
    }

    @Transactional(readOnly = true)
    public String buildYesterdayBodyForPreview() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate yesterday = LocalDate.now(zone).minusDays(1);
        Instant from = yesterday.atStartOfDay(zone).toInstant();
        Instant to = yesterday.plusDays(1).atStartOfDay(zone).toInstant();
        return buildBody(zone, yesterday, from, to);
    }

    @Transactional
    public void sendTestEmail() {
        EmailSettings settings = emailSettingsService.getOrCreate();
        EmailSettingsValidator.ValidationResult validation = EmailSettingsValidator.validate(settings);
        if (!validation.ok()) {
            throw new IllegalStateException(validation.message());
        }

        JavaMailSender sender = buildMailSender(settings);
        String prefix = EmailSettingsValidator.normalizeOptional(settings.getSubjectPrefix());
        String subject = (prefix == null ? "Maestrao" : prefix) + " - test";
        String body = "SMTP test OK\n\nSent at: " + Instant.now();
        sendPlainText(sender, settings, subject, body);
    }

    private String buildBody(ZoneId zone, LocalDate day, Instant from, Instant to) {
        List<com.allanvital.maestrao.model.LogSource> allSources = logSourceRepository.findAllByOrderByNameAsc();

        Map<Long, Long> counts = new HashMap<>();
        for (LogSourceLineCountRow row : logLineRepository.countByLogSourceBetween(from, to)) {
            if (row == null) {
                continue;
            }
            if (row.logSourceId() == null) {
                continue;
            }
            counts.put(row.logSourceId(), Math.max(0L, row.lineCount()));
        }

        List<JobRun> runs = jobRunRepository.findRunsStartedBetweenWithJobDefinition(from, to);
        List<Long> runIds = runs.stream().map(JobRun::getId).filter(Objects::nonNull).toList();

        Map<Long, Long> execCountsByRun = new HashMap<>();
        if (!runIds.isEmpty()) {
            List<JobRunExecutionCountRow> execCounts = jobExecutionRepository.countExecutionsByRunIds(runIds);
            if (execCounts != null) {
                for (JobRunExecutionCountRow row : execCounts) {
                    if (row == null || row.runId() == null) {
                        continue;
                    }
                    execCountsByRun.put(row.runId(), row.executionCount());
                }
            }
        }

        Set<Long> failedRunIds = new HashSet<>();
        if (!runIds.isEmpty()) {
            List<JobFailedExecutionRow> failed = jobExecutionRepository.findFailedExecutionsByRunIds(
                    runIds,
                    List.of(JobExecutionStatus.FAILED, JobExecutionStatus.TIMEOUT)
            );
            if (failed != null) {
                for (JobFailedExecutionRow row : failed) {
                    if (row != null && row.runId() != null) {
                        failedRunIds.add(row.runId());
                    }
                }
            }
        }

        DateTimeFormatter dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone);

        StringBuilder out = new StringBuilder(4096);
        out.append("Maestrao daily report\n");
        out.append("Date: ").append(day).append(" (server timezone: ").append(zone).append(")\n");
        out.append("Window: ").append(dt.format(from)).append(" -> ").append(dt.format(to)).append("\n\n");

        out.append("Log lines ingested\n");
        if (allSources.isEmpty()) {
            out.append("- (no loggers configured)\n");
        } else {
            for (com.allanvital.maestrao.model.LogSource s : allSources) {
                if (s == null || s.getId() == null) {
                    continue;
                }
                String name = EmailSettingsValidator.normalizeOptional(s.getName());
                if (name == null) {
                    name = "(unnamed)";
                }
                long c = counts.getOrDefault(s.getId(), 0L);
                out.append("- ").append(name).append(": ").append(c).append("\n");
            }
        }
        out.append("\n");

        out.append("Jobs\n");
        boolean anyJobLine = false;
        for (JobRun r : runs) {
            if (r == null || r.getId() == null || r.getJobDefinition() == null) {
                continue;
            }
            long execCount = execCountsByRun.getOrDefault(r.getId(), 0L);
            if (execCount <= 0) {
                // Omit runs with 0 executions.
                continue;
            }

            String jobName = EmailSettingsValidator.normalizeOptional(r.getJobDefinition().getName());
            if (jobName == null) {
                jobName = "(unnamed)";
            }
            String outcome = failedRunIds.contains(r.getId()) ? "ERROR" : "OK";

            out.append("- ")
                    .append(jobName)
                    .append(" (runId=").append(r.getId()).append(") ")
                    .append(outcome)
                    .append(" started=").append(r.getStartedAt() == null ? "-" : dt.format(r.getStartedAt()))
                    .append(" finished=").append(r.getFinishedAt() == null ? "-" : dt.format(r.getFinishedAt()))
                    .append("\n");
            anyJobLine = true;
        }
        if (!anyJobLine) {
            out.append("- (no job executions)\n");
        }

        return out.toString();
    }

    private JavaMailSender buildMailSender(EmailSettings settings) {
        JavaMailSenderImpl impl = new JavaMailSenderImpl();
        impl.setHost(settings.getSmtpHost());
        impl.setPort(settings.getSmtpPort() == null ? 587 : settings.getSmtpPort());

        String user = EmailSettingsValidator.normalizeOptional(settings.getSmtpUsername());
        if (user != null) {
            impl.setUsername(user);
        }

        String encrypted = EmailSettingsValidator.normalizeOptional(settings.getSmtpEncryptedPassword());
        if (encrypted != null) {
            // Password may exist even without username (some servers allow it, but uncommon).
            impl.setPassword(crypto.decrypt(encrypted));
        }

        Properties props = impl.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", user != null ? "true" : "false");
        props.put("mail.smtp.starttls.enable", settings.isSmtpUseStartTls() ? "true" : "false");
        props.put("mail.smtp.ssl.enable", settings.isSmtpUseSsl() ? "true" : "false");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "20000");
        props.put("mail.smtp.writetimeout", "20000");

        return impl;
    }

    private void sendPlainText(JavaMailSender sender, EmailSettings settings, String subject, String body) {
        List<String> to = EmailSettingsValidator.splitToAddresses(settings.getToAddresses());
        if (to.isEmpty()) {
            throw new IllegalStateException("To addresses are empty");
        }

        String from = EmailSettingsValidator.normalizeOptional(settings.getFromAddress());
        if (from == null) {
            throw new IllegalStateException("From address is required");
        }

        try {
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setSubject(subject);
            helper.setText(body, false);
            helper.setFrom(new InternetAddress(from));
            helper.setTo(to.toArray(String[]::new));
            msg.setHeader("X-Mailer", "Maestrao");
            sender.send(msg);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send email: " + safeMessage(e), e);
        }
    }

    private String buildSubject(EmailSettings settings, LocalDate day) {
        String prefix = EmailSettingsValidator.normalizeOptional(settings.getSubjectPrefix());
        if (prefix == null) {
            prefix = "Maestrao daily report";
        }
        return prefix + " - " + day;
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
