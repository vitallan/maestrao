package com.allanvital.maestrao.service.log;

import com.allanvital.maestrao.TestConfig;
import com.allanvital.maestrao.model.*;
import com.allanvital.maestrao.repository.LogLineRepository;
import com.allanvital.maestrao.repository.LogSourceRepository;
import com.allanvital.maestrao.service.CredentialService;
import com.allanvital.maestrao.service.HostService;
import com.allanvital.maestrao.ssh.FakeSshClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import jakarta.persistence.EntityManager;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestConfig.class)
@TestPropertySource(properties = {
        "maestrao.logs.collector.reconnect-delay-ms=123"
})
class LogCollectorManagerTest {

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

    @Autowired
    private FakeSshClient fakeSshClient;

    @Autowired
    private TestConfig.CapturingSleeper capturingSleeper;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        logLineRepository.deleteAll();
        logLineRepository.flush();
        logSourceRepository.deleteAll();
        logSourceRepository.flush();
        fakeSshClient.reset();
    }

    @Test
    void shouldIngestThenReconnectAfterDisconnect() {
        Credential credential = credentialService.create("cred", CredentialType.PASSWORD, "root", "secret", null);
        Host host = hostService.create("server", "server.local", 22, null, credential.getId());

        FakeSshClient.FakeExecHandle first = fakeSshClient.enqueueExecHandle();
        FakeSshClient.FakeExecHandle second = fakeSshClient.enqueueExecHandle();

        LogSource source = logSourceService.createLogFile("App", host.getId(), "/var/log/app.log", true);

        first.emitLine("l1");
        waitUntil(() -> logLineRepository.count() >= 1, Duration.ofSeconds(2));

        // Simulate disconnect/error
        first.close();

        // Loosely validate backoff was requested
        assertTrue(capturingSleeper.awaitSleep(2000));
        assertTrue(capturingSleeper.getLastSleptMillis() >= 123);

        // Reconnect and keep ingesting
        second.emitLine("l2");
        waitUntil(() -> logLineRepository.count() >= 2, Duration.ofSeconds(2));

        waitUntil(() -> {
            entityManager.clear();
            LogSource refreshed = logSourceRepository.findById(source.getId()).orElseThrow();
            return refreshed.getStatus() == LogSourceStatus.RUNNING;
        }, Duration.ofSeconds(2));
    }

    private void waitUntil(BooleanSupplier condition, Duration timeout) {
        Instant end = Instant.now().plus(timeout);
        while (Instant.now().isBefore(end)) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        fail("Condition not met within " + timeout);
    }

    private interface BooleanSupplier {
        boolean getAsBoolean();
    }
}
