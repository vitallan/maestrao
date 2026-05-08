package com.allanvital.maestrao.repository;

import com.allanvital.maestrao.model.JobDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public interface JobDefinitionRepository extends JpaRepository<JobDefinition, Long> {

    @EntityGraph(attributePaths = {"hosts"})
    Optional<JobDefinition> findWithHostsById(Long id);

    @Query("""
            select new com.allanvital.maestrao.repository.JobDefinitionListRow(
                j.id,
                j.name,
                j.shell,
                j.useSudo,
                j.updatedAt,
                count(h)
            )
            from JobDefinition j
            left join j.hosts h
            group by j.id, j.name, j.shell, j.useSudo, j.updatedAt
            """)
    Page<JobDefinitionListRow> findAllWithHostCount(Pageable pageable);
}
