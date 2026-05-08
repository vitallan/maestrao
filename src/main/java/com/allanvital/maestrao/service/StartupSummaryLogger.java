package com.allanvital.maestrao.service;

import com.allanvital.maestrao.repository.HostRepository;
import com.allanvital.maestrao.repository.JobDefinitionRepository;
import com.allanvital.maestrao.repository.LogSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

@Component
public class StartupSummaryLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupSummaryLogger.class);

    private final HostRepository hostRepository;
    private final LogSourceRepository logSourceRepository;
    private final JobDefinitionRepository jobDefinitionRepository;
    private final DataSource dataSource;

    public StartupSummaryLogger(HostRepository hostRepository,
                                LogSourceRepository logSourceRepository,
                                JobDefinitionRepository jobDefinitionRepository,
                                DataSource dataSource) {
        this.hostRepository = hostRepository;
        this.logSourceRepository = logSourceRepository;
        this.jobDefinitionRepository = jobDefinitionRepository;
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logStartupSummary() {
        long hosts = hostRepository.count();
        long loggersEnabled = logSourceRepository.countByEnabledTrue();
        long jobs = jobDefinitionRepository.count();

        String driver = null;
        String user = null;
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            driver = safe(meta.getDriverName());
            user = safe(meta.getUserName());
        } catch (Exception e) {
            log.warn("startup.summary failedToReadDbMetadata reason={}", safe(e.getMessage()));
            log.debug("startup.summary failedToReadDbMetadataStack", e);
        }

        log.info("startup.summary hosts={} loggersEnabled={} jobs={} dbDriver=\"{}\" dbUser=\"{}\"",
                hosts, loggersEnabled, jobs, driver, user);
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        return v.length() > 200 ? v.substring(0, 200) : v;
    }
}
