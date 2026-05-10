package com.allanvital.maestrao.repository;

import com.allanvital.maestrao.model.EmailSettings;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Stores a single EmailSettings row.
 */
public interface EmailSettingsRepository extends JpaRepository<EmailSettings, Long> {
}
