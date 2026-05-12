package com.allanvital.maestrao.service.log;

import com.allanvital.maestrao.TestConfig;
import com.allanvital.maestrao.model.CredentialType;
import com.allanvital.maestrao.model.LogLine;
import com.allanvital.maestrao.model.LogSource;
import com.allanvital.maestrao.repository.LogLineRepository;
import com.allanvital.maestrao.service.CredentialService;
import com.allanvital.maestrao.service.HostService;
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
class LogLineQueryServiceTest {

    @Autowired
    private LogLineQueryService logLineQueryService;

    @Autowired
    private LogLineRepository logLineRepository;

    @Autowired
    private LogSourceService logSourceService;

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private HostService hostService;

    @Test
    void shouldReturnLatestLine() {
        var credential = credentialService.create("cred", CredentialType.PASSWORD, "root", "secret", null);
        var host = hostService.create("server", "server.local", 22, null, credential.getId(), false);
        LogSource source = logSourceService.createLogFile("App", host.getId(), "/var/log/app.log", false);

        LogLine l1 = new LogLine();
        l1.setLogSource(source);
        l1.setIngestedAt(Instant.now());
        l1.setLine("a");
        logLineRepository.save(l1);

        LogLine l2 = new LogLine();
        l2.setLogSource(source);
        l2.setIngestedAt(Instant.now());
        l2.setLine("b");
        logLineRepository.saveAndFlush(l2);

        LogLineQueryService.LatestLogLine latest = logLineQueryService.findLatest(source.getId());
        assertNotNull(latest);
        assertEquals("b", latest.line());
    }
}
