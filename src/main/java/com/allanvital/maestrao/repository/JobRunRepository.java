package com.allanvital.maestrao.repository;

import com.allanvital.maestrao.model.JobRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public interface JobRunRepository extends JpaRepository<JobRun, Long> {

    @EntityGraph(attributePaths = {"jobDefinition"})
    Page<JobRun> findAllByJobDefinitionIdOrderByIdDesc(Long jobDefinitionId, Pageable pageable);

    long countByJobDefinitionId(Long jobDefinitionId);

    @Query("""
            select r
            from JobRun r
            where r.jobDefinition.id in :jobDefinitionIds
              and r.id in (
                select max(r2.id)
                from JobRun r2
                where r2.jobDefinition.id in :jobDefinitionIds
                group by r2.jobDefinition.id
              )
            """)
    List<JobRun> findLatestRunsByJobDefinitionIds(@Param("jobDefinitionIds") List<Long> jobDefinitionIds);
}
