package com.allanvital.maestrao.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Entity
@Table(
        name = "log_lines",
        indexes = {
                @Index(name = "idx_log_lines_ingested_at", columnList = "ingested_at")
        }
)
public class LogLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "log_source_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private LogSource logSource;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String line;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LogSource getLogSource() {
        return logSource;
    }

    public void setLogSource(LogSource logSource) {
        this.logSource = logSource;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }

    public void setIngestedAt(Instant ingestedAt) {
        this.ingestedAt = ingestedAt;
    }

    public String getLine() {
        return line;
    }

    public void setLine(String line) {
        this.line = line;
    }
}
