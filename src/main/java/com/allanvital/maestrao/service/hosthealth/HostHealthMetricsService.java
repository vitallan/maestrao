package com.allanvital.maestrao.service.hosthealth;

import com.allanvital.maestrao.model.Host;
import com.allanvital.maestrao.model.HostHealthSample;
import com.allanvital.maestrao.repository.HostHealthSampleRepository;
import com.allanvital.maestrao.repository.HostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class HostHealthMetricsService {

    private static final Logger log = LoggerFactory.getLogger(HostHealthMetricsService.class);

    private final HostRepository hostRepository;
    private final HostHealthSampleRepository sampleRepository;

    public HostHealthMetricsService(HostRepository hostRepository, HostHealthSampleRepository sampleRepository) {
        this.hostRepository = hostRepository;
        this.sampleRepository = sampleRepository;
    }

    @Transactional(readOnly = true)
    public List<Host> findEnabledHostsForSelection() {
        return hostRepository.findByGatherHealthMetricsTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public HostHealthSeries getSeries(Long hostId, HostHealthWindow window) {
        if (hostId == null) {
            throw new IllegalArgumentException("hostId is required");
        }
        HostHealthWindow w = window == null ? HostHealthWindow.M15 : window;
        Instant from = w.since();
        List<HostHealthSample> samples = sampleRepository.findRecent(hostId, from);
        log.debug("hostMetrics.read hostId={} window={} samples={}", hostId, w.name(), samples.size());

        List<Point> points = new ArrayList<>(samples.size());
        HostHealthSample prev = null;
        for (HostHealthSample s : samples) {
            double cpuUsedPct = prev == null ? Double.NaN : computeCpuUsedPct(prev, s);
            double memUsedPct = computeUsedPct(s.getMemTotalBytes(), s.getMemAvailableBytes());
            double diskUsedPct = computeUsedPct(s.getDiskRootTotalBytes(), s.getDiskRootAvailableBytes());

            points.add(new Point(
                    s.getCollectedAt(),
                    s.getLoad1(), s.getLoad5(), s.getLoad15(),
                    cpuUsedPct,
                    memUsedPct,
                    diskUsedPct
            ));
            prev = s;
        }
        return new HostHealthSeries(points);
    }

    private double computeUsedPct(Long total, Long available) {
        if (total == null || available == null || total <= 0) {
            return Double.NaN;
        }
        long used = Math.max(0L, total - Math.max(0L, available));
        return (used * 100.0) / total;
    }

    private double computeCpuUsedPct(HostHealthSample prev, HostHealthSample cur) {
        long prevIdle = safe(prev.getCpuIdle()) + safe(prev.getCpuIowait());
        long curIdle = safe(cur.getCpuIdle()) + safe(cur.getCpuIowait());

        long prevNonIdle = safe(prev.getCpuUser()) + safe(prev.getCpuNice()) + safe(prev.getCpuSystem())
                + safe(prev.getCpuIrq()) + safe(prev.getCpuSoftirq()) + safe(prev.getCpuSteal());
        long curNonIdle = safe(cur.getCpuUser()) + safe(cur.getCpuNice()) + safe(cur.getCpuSystem())
                + safe(cur.getCpuIrq()) + safe(cur.getCpuSoftirq()) + safe(cur.getCpuSteal());

        long prevTotal = prevIdle + prevNonIdle;
        long curTotal = curIdle + curNonIdle;

        long totalDelta = curTotal - prevTotal;
        long idleDelta = curIdle - prevIdle;
        if (totalDelta <= 0) {
            return Double.NaN;
        }
        double used = (totalDelta - idleDelta) * 100.0 / totalDelta;
        if (used < 0) {
            return 0;
        }
        if (used > 100) {
            return 100;
        }
        return used;
    }

    private long safe(Long v) {
        return v == null ? 0L : v;
    }

    public record Point(
            Instant at,
            double load1,
            double load5,
            double load15,
            double cpuUsedPct,
            double memUsedPct,
            double diskUsedPct
    ) {
    }

    public record HostHealthSeries(List<Point> points) {
        public Point latest() {
            if (points == null || points.isEmpty()) {
                return null;
            }
            return points.get(points.size() - 1);
        }
    }
}
