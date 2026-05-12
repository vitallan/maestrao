package com.allanvital.maestrao.service.hosthealth;

import com.allanvital.maestrao.model.Host;
import com.allanvital.maestrao.model.HostHealthSample;
import com.allanvital.maestrao.model.DecryptedCredential;
import com.allanvital.maestrao.repository.HostHealthSampleRepository;
import com.allanvital.maestrao.repository.HostRepository;
import com.allanvital.maestrao.security.CredentialCryptoService;
import com.allanvital.maestrao.service.ssh.SshClient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class HostHealthMetricsCollectorService {

    private static final Logger log = LoggerFactory.getLogger(HostHealthMetricsCollectorService.class);

    // Linux-only, designed to be very small output and parseable.
    static final String CMD_LOADAVG = "cat /proc/loadavg";
    static final String CMD_CPU = "head -n1 /proc/stat";
    static final String CMD_MEMINFO = "cat /proc/meminfo";
    static final String CMD_DF = "df -P -k /";

    private final HostRepository hostRepository;
    private final HostHealthSampleRepository sampleRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final SshClient sshClient;
    private final HostHealthMetricsParser parser;
    private final TransactionTemplate tx;

    @PersistenceContext
    private EntityManager entityManager;

    private final ExecutorService executor;

    private final long sshTimeoutMillis;
    private final int maxStdoutBytes;
    private final int maxStderrBytes;

    // Simple in-memory backoff to avoid hammering down hosts.
    private final Map<Long, Long> nextAllowedPollAtMillis = new ConcurrentHashMap<>();

    public HostHealthMetricsCollectorService(HostRepository hostRepository,
                                            HostHealthSampleRepository sampleRepository,
                                            CredentialCryptoService credentialCryptoService,
                                            SshClient sshClient,
                                            HostHealthMetricsParser parser,
                                            PlatformTransactionManager transactionManager,
                                            @Value("${maestrao.host-metrics.ssh-timeout-ms:2000}") long sshTimeoutMillis,
                                            @Value("${maestrao.host-metrics.max-stdout-bytes:16384}") int maxStdoutBytes,
                                            @Value("${maestrao.host-metrics.max-stderr-bytes:4096}") int maxStderrBytes,
                                            @Value("${maestrao.host-metrics.poll-concurrency:6}") int concurrency) {
        this.hostRepository = hostRepository;
        this.sampleRepository = sampleRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.sshClient = sshClient;
        this.parser = parser;
        this.tx = new TransactionTemplate(transactionManager);

        this.sshTimeoutMillis = sshTimeoutMillis;
        this.maxStdoutBytes = Math.max(1024, maxStdoutBytes);
        this.maxStderrBytes = Math.max(1024, maxStderrBytes);

        int threads = Math.max(1, Math.min(16, concurrency));
        this.executor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r);
            t.setName("maestrao-host-metrics-" + t.threadId());
            t.setDaemon(true);
            return t;
        });
    }

    @Scheduled(
            initialDelayString = "${maestrao.host-metrics.poll-ms:15000}",
            fixedDelayString = "${maestrao.host-metrics.poll-ms:15000}"
    )
    public void pollScheduled() {
        try {
            log.info("hostMetrics.poll tick");
            pollOnce();
        } catch (RuntimeException e) {
            log.warn("hostMetrics.poll failed: {}", e.getMessage());
        }
    }

    public void pollOnce() {
        List<Host> hosts = hostRepository.findByGatherHealthMetricsTrueOrderByNameAsc();
        log.info("hostMetrics.poll enabledHosts={}", hosts.size());
        if (hosts.isEmpty()) {
            return;
        }

        log.debug("hostMetrics.poll begin hosts={}", hosts.size());

        // Ensure hosts and credentials are fully initialized before we hop threads.
        for (Host h : hosts) {
            if (h != null && h.getCredential() != null) {
                h.getCredential().getId();
            }
        }

        long nowMillis = System.currentTimeMillis();
        List<Callable<Void>> tasks = new ArrayList<>(hosts.size());
        for (Host h : hosts) {
            Long hostId = h.getId();
            if (hostId == null) {
                continue;
            }

            Long nextAllowed = nextAllowedPollAtMillis.get(hostId);
            if (nextAllowed != null && nextAllowed > nowMillis) {
                continue;
            }

            tasks.add(() -> {
                pollHost(h);
                return null;
            });
        }

        if (tasks.isEmpty()) {
            return;
        }

        try {
            // Stagger polls slightly to avoid bursts on the SSH server side.
            long jitter = ThreadLocalRandom.current().nextLong(0, 500);
            try {
                Thread.sleep(jitter);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException ignored) {
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void pollHost(Host host) {
        Long hostId = host.getId();
        if (hostId == null) {
            return;
        }

        try {
            String secret = credentialCryptoService.decrypt(host.getCredential().getEncryptedSecret());
            DecryptedCredential decrypted = new DecryptedCredential(host.getCredential(), secret);
            String loadAvg = runCommand(host, decrypted, hostId, CMD_LOADAVG);
            String cpu = runCommand(host, decrypted, hostId, CMD_CPU);
            String memInfo = runCommand(host, decrypted, hostId, CMD_MEMINFO);
            String df = runCommand(host, decrypted, hostId, CMD_DF);
            if (loadAvg == null || cpu == null || memInfo == null || df == null) {
                backoff(hostId);
                return;
            }

            String memTotal = null;
            String memAvailable = null;
            for (String line : memInfo.split("\\r?\\n")) {
                if (line.startsWith("MemTotal:")) {
                    memTotal = line;
                } else if (line.startsWith("MemAvailable:")) {
                    memAvailable = line;
                }
            }
            if (memTotal == null || memAvailable == null) {
                log.warn("hostMetrics.poll hostId={} ip={} failed: meminfo missing required keys", hostId, host.getIp());
                backoff(hostId);
                return;
            }

            StringBuilder combined = new StringBuilder();
            combined.append(firstNonBlankLine(loadAvg)).append('\n');
            combined.append(firstNonBlankLine(cpu)).append('\n');
            combined.append(memTotal).append('\n');
            combined.append(memAvailable).append('\n');
            combined.append(df.trim()).append('\n');

            HostHealthMetricsParser.ParsedMetrics m;
            try {
                m = parser.parse(combined.toString());
            } catch (RuntimeException e) {
                String out = combined.toString();
                if (out != null && out.length() > 400) {
                    out = out.substring(0, 400) + "...";
                }
                log.warn("hostMetrics.poll hostId={} ip={} failed: parse error: {} stdout={}",
                        hostId, host.getIp(), e.getMessage(), out);
                backoff(hostId);
                return;
            }
            HostHealthSample s = new HostHealthSample();
            s.setHost(entityManager.getReference(Host.class, hostId));
            s.setCollectedAt(Instant.now());
            s.setLoad1(m.load1());
            s.setLoad5(m.load5());
            s.setLoad15(m.load15());
            s.setMemTotalBytes(m.memTotalBytes());
            s.setMemAvailableBytes(m.memAvailableBytes());
            s.setDiskRootTotalBytes(m.diskRootTotalBytes());
            s.setDiskRootAvailableBytes(m.diskRootAvailableBytes());

            s.setCpuUser(m.cpuUser());
            s.setCpuNice(m.cpuNice());
            s.setCpuSystem(m.cpuSystem());
            s.setCpuIdle(m.cpuIdle());
            s.setCpuIowait(m.cpuIowait());
            s.setCpuIrq(m.cpuIrq());
            s.setCpuSoftirq(m.cpuSoftirq());
            s.setCpuSteal(m.cpuSteal());

            tx.executeWithoutResult(status -> sampleRepository.save(s));
            nextAllowedPollAtMillis.remove(hostId);
            log.info("hostMetrics.poll hostId={} ip={} ok", hostId, host.getIp());
        } catch (RuntimeException e) {
            log.warn("hostMetrics.poll hostId={} ip={} failed: {}", hostId, host.getIp(), e.getMessage());
            backoff(hostId);
        }
    }

    private void backoff(Long hostId) {
        // Backoff for 60 seconds on failure.
        nextAllowedPollAtMillis.put(hostId, System.currentTimeMillis() + 60_000L);
    }

    private String runCommand(Host host,
                              DecryptedCredential decrypted,
                              Long hostId,
                              String command) {
        SshClient.SshExecResult res = sshClient.execBlocking(
                host.getIp(),
                host.getSshPort(),
                decrypted,
                command,
                null,
                sshTimeoutMillis,
                maxStdoutBytes,
                maxStderrBytes
        );

        if (res == null) {
            log.warn("hostMetrics.poll hostId={} ip={} failed: null ssh result for command={}", hostId, host.getIp(), command);
            return null;
        }
        if (res.timedOut()) {
            log.warn("hostMetrics.poll hostId={} ip={} failed: timeout for command={}", hostId, host.getIp(), command);
            return null;
        }
        if (res.exitCode() == null || res.exitCode() != 0) {
            log.warn("hostMetrics.poll hostId={} ip={} failed: exitCode={} command={} stderr={}",
                    hostId, host.getIp(), res.exitCode(), command, truncate(res.stderr()));
            return null;
        }
        String out = res.stdout();
        if (out == null || out.isBlank()) {
            log.warn("hostMetrics.poll hostId={} ip={} failed: empty output for command={}", hostId, host.getIp(), command);
            return null;
        }
        return out;
    }

    private String firstNonBlankLine(String value) {
        if (value == null) {
            return "";
        }
        for (String line : value.split("\\r?\\n")) {
            if (!line.isBlank()) {
                return line;
            }
        }
        return "";
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= 400) {
            return value;
        }
        return value.substring(0, 400) + "...";
    }
}
