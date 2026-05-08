package com.allanvital.maestrao.service.log;

import com.allanvital.maestrao.model.LogLine;
import com.allanvital.maestrao.model.LogSource;
import com.allanvital.maestrao.repository.LogLineRepository;
import com.allanvital.maestrao.repository.LogSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Service
public class LogIngestionService {

    private static final Logger log = LoggerFactory.getLogger(LogIngestionService.class);

    private final LogSourceRepository logSourceRepository;
    private final LogLineRepository logLineRepository;

    // Avoid per-line logging; counters are exported via heartbeat.
    private final AtomicLong appendedLinesSinceLastHeartbeat = new AtomicLong();

    public LogIngestionService(LogSourceRepository logSourceRepository, LogLineRepository logLineRepository) {
        this.logSourceRepository = logSourceRepository;
        this.logLineRepository = logLineRepository;
    }

    @Transactional
    public void appendLine(Long logSourceId, String line) {
        if (logSourceId == null) {
            throw new IllegalArgumentException("logSourceId is required");
        }
        if (line == null) {
            throw new IllegalArgumentException("line is required");
        }

        LogSource logSource = logSourceRepository.findById(logSourceId)
                .orElseThrow(() -> new IllegalArgumentException("LogSource not found: " + logSourceId));

        LogLine logLine = new LogLine();
        logLine.setLogSource(logSource);
        logLine.setIngestedAt(Instant.now());
        logLine.setLine(line);
        logLineRepository.save(logLine);
        appendedLinesSinceLastHeartbeat.incrementAndGet();
    }

    long drainAppendedLinesSinceLastHeartbeat() {
        long drained = appendedLinesSinceLastHeartbeat.getAndSet(0);
        if (drained < 0) {
            // Should never happen; protect against overflow bugs.
            log.debug("logIngestion.negativeCounter detected={}", drained);
            return 0;
        }
        return drained;
    }
}
