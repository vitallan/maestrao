package com.allanvital.maestrao.service.log;

import com.allanvital.maestrao.model.*;
import com.allanvital.maestrao.repository.HostRepository;
import com.allanvital.maestrao.repository.LogSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Service
public class LogSourceService {

    private static final Logger log = LoggerFactory.getLogger(LogSourceService.class);

    private final LogSourceRepository logSourceRepository;
    private final HostRepository hostRepository;
    private final LogCollectorManager logCollectorManager;

    public LogSourceService(LogSourceRepository logSourceRepository,
                            HostRepository hostRepository,
                            LogCollectorManager logCollectorManager) {
        this.logSourceRepository = logSourceRepository;
        this.hostRepository = hostRepository;
        this.logCollectorManager = logCollectorManager;
    }

    @Transactional(readOnly = true)
    public Page<LogSource> findAll(Pageable pageable) {
        return logSourceRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public long count() {
        return logSourceRepository.count();
    }

    @Transactional
    public LogSource createLogFile(String name, Long hostId, String filePath, boolean enabled) {
        LogSource logSource = new LogSource();
        logSource.setName(normalizeRequired(name, "name"));
        logSource.setType(LogType.LOG_FILE);
        logSource.setHost(findHost(hostId));
        logSource.setFilePath(normalizeRequired(filePath, "file path"));
        logSource.setEnabled(enabled);
        logSource.setStatus(LogSourceStatus.STOPPED);

        LogSource saved = logSourceRepository.save(logSource);
        log.info("logSource.create id={} name={} hostId={} enabled={} type={}",
                saved.getId(), saved.getName(), saved.getHost() == null ? null : saved.getHost().getId(),
                saved.isEnabled(), saved.getType());
        if (saved.isEnabled()) {
            runAfterCommit(() -> logCollectorManager.start(saved.getId()));
        }
        return saved;
    }

    @Transactional
    public void enable(Long id) {
        LogSource logSource = find(id);
        if (!logSource.isEnabled()) {
            logSource.setEnabled(true);
            logSourceRepository.save(logSource);
        }
        log.info("logSource.enable id={}", id);
        runAfterCommit(() -> logCollectorManager.start(id));
    }

    @Transactional
    public void disable(Long id) {
        LogSource logSource = find(id);
        logSource.setEnabled(false);
        logSource.setStatus(LogSourceStatus.STOPPED);
        logSource.setLastError(null);
        logSourceRepository.save(logSource);
        // Stop immediately; the collector loop also checks enabled state on restart.
        logCollectorManager.stop(id);
        log.info("logSource.disable id={}", id);
    }

    @Transactional
    public void updateFilePath(Long id, String filePath) {
        LogSource logSource = find(id);

        String normalizedPath = normalizeRequired(filePath, "file path");
        boolean shouldRestart = logSource.isEnabled() && !normalizedPath.equals(logSource.getFilePath());

        if (shouldRestart) {
            logCollectorManager.stop(id);
        }

        logSource.setFilePath(normalizedPath);
        logSourceRepository.save(logSource);

        if (shouldRestart) {
            runAfterCommit(() -> logCollectorManager.start(id));
        }

        log.info("logSource.updatePath id={} restarted={}", id, shouldRestart);
    }

    @Transactional
    public void delete(Long id) {
        logCollectorManager.stop(id);
        logSourceRepository.deleteById(id);
        log.info("logSource.delete id={}", id);
    }

    @Transactional
    public void updateStatus(Long id, LogSourceStatus status, String lastError) {
        LogSource logSource = find(id);
        logSource.setStatus(status);
        logSource.setLastError(lastError);
        logSourceRepository.save(logSource);

        if (log.isDebugEnabled()) {
            log.debug("logSource.status id={} status={} err={}", id, status, summarizeError(lastError));
        }
    }

    private String summarizeError(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        if (v.length() > 200) {
            return v.substring(0, 200);
        }
        return v;
    }

    @Transactional(readOnly = true)
    public LogSource find(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        return logSourceRepository.findWithHostAndCredentialById(id)
                .orElseThrow(() -> new IllegalArgumentException("LogSource not found: " + id));
    }

    private Host findHost(Long hostId) {
        if (hostId == null) {
            throw new IllegalArgumentException("host is required");
        }
        return hostRepository.findById(hostId)
                .orElseThrow(() -> new IllegalArgumentException("Host not found: " + hostId));
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
