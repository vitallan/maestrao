package com.allanvital.maestrao.service.hosthealth;

import com.allanvital.maestrao.repository.HostHealthSampleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class HostHealthRetentionService {

    private static final Logger log = LoggerFactory.getLogger(HostHealthRetentionService.class);

    private final HostHealthSampleRepository sampleRepository;
    private final long retentionDays;

    public HostHealthRetentionService(HostHealthSampleRepository sampleRepository,
                                     @Value("${maestrao.host-metrics.retention-days:7}") long retentionDays) {
        this.sampleRepository = sampleRepository;
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Scheduled(cron = "0 30 2 * * *")
    @Transactional
    public void cleanup() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = sampleRepository.deleteOlderThan(cutoff);
        log.info("hostMetrics.retention.cleanup deleted={} cutoff={}", deleted, cutoff);
    }
}
