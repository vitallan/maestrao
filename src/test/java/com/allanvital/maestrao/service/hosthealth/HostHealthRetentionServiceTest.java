package com.allanvital.maestrao.service.hosthealth;

import com.allanvital.maestrao.TestConfig;
import com.allanvital.maestrao.model.CredentialType;
import com.allanvital.maestrao.model.Host;
import com.allanvital.maestrao.model.HostHealthSample;
import com.allanvital.maestrao.repository.HostHealthSampleRepository;
import com.allanvital.maestrao.repository.HostRepository;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestConfig.class)
@TestPropertySource(properties = {
        "maestrao.host-metrics.retention-days=7"
})
@Transactional
class HostHealthRetentionServiceTest {

    @Autowired
    private HostHealthRetentionService retentionService;

    @Autowired
    private HostHealthSampleRepository sampleRepository;

    @Autowired
    private HostService hostService;

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private HostRepository hostRepository;

    @BeforeEach
    void setUp() {
        sampleRepository.deleteAll();
        sampleRepository.flush();
        hostRepository.deleteAll();
        hostRepository.flush();
    }

    @Test
    void cleanupDeletesOlderThan7Days() {
        var cred = credentialService.create("cred", CredentialType.PASSWORD, "root", "secret", null);
        Host host = hostService.create("h1", "server.local", 22, null, cred.getId(), true);

        HostHealthSample old = sample(host, Instant.now().minus(8, ChronoUnit.DAYS));
        HostHealthSample recent = sample(host, Instant.now().minus(1, ChronoUnit.DAYS));
        sampleRepository.save(old);
        sampleRepository.save(recent);
        sampleRepository.flush();
        assertEquals(2, sampleRepository.count());

        retentionService.cleanup();
        sampleRepository.flush();

        assertEquals(1, sampleRepository.count());
    }

    private HostHealthSample sample(Host host, Instant at) {
        HostHealthSample s = new HostHealthSample();
        s.setHost(host);
        s.setCollectedAt(at);
        s.setLoad1(0.1);
        s.setLoad5(0.1);
        s.setLoad15(0.1);
        s.setMemTotalBytes(1000L);
        s.setMemAvailableBytes(500L);
        s.setDiskRootTotalBytes(1000L);
        s.setDiskRootAvailableBytes(400L);
        s.setCpuUser(1L);
        s.setCpuNice(0L);
        s.setCpuSystem(1L);
        s.setCpuIdle(1L);
        s.setCpuIowait(0L);
        s.setCpuIrq(0L);
        s.setCpuSoftirq(0L);
        s.setCpuSteal(0L);
        return s;
    }
}
