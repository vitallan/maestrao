package com.allanvital.maestrao.view.individual;

import com.allanvital.maestrao.model.EmailSettings;
import com.allanvital.maestrao.security.CredentialCryptoService;
import com.allanvital.maestrao.service.email.DailyEmailReportService;
import com.allanvital.maestrao.service.email.EmailSettingsService;
import com.allanvital.maestrao.service.email.EmailSettingsValidator;
import com.allanvital.maestrao.service.email.schedule.EmailReportScheduleService;
import com.allanvital.maestrao.view.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Route(value = "email", layout = MainLayout.class)
@PageTitle("Email | Maestrao")
@PermitAll
public class EmailView extends VerticalLayout {

    private final EmailSettingsService settingsService;
    private final EmailReportScheduleService scheduleService;
    private final DailyEmailReportService reportService;
    private final CredentialCryptoService crypto;

    private EmailSettings settings;

    public EmailView(EmailSettingsService settingsService,
                     EmailReportScheduleService scheduleService,
                     DailyEmailReportService reportService,
                     CredentialCryptoService crypto) {
        this.settingsService = settingsService;
        this.scheduleService = scheduleService;
        this.reportService = reportService;
        this.crypto = crypto;

        setWidthFull();
        setMaxWidth("980px");

        add(new H1("Email"));
        add(new Paragraph("Configure SMTP and the daily report email (yesterday, server timezone)."));

        this.settings = settingsService.getOrCreate();

        render();
    }

    private void render() {
        removeAll();

        add(new H1("Email"));
        add(new Paragraph("Configure SMTP and the daily report email (yesterday, server timezone)."));

        Checkbox enabled = new Checkbox("Enabled");
        enabled.setValue(settings.isEnabled());

        TextField smtpHost = new TextField("SMTP host");
        smtpHost.setWidthFull();
        smtpHost.setValue(defaultString(settings.getSmtpHost()));

        IntegerField smtpPort = new IntegerField("SMTP port");
        smtpPort.setMin(1);
        smtpPort.setMax(65535);
        smtpPort.setValue(settings.getSmtpPort() == null ? 587 : settings.getSmtpPort());

        TextField smtpUsername = new TextField("SMTP username");
        smtpUsername.setWidthFull();
        smtpUsername.setValue(defaultString(settings.getSmtpUsername()));

        PasswordField smtpPassword = new PasswordField("SMTP password");
        smtpPassword.setWidthFull();
        smtpPassword.setPlaceholder(settings.getSmtpEncryptedPassword() == null ? "" : "(stored)");
        smtpPassword.setRevealButtonVisible(true);

        Checkbox clearPassword = new Checkbox("Clear stored password");
        clearPassword.setValue(false);

        Checkbox startTls = new Checkbox("Use STARTTLS");
        startTls.setValue(settings.isSmtpUseStartTls());

        Checkbox ssl = new Checkbox("Use SSL");
        ssl.setValue(settings.isSmtpUseSsl());

        TextField from = new TextField("From address");
        from.setWidthFull();
        from.setValue(defaultString(settings.getFromAddress()));

        TextArea to = new TextArea("To addresses (comma-separated)");
        to.setWidthFull();
        to.setValue(defaultString(settings.getToAddresses()));

        TextField subjectPrefix = new TextField("Subject prefix");
        subjectPrefix.setWidthFull();
        subjectPrefix.setValue(defaultString(settings.getSubjectPrefix()));

        TimePicker sendTime = new TimePicker("Daily send time (server timezone)");
        sendTime.setStep(java.time.Duration.ofMinutes(5));
        sendTime.setValue(settings.getSendTime());

        DateTimePicker lastSent = new DateTimePicker("Last sent");
        lastSent.setReadOnly(true);
        lastSent.setWidthFull();
        lastSent.setValue(toLocalDateTime(settings.getLastSentAt()));

        TextArea lastError = new TextArea("Last error");
        lastError.setReadOnly(true);
        lastError.setWidthFull();
        lastError.setValue(defaultString(settings.getLastError()));

        FormLayout form = new FormLayout();
        form.setWidthFull();
        form.add(enabled);
        form.add(smtpHost, smtpPort);
        form.add(smtpUsername, smtpPassword);
        form.add(clearPassword);
        form.add(startTls, ssl);
        form.add(from);
        form.add(to);
        form.add(subjectPrefix);
        form.add(sendTime);
        form.add(lastSent);
        form.add(lastError);

        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("720px", 2)
        );
        form.setColspan(enabled, 2);
        form.setColspan(clearPassword, 2);
        form.setColspan(from, 2);
        form.setColspan(to, 2);
        form.setColspan(subjectPrefix, 2);
        form.setColspan(sendTime, 2);
        form.setColspan(lastSent, 2);
        form.setColspan(lastError, 2);

        Button save = new Button("Save", event -> {
            try {
                EmailSettings updated = settingsService.getOrCreate();
                updated.setEnabled(enabled.getValue());
                updated.setSmtpHost(trimToNull(smtpHost.getValue()));
                updated.setSmtpPort(smtpPort.getValue());
                updated.setSmtpUsername(trimToNull(smtpUsername.getValue()));
                updated.setSmtpUseStartTls(startTls.getValue());
                updated.setSmtpUseSsl(ssl.getValue());
                updated.setFromAddress(trimToNull(from.getValue()));
                updated.setToAddresses(trimToNull(to.getValue()));
                updated.setSubjectPrefix(trimToNull(subjectPrefix.getValue()));
                updated.setSendTime(sendTime.getValue());

                if (clearPassword.getValue()) {
                    updated.setSmtpEncryptedPassword(null);
                } else {
                    String pw = trimToNull(smtpPassword.getValue());
                    if (pw != null) {
                        updated.setSmtpEncryptedPassword(crypto.encrypt(pw));
                    }
                }

                EmailSettingsValidator.ValidationResult validation = EmailSettingsValidator.validate(updated);
                if (updated.isEnabled() && !validation.ok()) {
                    updated.setLastError(validation.message());
                } else if (!updated.isEnabled()) {
                    updated.setLastError(null);
                }

                settingsService.save(updated);
                scheduleService.applyOrUnscheduleFromDb();

                this.settings = updated;
                showSuccess("Saved");
                render();
            } catch (RuntimeException e) {
                showError(safeMessage(e));
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button test = new Button("Send test email", event -> {
            try {
                reportService.sendTestEmail();
                showSuccess("Test email sent");
            } catch (RuntimeException e) {
                showError(safeMessage(e));
            }
        });

        Button sendNow = new Button("Send yesterday report now", event -> {
            try {
                reportService.sendYesterdayReport();
                this.settings = settingsService.getOrCreate();
                showSuccess("Report send triggered");
                render();
            } catch (RuntimeException e) {
                showError(safeMessage(e));
            }
        });

        HorizontalLayout actions = new HorizontalLayout(save, test, sendNow);
        actions.setAlignItems(Alignment.BASELINE);

        add(form, actions);
    }

    private String defaultString(String v) {
        return v == null ? "" : v;
    }

    private LocalDateTime toLocalDateTime(Instant v) {
        if (v == null) {
            return null;
        }
        return LocalDateTime.ofInstant(v, ZoneId.systemDefault());
    }

    private void showSuccess(String message) {
        Notification n = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String message) {
        Notification n = Notification.show(message, 6000, Notification.Position.TOP_CENTER);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
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
