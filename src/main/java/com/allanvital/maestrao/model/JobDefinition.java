package com.allanvital.maestrao.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Entity
@Table(name = "job_definitions")
public class JobDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobShell shell;

    @Column(nullable = false)
    private boolean useSudo;

    @Lob
    @Column(nullable = false)
    private String content;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "job_definition_hosts",
            joinColumns = @JoinColumn(name = "job_definition_id"),
            inverseJoinColumns = @JoinColumn(name = "host_id")
    )
    private Set<Host> hosts = new HashSet<>();

    @Column(nullable = false)
    private boolean scheduleEnabled;

    @Column(length = 120)
    private String cron5;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public JobShell getShell() {
        return shell;
    }

    public void setShell(JobShell shell) {
        this.shell = shell;
    }

    public boolean isUseSudo() {
        return useSudo;
    }

    public void setUseSudo(boolean useSudo) {
        this.useSudo = useSudo;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Set<Host> getHosts() {
        return hosts;
    }

    public void setHosts(Set<Host> hosts) {
        this.hosts = hosts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isScheduleEnabled() {
        return scheduleEnabled;
    }

    public void setScheduleEnabled(boolean scheduleEnabled) {
        this.scheduleEnabled = scheduleEnabled;
    }

    public String getCron5() {
        return cron5;
    }

    public void setCron5(String cron5) {
        this.cron5 = cron5;
    }
}
