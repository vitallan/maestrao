package com.allanvital.maestrao.service.log.search;

import com.allanvital.maestrao.TestConfig;
import com.allanvital.maestrao.model.*;
import com.allanvital.maestrao.repository.LogLineRepository;
import com.allanvital.maestrao.service.CredentialService;
import com.allanvital.maestrao.service.HostService;
import com.allanvital.maestrao.service.log.LogSourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestConfig.class)
@Transactional
class LogSearchServiceTest {

    @Autowired
    private LogSearchService logSearchService;

    @Autowired
    private LogLineRepository logLineRepository;

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private HostService hostService;

    @Autowired
    private LogSourceService logSourceService;

    @BeforeEach
    void setUp() {
        logLineRepository.deleteAll();
        logLineRepository.flush();
    }

    @Test
    void emptyQueryReturnsAllSortedByIngestedAtDesc() {
        LogSource source1 = createSource("h1", "10.0.0.1");
        LogSource source2 = createSource("h2", "10.0.0.2");

        insertLine(source1, Instant.parse("2026-05-05T10:00:00Z"), "alpha");
        insertLine(source2, Instant.parse("2026-05-05T10:01:00Z"), "beta");

        Page<LogSearchRow> page = logSearchService.search("   ", PageRequest.of(0, 10));
        assertEquals(2, page.getTotalElements());
        assertEquals(source2.getName(), page.getContent().get(0).logName());
        assertEquals("beta", page.getContent().get(0).line());
        assertEquals(source1.getName(), page.getContent().get(1).logName());
        assertEquals("alpha", page.getContent().get(1).line());
    }

    @Test
    void queryIsCaseInsensitiveLike() {
        LogSource source = createSource("h1", "10.0.0.1");
        insertLine(source, Instant.parse("2026-05-05T10:00:00Z"), "Error: boom");
        insertLine(source, Instant.parse("2026-05-05T10:01:00Z"), "all good");

        Page<LogSearchRow> page = logSearchService.search("error", PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals(source.getName(), page.getContent().get(0).logName());
        assertTrue(page.getContent().get(0).line().toLowerCase().contains("error"));

        Page<LogSearchRow> page2 = logSearchService.search("ERROR", PageRequest.of(0, 10));
        assertEquals(1, page2.getTotalElements());
    }

    @Test
    void keyValueTokensAreAndFilters() {
        LogSource source = createSource("h1", "10.0.0.1");
        insertLine(source, Instant.parse("2026-05-05T10:00:00Z"), "service=api level=info hello");
        insertLine(source, Instant.parse("2026-05-05T10:01:00Z"), "service=api level=error boom");
        insertLine(source, Instant.parse("2026-05-05T10:02:00Z"), "service=web level=error nope");

        Page<LogSearchRow> page = logSearchService.search("service=api level=error", PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertTrue(page.getContent().get(0).line().contains("service=api"));
        assertTrue(page.getContent().get(0).line().contains("level=error"));
    }

    @Test
    void freeTextAndKeyValueMustBothMatch() {
        LogSource source = createSource("h1", "10.0.0.1");
        insertLine(source, Instant.parse("2026-05-05T10:00:00Z"), "service=api timeout while connecting");
        insertLine(source, Instant.parse("2026-05-05T10:01:00Z"), "service=api all good");

        Page<LogSearchRow> page = logSearchService.search("timeout service=api", PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertTrue(page.getContent().get(0).line().toLowerCase().contains("timeout"));
        assertTrue(page.getContent().get(0).line().toLowerCase().contains("service=api"));
    }

    @Test
    void keyValueIsCaseInsensitive() {
        LogSource source = createSource("h1", "10.0.0.1");
        insertLine(source, Instant.parse("2026-05-05T10:00:00Z"), "service=api hello");

        Page<LogSearchRow> page = logSearchService.search("SERVICE=API", PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
    }

    @Test
    void paginationWorks() {
        LogSource source = createSource("h1", "10.0.0.1");
        insertLine(source, Instant.parse("2026-05-05T10:00:00Z"), "l1");
        insertLine(source, Instant.parse("2026-05-05T10:01:00Z"), "l2");
        insertLine(source, Instant.parse("2026-05-05T10:02:00Z"), "l3");

        Page<LogSearchRow> page1 = logSearchService.search(null, PageRequest.of(0, 2));
        assertEquals(3, page1.getTotalElements());
        assertEquals(2, page1.getContent().size());
        assertEquals(source.getName(), page1.getContent().get(0).logName());
        assertEquals("l3", page1.getContent().get(0).line());

        Page<LogSearchRow> page2 = logSearchService.search(null, PageRequest.of(1, 2));
        assertEquals(1, page2.getContent().size());
        assertEquals("l1", page2.getContent().get(0).line());
    }

    @Test
    void logFilterSupportsDoubleQuotedNamesWithSpaces() {
        LogSource spaced = createSourceWithName("h1", "10.0.0.1", "My Log With Spaces");
        LogSource other = createSourceWithName("h2", "10.0.0.2", "OtherLog");

        insertLine(spaced, Instant.parse("2026-05-05T10:00:00Z"), "alpha");
        insertLine(other, Instant.parse("2026-05-05T10:01:00Z"), "beta");

        Page<LogSearchRow> page = logSearchService.search("log:\"My Log With Spaces\"", PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals("My Log With Spaces", page.getContent().get(0).logName());
        assertEquals("alpha", page.getContent().get(0).line());
    }

    @Test
    void logFilterSupportsSingleQuotes() {
        LogSource spaced = createSourceWithName("h1", "10.0.0.1", "My Log With Spaces");
        LogSource other = createSourceWithName("h2", "10.0.0.2", "OtherLog");

        insertLine(spaced, Instant.parse("2026-05-05T10:00:00Z"), "alpha");
        insertLine(other, Instant.parse("2026-05-05T10:01:00Z"), "beta");

        Page<LogSearchRow> page = logSearchService.search("log:'My Log With Spaces'", PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals("My Log With Spaces", page.getContent().get(0).logName());
    }

    @Test
    void logFilterIsCaseInsensitiveExactMatch() {
        LogSource source = createSourceWithName("h1", "10.0.0.1", "MyLog");
        insertLine(source, Instant.parse("2026-05-05T10:00:00Z"), "alpha");

        Page<LogSearchRow> page = logSearchService.search("log:MYLOG", PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals("MyLog", page.getContent().get(0).logName());
    }

    @Test
    void logFilterAllowsWhitespaceBetweenOperatorAndValue() {
        LogSource source = createSourceWithName("h1", "10.0.0.1", "dnsao1");
        insertLine(source, Instant.parse("2026-05-05T10:00:00Z"), "INFO QueryEvent");

        Page<LogSearchRow> page = logSearchService.search("log: dnsao1 INFO QueryEvent", PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals("dnsao1", page.getContent().get(0).logName());
    }

    @Test
    void multipleLogFiltersAreRejected() {
        assertDoesNotThrow(() -> logSearchService.search("log:One log:Two", PageRequest.of(0, 10)));
    }

    @Test
    void unterminatedQuotesAreRejected() {
        assertDoesNotThrow(() -> logSearchService.search("log:\"My Log", PageRequest.of(0, 10)));
    }

    @Test
    void gluedTokensAfterQuotedLogAreRejectedWhenWhitespaceRequired() {
        assertDoesNotThrow(() -> logSearchService.search("log:\"My Log\"level=error", PageRequest.of(0, 10)));
    }

    private LogSource createSource(String hostName, String ip) {
        Credential credential = credentialService.create("cred-" + hostName, CredentialType.PASSWORD, "root", "secret", null);
        Host host = hostService.create(hostName, ip, 22, null, credential.getId());
        return logSourceService.createLogFile("src-" + hostName, host.getId(), "/var/log/app.log", false);
    }

    private LogSource createSourceWithName(String hostName, String ip, String logName) {
        Credential credential = credentialService.create("cred-" + hostName, CredentialType.PASSWORD, "root", "secret", null);
        Host host = hostService.create(hostName, ip, 22, null, credential.getId());
        return logSourceService.createLogFile(logName, host.getId(), "/var/log/app.log", false);
    }

    private void insertLine(LogSource source, Instant ingestedAt, String lineText) {
        LogLine line = new LogLine();
        line.setLogSource(source);
        line.setIngestedAt(ingestedAt);
        line.setLine(lineText);
        logLineRepository.save(line);
        logLineRepository.flush();
    }
}
