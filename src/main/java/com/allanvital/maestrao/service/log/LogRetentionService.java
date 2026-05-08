package com.allanvital.maestrao.service.log;

import com.allanvital.maestrao.repository.LogLineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Service
public class LogRetentionService {

    private static final Logger log = LoggerFactory.getLogger(LogRetentionService.class);

    private final LogLineRepository logLineRepository;
    private final int retentionDays;

    public LogRetentionService(LogLineRepository logLineRepository,
                               @Value("${maestrao.logs.retention-days:7}") int retentionDays) {
        this.logLineRepository = logLineRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldLines() {
        if (retentionDays <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        log.info("retention.cleanup start retentionDays={} cutoff={}", retentionDays, cutoff);
        logLineRepository.deleteOlderThan(cutoff);
        log.info("retention.cleanup done cutoff={}", cutoff);
    }
}
