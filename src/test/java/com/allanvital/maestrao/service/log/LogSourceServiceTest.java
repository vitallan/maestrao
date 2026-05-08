package com.allanvital.maestrao.service.log;

import com.allanvital.maestrao.TestConfig;
import com.allanvital.maestrao.model.Credential;
import com.allanvital.maestrao.model.CredentialType;
import com.allanvital.maestrao.model.Host;
import com.allanvital.maestrao.model.LogLine;
import com.allanvital.maestrao.repository.LogLineRepository;
import com.allanvital.maestrao.repository.LogSourceRepository;
import com.allanvital.maestrao.service.CredentialService;
import com.allanvital.maestrao.service.HostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestConfig.class)
@Transactional
class LogSourceServiceTest {

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private HostService hostService;

    @Autowired
    private LogSourceService logSourceService;

    @Autowired
    private LogSourceRepository logSourceRepository;

    @Autowired
    private LogLineRepository logLineRepository;

    @BeforeEach
    void setUp() {
        logLineRepository.deleteAll();
        logLineRepository.flush();

        logSourceRepository.deleteAll();
        logSourceRepository.flush();
    }

    @Test
    void shouldCreateDisabledLogFileSource() {
        Host host = createHost();

        var created = logSourceService.createLogFile("  App log  ", host.getId(), "  /var/log/app.log  ", false);
        assertNotNull(created.getId());
        assertEquals("App log", created.getName());
        assertEquals("/var/log/app.log", created.getFilePath());
        assertFalse(created.isEnabled());
        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getUpdatedAt());
    }

    @Test
    void shouldRejectInvalidCreateArguments() {
        Host host = createHost();

        assertThrows(IllegalArgumentException.class, () ->
                logSourceService.createLogFile(" ", host.getId(), "/var/log/app.log", false)
        );

        assertThrows(IllegalArgumentException.class, () ->
                logSourceService.createLogFile("App", null, "/var/log/app.log", false)
        );

        assertThrows(IllegalArgumentException.class, () ->
                logSourceService.createLogFile("App", host.getId(), "   ", false)
        );
    }

    @Test
    void shouldDeleteLogSourceAndCascadeDeleteLines() {
        Host host = createHost();
        var source = logSourceService.createLogFile("App", host.getId(), "/var/log/app.log", false);

        LogLine line = new LogLine();
        line.setLogSource(source);
        line.setIngestedAt(Instant.now());
        line.setLine("hello");
        logLineRepository.saveAndFlush(line);

        assertEquals(1, logLineRepository.count());

        logSourceService.delete(source.getId());
        logSourceRepository.flush();

        assertEquals(0, logSourceRepository.count());
        assertEquals(0, logLineRepository.count());
    }

    @Test
    void shouldUpdateFilePath() {
        Host host = createHost();
        var source = logSourceService.createLogFile("App", host.getId(), "/var/log/app.log", false);

        logSourceService.updateFilePath(source.getId(), "  /var/log/app2.log  ");

        var refreshed = logSourceService.find(source.getId());
        assertEquals("/var/log/app2.log", refreshed.getFilePath());
    }

    @Test
    void shouldDisableClearsLastErrorAndStops() {
        Host host = createHost();
        var source = logSourceService.createLogFile("App", host.getId(), "/var/log/app.log", false);

        logSourceService.updateStatus(source.getId(), com.allanvital.maestrao.model.LogSourceStatus.ERROR, "boom");
        var withError = logSourceService.find(source.getId());
        assertEquals("boom", withError.getLastError());

        logSourceService.disable(source.getId());
        var paused = logSourceService.find(source.getId());
        assertFalse(paused.isEnabled());
        assertEquals(com.allanvital.maestrao.model.LogSourceStatus.STOPPED, paused.getStatus());
        assertNull(paused.getLastError());
    }

    private Host createHost() {
        Credential credential = credentialService.create(
                "Main credential",
                CredentialType.PASSWORD,
                "root",
                "secret",
                null
        );

        return hostService.create(
                "server",
                "server.local",
                22,
                null,
                credential.getId()
        );
    }
}
