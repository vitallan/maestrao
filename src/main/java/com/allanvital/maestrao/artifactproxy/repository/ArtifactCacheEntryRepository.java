package com.allanvital.maestrao.artifactproxy.repository;

import com.allanvital.maestrao.artifactproxy.model.ArtifactCacheEntry;
import com.allanvital.maestrao.artifactproxy.model.ArtifactCacheStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ArtifactCacheEntryRepository extends JpaRepository<ArtifactCacheEntry, Long> {
    Optional<ArtifactCacheEntry> findByArtifactPath(String artifactPath);

    List<ArtifactCacheEntry> findByStatusAndNegativeTtlUntilBefore(ArtifactCacheStatus status, Instant threshold);

    @Modifying
    @Query("delete from ArtifactCacheEntry e where e.status = :status and e.negativeTtlUntil < :threshold")
    int deleteExpiredNegativeEntries(ArtifactCacheStatus status, Instant threshold);
}
