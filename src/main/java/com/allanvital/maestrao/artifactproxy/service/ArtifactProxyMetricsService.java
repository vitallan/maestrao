package com.allanvital.maestrao.artifactproxy.service;

import com.allanvital.maestrao.artifactproxy.model.ArtifactCacheEntry;
import com.allanvital.maestrao.artifactproxy.model.ArtifactCacheStatus;
import com.allanvital.maestrao.artifactproxy.repository.ArtifactCacheEntryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "maestrao.artifact-proxy", name = "enabled", havingValue = "true")
public class ArtifactProxyMetricsService {

    private final ArtifactCacheEntryRepository cacheEntryRepository;

    public ArtifactProxyMetricsService(ArtifactCacheEntryRepository cacheEntryRepository) {
        this.cacheEntryRepository = cacheEntryRepository;
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot() {
        List<ArtifactCacheEntry> entries = cacheEntryRepository.findAll();
        long files = entries.stream().filter(e -> e.getStatus() == ArtifactCacheStatus.READY).count();
        long misses = entries.stream().filter(e -> e.getStatus() == ArtifactCacheStatus.NEGATIVE).count();
        long size = entries.stream().filter(e -> e.getStatus() == ArtifactCacheStatus.READY).mapToLong(e -> e.getSizeBytes() == null ? 0 : e.getSizeBytes()).sum();
        long hits = entries.stream().filter(e -> e.getStatus() == ArtifactCacheStatus.READY).mapToLong(e -> e.getHitCount() == null ? 0 : e.getHitCount()).sum();
        return new Snapshot(files, size, hits, misses);
    }

    public record Snapshot(long fileCount, long totalSizeBytes, long hitCount, long negativeCount) {
        public double hitRatioPercent() {
            long total = hitCount + negativeCount;
            if (total <= 0) {
                return 100.0;
            }
            return (hitCount * 100.0) / total;
        }
    }
}
