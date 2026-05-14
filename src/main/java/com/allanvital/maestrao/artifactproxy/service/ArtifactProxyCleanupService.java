package com.allanvital.maestrao.artifactproxy.service;

import com.allanvital.maestrao.artifactproxy.config.ArtifactProxyProperties;
import com.allanvital.maestrao.artifactproxy.model.ArtifactCacheEntry;
import com.allanvital.maestrao.artifactproxy.model.ArtifactCacheStatus;
import com.allanvital.maestrao.artifactproxy.repository.ArtifactCacheEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "maestrao.artifact-proxy", name = "enabled", havingValue = "true")
public class ArtifactProxyCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactProxyCleanupService.class);

    private final ArtifactProxyProperties properties;
    private final ArtifactCacheEntryRepository cacheEntryRepository;
    private final ArtifactCacheService cacheService;

    public ArtifactProxyCleanupService(ArtifactProxyProperties properties,
                                       ArtifactCacheEntryRepository cacheEntryRepository,
                                       ArtifactCacheService cacheService) {
        this.properties = properties;
        this.cacheEntryRepository = cacheEntryRepository;
        this.cacheService = cacheService;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void cleanup() {
        if (!properties.getCleanup().isEnabled()) {
            return;
        }
        cacheService.cleanupExpiredNegativeEntries();

        Instant cutoff = Instant.now().minus(properties.getCleanup().getMaxIdleDays(), ChronoUnit.DAYS);
        List<ArtifactCacheEntry> old = cacheEntryRepository.findAll().stream()
                .filter(e -> e.getStatus() == ArtifactCacheStatus.READY)
                .filter(e -> e.getLastAccessAt() != null && e.getLastAccessAt().isBefore(cutoff))
                .toList();
        for (ArtifactCacheEntry entry : old) {
            try {
                cacheService.purgePath(entry.getArtifactPath());
            } catch (Exception ignored) {
            }
        }

        long maxBytes = Math.max(1, properties.getCleanup().getMaxSizeGb()) * 1024L * 1024L * 1024L;
        List<ArtifactCacheEntry> ready = cacheEntryRepository.findAll().stream()
                .filter(e -> e.getStatus() == ArtifactCacheStatus.READY)
                .sorted(Comparator.comparing(ArtifactCacheEntry::getLastAccessAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        long total = ready.stream().mapToLong(e -> e.getSizeBytes() == null ? 0 : e.getSizeBytes()).sum();
        int removed = 0;
        for (ArtifactCacheEntry entry : ready) {
            if (total <= maxBytes) {
                break;
            }
            long size = entry.getSizeBytes() == null ? 0 : entry.getSizeBytes();
            try {
                cacheService.purgePath(entry.getArtifactPath());
                total -= size;
                removed++;
            } catch (Exception ignored) {
            }
        }
        if (removed > 0 || !old.isEmpty()) {
            log.info("artifactProxy.cleanup removedByAge={} removedBySize={}", old.size(), removed);
        }
    }
}
