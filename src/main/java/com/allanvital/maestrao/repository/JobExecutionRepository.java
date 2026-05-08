package com.allanvital.maestrao.repository;

import com.allanvital.maestrao.model.JobExecution;
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
public interface JobExecutionRepository extends JpaRepository<JobExecution, Long> {

    @EntityGraph(attributePaths = {"host"})
    List<JobExecution> findAllByJobRunIdOrderByIdAsc(Long jobRunId);

    @EntityGraph(attributePaths = {"host"})
    Page<JobExecution> findAllByJobRunIdOrderByIdAsc(Long jobRunId, Pageable pageable);

    long countByJobRunId(Long jobRunId);

    @Query("""
            select new com.allanvital.maestrao.repository.JobRunExecutionCountRow(
                e.jobRun.id,
                count(e)
            )
            from JobExecution e
            where e.jobRun.id in :runIds
            group by e.jobRun.id
            """)
    List<JobRunExecutionCountRow> countExecutionsByRunIds(@Param("runIds") List<Long> runIds);

    @Query("""
            select new com.allanvital.maestrao.repository.JobFailedExecutionRow(
                e.id,
                e.jobRun.id,
                h.name,
                e.status,
                e.exitCode,
                e.errorMessage
            )
            from JobExecution e
            join e.host h
            where e.jobRun.id in :runIds
              and e.status in :statuses
            order by e.id asc
            """)
    List<JobFailedExecutionRow> findFailedExecutionsByRunIds(
            @Param("runIds") List<Long> runIds,
            @Param("statuses") List<com.allanvital.maestrao.model.JobExecutionStatus> statuses);
}
