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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestConfig.class)
@Transactional
class HostHealthMetricsServiceTest {

    @Autowired
    private HostHealthMetricsService metricsService;

    @Autowired
    private HostService hostService;

    @Autowired
    private CredentialService credentialService;

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
    }

    @Test
    void getSeriesComputesCpuAndPercentages() {
        var cred = credentialService.create("cred", CredentialType.PASSWORD, "root", "secret", null);
        Host host = hostService.create("h1", "server.local", 22, null, cred.getId(), true);

        HostHealthSample s1 = sample(host, Instant.now().minusSeconds(60), 100, 50, 200);
        HostHealthSample s2 = sample(host, Instant.now(), 130, 70, 230);
        sampleRepository.save(s1);
        sampleRepository.save(s2);
        sampleRepository.flush();

        HostHealthMetricsService.HostHealthSeries series = metricsService.getSeries(host.getId(), HostHealthWindow.H1);
        assertEquals(2, series.points().size());

        var p1 = series.points().get(0);
        assertTrue(Double.isNaN(p1.cpuUsedPct()));
        assertEquals(50.0, p1.memUsedPct(), 0.0001);
        assertEquals(60.0, p1.diskUsedPct(), 0.0001);

        var p2 = series.points().get(1);
        // total delta = (idle+iowait + nonIdle) delta.
        // here we only change user/system/idle: prev total=100+50+200=350, cur total=130+70+230=430, delta=80
        // idle delta=30 -> used = (80-30)/80=62.5%
        assertEquals(62.5, p2.cpuUsedPct(), 0.0001);
    }

    private HostHealthSample sample(Host host, Instant at, long cpuUser, long cpuSystem, long cpuIdle) {
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
        s.setCpuUser(cpuUser);
        s.setCpuNice(0L);
        s.setCpuSystem(cpuSystem);
        s.setCpuIdle(cpuIdle);
        s.setCpuIowait(0L);
        s.setCpuIrq(0L);
        s.setCpuSoftirq(0L);
        s.setCpuSteal(0L);
        return s;
    }
}
