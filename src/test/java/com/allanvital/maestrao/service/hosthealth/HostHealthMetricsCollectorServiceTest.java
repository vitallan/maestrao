package com.allanvital.maestrao.service.hosthealth;

import com.allanvital.maestrao.TestConfig;
import com.allanvital.maestrao.model.CredentialType;
import com.allanvital.maestrao.model.Host;
import com.allanvital.maestrao.repository.HostHealthSampleRepository;
import com.allanvital.maestrao.repository.HostRepository;
import com.allanvital.maestrao.service.CredentialService;
import com.allanvital.maestrao.service.HostService;
import com.allanvital.maestrao.ssh.FakeSshClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestConfig.class)
class HostHealthMetricsCollectorServiceTest {

    @Autowired
    private HostHealthMetricsCollectorService collector;

    @Autowired
    private HostService hostService;

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private FakeSshClient fakeSshClient;

    @Autowired
    private HostHealthSampleRepository sampleRepository;

    @Autowired
    private HostRepository hostRepository;

    @BeforeEach
    void setUp() {
        sampleRepository.deleteAll();
        sampleRepository.flush();
        hostRepository.deleteAll();
        hostRepository.flush();
        fakeSshClient.reset();
    }

    @Test
    void pollOnceShouldIngestSampleForEnabledHost() {
        var cred = credentialService.create("cred", CredentialType.PASSWORD, "root", "secret", null);
        Host host = hostService.create("h1", "server.local", 22, null, cred.getId(), true);

        fakeSshClient.enqueueExecResult().stdout("0.15 0.10 0.05 1/100 12345\n");
        fakeSshClient.enqueueExecResult().stdout("cpu 100 0 50 200 0 0 0 0\n");
        fakeSshClient.enqueueExecResult().stdout("MemTotal:       16384 kB\nMemAvailable:    8192 kB\n");
        fakeSshClient.enqueueExecResult().stdout("Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/sda1 1000000 600000 400000 60% /\n");

        collector.pollOnce();

        assertEquals(1, sampleRepository.count());
        var sample = sampleRepository.findAll().get(0);
        assertEquals(host.getId(), sample.getHost().getId());
        assertEquals(0.15, sample.getLoad1(), 0.0001);
        assertEquals(16384L * 1024L, sample.getMemTotalBytes());
        assertEquals(8192L * 1024L, sample.getMemAvailableBytes());
        assertEquals(1000000L * 1024L, sample.getDiskRootTotalBytes());
        assertEquals(400000L * 1024L, sample.getDiskRootAvailableBytes());
        assertEquals(100L, sample.getCpuUser());
        assertEquals(200L, sample.getCpuIdle());
    }

    @Test
    void pollOnceShouldSkipHostsWithoutFlag() {
        var cred = credentialService.create("cred", CredentialType.PASSWORD, "root", "secret", null);
        hostService.create("h1", "server.local", 22, null, cred.getId(), false);

        collector.pollOnce();

        assertEquals(0, sampleRepository.count());
    }
}
