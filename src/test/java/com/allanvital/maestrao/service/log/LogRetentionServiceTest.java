package com.allanvital.maestrao.service.log;

import com.allanvital.maestrao.TestConfig;
import com.allanvital.maestrao.model.CredentialType;
import com.allanvital.maestrao.model.LogLine;
import com.allanvital.maestrao.model.LogSource;
import com.allanvital.maestrao.repository.LogLineRepository;
import com.allanvital.maestrao.service.CredentialService;
import com.allanvital.maestrao.service.HostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Import(TestConfig.class)
@TestPropertySource(properties = {
        "maestrao.logs.retention-days=1"
})
@Transactional
class LogRetentionServiceTest {

    @Autowired
    private LogRetentionService logRetentionService;

    @Autowired
    private LogLineRepository logLineRepository;

    @Autowired
    private LogSourceService logSourceService;

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private HostService hostService;

    @BeforeEach
    void setUp() {
        logLineRepository.deleteAll();
        logLineRepository.flush();
    }

    @Test
    void shouldDeleteOlderThanRetentionCutoff() {
        var credential = credentialService.create("cred", CredentialType.PASSWORD, "root", "secret", null);
        var host = hostService.create("server", "server.local", 22, null, credential.getId());
        LogSource source = logSourceService.createLogFile("App", host.getId(), "/var/log/app.log", false);

        LogLine oldLine = new LogLine();
        oldLine.setLogSource(source);
        oldLine.setIngestedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        oldLine.setLine("old");
        logLineRepository.save(oldLine);

        LogLine newLine = new LogLine();
        newLine.setLogSource(source);
        newLine.setIngestedAt(Instant.now());
        newLine.setLine("new");
        logLineRepository.saveAndFlush(newLine);

        assertEquals(2, logLineRepository.count());

        logRetentionService.cleanupOldLines();
        logLineRepository.flush();

        assertEquals(1, logLineRepository.count());
    }
}
