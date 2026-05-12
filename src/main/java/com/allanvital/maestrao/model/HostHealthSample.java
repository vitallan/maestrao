package com.allanvital.maestrao.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A point-in-time host health snapshot collected over SSH.
 */
@Entity
@Table(
        name = "host_health_samples",
        indexes = {
                @Index(name = "idx_host_health_host_collected", columnList = "host_id,collected_at")
        }
)
public class HostHealthSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private Host host;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    @Column(name = "load1", nullable = false)
    private Double load1;

    @Column(name = "load5", nullable = false)
    private Double load5;

    @Column(name = "load15", nullable = false)
    private Double load15;

    @Column(name = "mem_total_bytes", nullable = false)
    private Long memTotalBytes;

    @Column(name = "mem_available_bytes", nullable = false)
    private Long memAvailableBytes;

    @Column(name = "disk_root_total_bytes", nullable = false)
    private Long diskRootTotalBytes;

    @Column(name = "disk_root_available_bytes", nullable = false)
    private Long diskRootAvailableBytes;

    // Raw counters from /proc/stat (first line: "cpu ...")
    @Column(name = "cpu_user", nullable = false)
    private Long cpuUser;

    @Column(name = "cpu_nice", nullable = false)
    private Long cpuNice;

    @Column(name = "cpu_system", nullable = false)
    private Long cpuSystem;

    @Column(name = "cpu_idle", nullable = false)
    private Long cpuIdle;

    @Column(name = "cpu_iowait", nullable = false)
    private Long cpuIowait;

    @Column(name = "cpu_irq", nullable = false)
    private Long cpuIrq;

    @Column(name = "cpu_softirq", nullable = false)
    private Long cpuSoftirq;

    @Column(name = "cpu_steal", nullable = false)
    private Long cpuSteal;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Host getHost() {
        return host;
    }

    public void setHost(Host host) {
        this.host = host;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(Instant collectedAt) {
        this.collectedAt = collectedAt;
    }

    public Double getLoad1() {
        return load1;
    }

    public void setLoad1(Double load1) {
        this.load1 = load1;
    }

    public Double getLoad5() {
        return load5;
    }

    public void setLoad5(Double load5) {
        this.load5 = load5;
    }

    public Double getLoad15() {
        return load15;
    }

    public void setLoad15(Double load15) {
        this.load15 = load15;
    }

    public Long getMemTotalBytes() {
        return memTotalBytes;
    }

    public void setMemTotalBytes(Long memTotalBytes) {
        this.memTotalBytes = memTotalBytes;
    }

    public Long getMemAvailableBytes() {
        return memAvailableBytes;
    }

    public void setMemAvailableBytes(Long memAvailableBytes) {
        this.memAvailableBytes = memAvailableBytes;
    }

    public Long getDiskRootTotalBytes() {
        return diskRootTotalBytes;
    }

    public void setDiskRootTotalBytes(Long diskRootTotalBytes) {
        this.diskRootTotalBytes = diskRootTotalBytes;
    }

    public Long getDiskRootAvailableBytes() {
        return diskRootAvailableBytes;
    }

    public void setDiskRootAvailableBytes(Long diskRootAvailableBytes) {
        this.diskRootAvailableBytes = diskRootAvailableBytes;
    }

    public Long getCpuUser() {
        return cpuUser;
    }

    public void setCpuUser(Long cpuUser) {
        this.cpuUser = cpuUser;
    }

    public Long getCpuNice() {
        return cpuNice;
    }

    public void setCpuNice(Long cpuNice) {
        this.cpuNice = cpuNice;
    }

    public Long getCpuSystem() {
        return cpuSystem;
    }

    public void setCpuSystem(Long cpuSystem) {
        this.cpuSystem = cpuSystem;
    }

    public Long getCpuIdle() {
        return cpuIdle;
    }

    public void setCpuIdle(Long cpuIdle) {
        this.cpuIdle = cpuIdle;
    }

    public Long getCpuIowait() {
        return cpuIowait;
    }

    public void setCpuIowait(Long cpuIowait) {
        this.cpuIowait = cpuIowait;
    }

    public Long getCpuIrq() {
        return cpuIrq;
    }

    public void setCpuIrq(Long cpuIrq) {
        this.cpuIrq = cpuIrq;
    }

    public Long getCpuSoftirq() {
        return cpuSoftirq;
    }

    public void setCpuSoftirq(Long cpuSoftirq) {
        this.cpuSoftirq = cpuSoftirq;
    }

    public Long getCpuSteal() {
        return cpuSteal;
    }

    public void setCpuSteal(Long cpuSteal) {
        this.cpuSteal = cpuSteal;
    }
}
