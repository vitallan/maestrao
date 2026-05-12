package com.allanvital.maestrao.repository;

import com.allanvital.maestrao.model.HostHealthSample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface HostHealthSampleRepository extends JpaRepository<HostHealthSample, Long> {

    @Query("""
            select s
            from HostHealthSample s
            where s.host.id = :hostId
              and s.collectedAt >= :from
            order by s.collectedAt asc
            """)
    List<HostHealthSample> findRecent(@Param("hostId") Long hostId, @Param("from") Instant from);

    @Modifying
    @Query("delete from HostHealthSample s where s.collectedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
