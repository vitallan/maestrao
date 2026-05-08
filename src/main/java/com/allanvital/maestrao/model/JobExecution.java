package com.allanvital.maestrao.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Entity
@Table(name = "job_executions")
public class JobExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_run_id", nullable = false)
    private JobRun jobRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private Host host;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobExecutionStatus status;

    private Instant startedAt;
    private Instant finishedAt;

    private Integer exitCode;

    @Lob
    private String stdout;

    @Lob
    private String stderr;

    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false)
    private boolean truncatedStdout;

    @Column(nullable = false)
    private boolean truncatedStderr;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public JobRun getJobRun() {
        return jobRun;
    }

    public void setJobRun(JobRun jobRun) {
        this.jobRun = jobRun;
    }

    public Host getHost() {
        return host;
    }

    public void setHost(Host host) {
        this.host = host;
    }

    public JobExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(JobExecutionStatus status) {
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

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean isTruncatedStdout() {
        return truncatedStdout;
    }

    public void setTruncatedStdout(boolean truncatedStdout) {
        this.truncatedStdout = truncatedStdout;
    }

    public boolean isTruncatedStderr() {
        return truncatedStderr;
    }

    public void setTruncatedStderr(boolean truncatedStderr) {
        this.truncatedStderr = truncatedStderr;
    }
}
