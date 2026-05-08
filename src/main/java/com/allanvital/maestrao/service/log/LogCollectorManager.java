package com.allanvital.maestrao.service.log;

import com.allanvital.maestrao.model.LogSource;
import com.allanvital.maestrao.model.LogSourceStatus;
import com.allanvital.maestrao.model.LogType;
import com.allanvital.maestrao.repository.LogSourceRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Service
public class LogCollectorManager {

    private static final Logger log = LoggerFactory.getLogger(LogCollectorManager.class);

    private final LogSourceRepository logSourceRepository;
    private final Map<LogType, LogCollectorRunnerFactory> factories;
    private final long reconnectDelayMillis;
    private final Sleeper sleeper;
    private final LogIngestionService logIngestionService;

    private final Map<Long, CollectorHandle> running = new ConcurrentHashMap<>();

    private final LongAdder startsSinceLastHeartbeat = new LongAdder();
    private final LongAdder stopsSinceLastHeartbeat = new LongAdder();
    private final LongAdder errorsSinceLastHeartbeat = new LongAdder();
    private final LongAdder unexpectedStopsSinceLastHeartbeat = new LongAdder();

    public LogCollectorManager(LogSourceRepository logSourceRepository,
                               List<LogCollectorRunnerFactory> factories,
                               @Value("${maestrao.logs.collector.reconnect-delay-ms:2000}") long reconnectDelayMillis,
                               Sleeper sleeper,
                               LogIngestionService logIngestionService) {
        this.logSourceRepository = logSourceRepository;
        this.reconnectDelayMillis = reconnectDelayMillis;
        this.sleeper = sleeper;
        this.logIngestionService = logIngestionService;

        Map<LogType, LogCollectorRunnerFactory> map = new ConcurrentHashMap<>();
        for (LogCollectorRunnerFactory factory : factories) {
            map.put(factory.type(), factory);
        }
        this.factories = map;
    }

    @PostConstruct
    public void startEnabledOnStartup() {
        List<LogSource> enabled = logSourceRepository.findByEnabledTrueOrderByIdAsc();
        log.info("collectors.startup enabledSources={}", enabled.size());
        for (LogSource source : enabled) {
            start(source.getId());
        }
    }

    public void start(Long logSourceId) {
        if (logSourceId == null) {
            return;
        }
        running.compute(logSourceId, (id, existing) -> {
            if (existing != null && existing.isRunning()) {
                return existing;
            }
            log.info("collectors.start id={}", id);
            startsSinceLastHeartbeat.increment();
            CollectorHandle handle = new CollectorHandle(id);
            Thread thread = new Thread(() -> runCollectorLoop(handle), "log-collector-" + id);
            handle.setThread(thread);
            thread.setDaemon(true);
            thread.start();
            return handle;
        });
    }

    public void stop(Long logSourceId) {
        if (logSourceId == null) {
            return;
        }
        CollectorHandle handle = running.remove(logSourceId);
        if (handle == null) {
            return;
        }
        log.info("collectors.stop id={} wasRunning={}", logSourceId, handle.isRunning());
        stopsSinceLastHeartbeat.increment();
        handle.stop();
    }

    @Scheduled(
            initialDelayString = "${maestrao.logs.collector.heartbeat-ms:300000}",
            fixedDelayString = "${maestrao.logs.collector.heartbeat-ms:300000}"
    )
    public void heartbeat() {
        long runningCount = running.size();
        long enabledCount = logSourceRepository.countByEnabledTrue();
        long errorCount = logSourceRepository.countByStatus(LogSourceStatus.ERROR);
        long appendedLines = logIngestionService.drainAppendedLinesSinceLastHeartbeat();

        long starts = startsSinceLastHeartbeat.sumThenReset();
        long stops = stopsSinceLastHeartbeat.sumThenReset();
        long errors = errorsSinceLastHeartbeat.sumThenReset();
        long unexpectedStops = unexpectedStopsSinceLastHeartbeat.sumThenReset();

        log.info("collectors.heartbeat running={} enabledSources={} errorSources={} linesAppended={} starts={} stops={} errors={} unexpectedStops={}",
                runningCount, enabledCount, errorCount, appendedLines, starts, stops, errors, unexpectedStops);
    }

    @PreDestroy
    public void shutdown() {
        for (Map.Entry<Long, CollectorHandle> entry : running.entrySet()) {
            entry.getValue().stop();
        }
        running.clear();
    }

    private void runCollectorLoop(CollectorHandle handle) {
        while (!handle.isStopped()) {
            LogSource source;
            try {
                source = logSourceRepository.findWithHostAndCredentialById(handle.getLogSourceId()).orElse(null);
            } catch (RuntimeException e) {
                return;
            }

            if (source == null) {
                return;
            }

            if (!source.isEnabled()) {
                updateStatus(source.getId(), LogSourceStatus.STOPPED, null);
                return;
            }

            LogCollectorRunnerFactory factory = factories.get(source.getType());
            if (factory == null) {
                updateStatus(source.getId(), LogSourceStatus.ERROR, "No collector registered for type: " + source.getType());
                log.warn("collectors.noFactory id={} type={}", source.getId(), source.getType());
                errorsSinceLastHeartbeat.increment();
                return;
            }

            try {
                updateStatus(source.getId(), LogSourceStatus.RUNNING, null);
                LogCollectorRunner runner = factory.create(source, handle);
                runner.run();
                // If runner exits without exception, treat as error and retry unless stopped/disabled.
                if (!handle.isStopped()) {
                    updateStatus(source.getId(), LogSourceStatus.ERROR, "Collector stopped unexpectedly");
                    log.warn("collectors.unexpectedStop id={} type={}", source.getId(), source.getType());
                    unexpectedStopsSinceLastHeartbeat.increment();
                }
            } catch (RuntimeException e) {
                if (handle.isStopped()) {
                    return;
                }
                updateStatus(source.getId(), LogSourceStatus.ERROR, safeMessage(e));
                log.warn("collectors.error id={} type={} message={}", source.getId(), source.getType(), safeMessage(e));
                log.debug("collectors.errorStack id={}", source.getId(), e);
                errorsSinceLastHeartbeat.increment();
            }

            if (handle.isStopped()) {
                return;
            }

            try {
                sleeper.sleep(Math.max(0, reconnectDelayMillis));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    void updateStatus(Long id, LogSourceStatus status, String lastError) {
        LogSource source = logSourceRepository.findById(id).orElse(null);
        if (source == null) {
            return;
        }
        source.setStatus(status);
        source.setLastError(lastError);
        logSourceRepository.save(source);
    }

    private String safeMessage(Throwable t) {
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            return t.getClass().getSimpleName();
        }
        return msg;
    }

    public interface LogCollectorRunner {
        void run();
    }

    public interface CollectorControl {
        boolean isStopped();

        void requestStop();

        void setResource(AutoCloseable resource);
    }

    private static class CollectorHandle implements CollectorControl {
        private final Long logSourceId;
        private volatile boolean stopped;
        private volatile Thread thread;
        private volatile AutoCloseable resource;

        private CollectorHandle(Long logSourceId) {
            this.logSourceId = logSourceId;
        }

        public Long getLogSourceId() {
            return logSourceId;
        }

        public boolean isRunning() {
            Thread t = thread;
            return t != null && t.isAlive() && !stopped;
        }

        public void setThread(Thread thread) {
            this.thread = thread;
        }

        @Override
        public boolean isStopped() {
            return stopped;
        }

        @Override
        public void requestStop() {
            stop();
        }

        @Override
        public void setResource(AutoCloseable resource) {
            this.resource = resource;
        }

        public void stop() {
            stopped = true;
            AutoCloseable r = resource;
            if (r != null) {
                try {
                    r.close();
                } catch (Exception ignored) {
                }
            }
            Thread t = thread;
            if (t != null) {
                t.interrupt();
            }
        }
    }
}
