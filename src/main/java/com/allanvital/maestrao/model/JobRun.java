package com.allanvital.maestrao.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Entity
@Table(name = "job_runs")
public class JobRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_definition_id", nullable = false)
    private JobDefinition jobDefinition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobRunStatus status;

    @Column(nullable = false)
    private boolean abortRequested;

    @Column(nullable = false, updatable = false)
    private Instant startedAt;

    private Instant finishedAt;

    @PrePersist
    void prePersist() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public JobDefinition getJobDefinition() {
        return jobDefinition;
    }

    public void setJobDefinition(JobDefinition jobDefinition) {
        this.jobDefinition = jobDefinition;
    }

    public JobRunStatus getStatus() {
        return status;
    }

    public void setStatus(JobRunStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public boolean isAbortRequested() {
        return abortRequested;
    }

    public void setAbortRequested(boolean abortRequested) {
        this.abortRequested = abortRequested;
    }
}
