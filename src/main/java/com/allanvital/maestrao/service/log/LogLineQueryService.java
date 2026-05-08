package com.allanvital.maestrao.service.log;

import com.allanvital.maestrao.model.LogLine;
import com.allanvital.maestrao.repository.LogLineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Service
public class LogLineQueryService {

    public record LatestLogLine(Instant ingestedAt, String line) {
    }

    private final LogLineRepository logLineRepository;

    public LogLineQueryService(LogLineRepository logLineRepository) {
        this.logLineRepository = logLineRepository;
    }

    @Transactional(readOnly = true)
    public LatestLogLine findLatest(Long logSourceId) {
        if (logSourceId == null) {
            throw new IllegalArgumentException("logSourceId is required");
        }

        return logLineRepository.findTopByLogSourceIdOrderByIdDesc(logSourceId)
                .map(line -> new LatestLogLine(line.getIngestedAt(), line.getLine()))
                .orElse(null);
    }
}
