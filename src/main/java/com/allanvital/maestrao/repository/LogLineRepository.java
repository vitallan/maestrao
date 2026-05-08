package com.allanvital.maestrao.repository;

import com.allanvital.maestrao.model.LogLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public interface LogLineRepository extends JpaRepository<LogLine, Long>, LogLineRepositoryCustom {

    Optional<LogLine> findTopByLogSourceIdOrderByIdDesc(Long logSourceId);

    @Modifying
    @Query("delete from LogLine l where l.ingestedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
