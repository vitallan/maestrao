package com.allanvital.maestrao.service.email;

import com.allanvital.maestrao.model.EmailSettings;
import com.allanvital.maestrao.repository.EmailSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class EmailSettingsService {

    private final EmailSettingsRepository repository;

    public EmailSettingsService(EmailSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public EmailSettings getOrCreate() {
        Optional<EmailSettings> existing = repository.findById(EmailSettings.SINGLETON_ID);
        if (existing.isPresent()) {
            return existing.get();
        }
        EmailSettings settings = new EmailSettings();
        settings.setId(EmailSettings.SINGLETON_ID);
        // Defaults
        settings.setEnabled(false);
        settings.setSmtpUseStartTls(true);
        settings.setSmtpUseSsl(false);
        // Default send time: 07:05 server time
        settings.setSendTime(java.time.LocalTime.of(7, 5));
        settings.setSubjectPrefix("Maestrao daily report");
        return repository.save(settings);
    }

    @Transactional
    public EmailSettings save(EmailSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("settings cannot be null");
        }
        settings.setId(EmailSettings.SINGLETON_ID);
        return repository.save(settings);
    }
}
