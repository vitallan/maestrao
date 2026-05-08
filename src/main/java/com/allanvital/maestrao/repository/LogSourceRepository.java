package com.allanvital.maestrao.repository;

import com.allanvital.maestrao.model.LogSource;
import com.allanvital.maestrao.model.LogSourceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public interface LogSourceRepository extends JpaRepository<LogSource, Long> {

    @Override
    @EntityGraph(attributePaths = {"host"})
    Page<LogSource> findAll(Pageable pageable);

    List<LogSource> findByEnabledTrue();

    long countByEnabledTrue();

    long countByStatus(LogSourceStatus status);

    @EntityGraph(attributePaths = {"host", "host.credential"})
    java.util.List<LogSource> findByEnabledTrueOrderByIdAsc();

    @EntityGraph(attributePaths = {"host", "host.credential"})
    Optional<LogSource> findWithHostAndCredentialById(Long id);
}
