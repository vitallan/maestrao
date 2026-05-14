package com.allanvital.maestrao.artifactproxy.service;

import com.allanvital.maestrao.artifactproxy.config.ArtifactProxyProperties;
import com.allanvital.maestrao.artifactproxy.model.ArtifactCacheStatus;
import com.allanvital.maestrao.artifactproxy.model.ArtifactRemote;
import com.allanvital.maestrao.artifactproxy.repository.ArtifactCacheEntryRepository;
import com.allanvital.maestrao.artifactproxy.service.storage.ArtifactContentStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

@Service
@ConditionalOnProperty(prefix = "maestrao.artifact-proxy", name = "enabled", havingValue = "true")
public class ArtifactPathProbeService {

    private final ArtifactRemoteService remoteService;
    private final ArtifactCacheEntryRepository cacheEntryRepository;
    private final ArtifactContentStore contentStore;
    private final ArtifactProxyProperties properties;
    private final HttpClient client;

    public ArtifactPathProbeService(ArtifactRemoteService remoteService,
                                    ArtifactCacheEntryRepository cacheEntryRepository,
                                    ArtifactContentStore contentStore,
                                    ArtifactProxyProperties properties) {
        this.remoteService = remoteService;
        this.cacheEntryRepository = cacheEntryRepository;
        this.contentStore = contentStore;
        this.properties = properties;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, properties.getConnectTimeoutMs())))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public ProbeReport probe(String rawPath) {
        String artifactPath = sanitize(rawPath);
        boolean cacheHit;
        try {
            cacheHit = contentStore.exists(artifactPath);
        } catch (Exception e) {
            cacheHit = false;
        }
        Instant now = Instant.now();
        Instant negativeTtlUntil = cacheEntryRepository.findByArtifactPath(artifactPath)
                .filter(e -> e.getStatus() == ArtifactCacheStatus.NEGATIVE)
                .map(e -> e.getNegativeTtlUntil())
                .orElse(null);
        boolean negativeCacheActive = negativeTtlUntil != null && negativeTtlUntil.isAfter(now);

        if (cacheHit) {
            return new ProbeReport(rawPath, artifactPath, true, false, null, 200, List.of(), null);
        }
        if (negativeCacheActive) {
            return new ProbeReport(rawPath, artifactPath, false, true, null, 404, List.of(), negativeTtlUntil);
        }

        List<ArtifactRemote> remotes = remoteService.findEnabled();
        if (remotes.isEmpty()) {
            return new ProbeReport(rawPath, artifactPath, false, false, null, 503, List.of(), null);
        }

        int threads = Math.max(1, Math.min(properties.getMaxInflightFetches(), remotes.size()));
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CompletionService<ProbeRemoteResult> completion = new ExecutorCompletionService<>(executor);
        try {
            for (ArtifactRemote remote : remotes) {
                completion.submit(() -> probeRemote(remote, artifactPath));
            }

            List<ProbeRemoteResult> all = new ArrayList<>();
            for (int i = 0; i < remotes.size(); i++) {
                Future<ProbeRemoteResult> f = completion.take();
                all.add(f.get());
            }

            ProbeRemoteResult winner = all.stream().filter(r -> r.statusCode() == 200).findFirst().orElse(null);
            int finalStatus = winner != null
                    ? 200
                    : (all.stream().allMatch(r -> r.statusCode() == 404) ? 404 : 502);

            all.sort(Comparator.comparingLong(ProbeRemoteResult::latencyMs));
            return new ProbeReport(
                    rawPath,
                    artifactPath,
                    false,
                    false,
                    winner == null ? null : winner.remoteName(),
                    finalStatus,
                    all,
                    null
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ProbeReport(rawPath, artifactPath, false, false, null, 502, List.of(), null);
        } catch (ExecutionException e) {
            return new ProbeReport(rawPath, artifactPath, false, false, null, 502, List.of(), null);
        } finally {
            executor.shutdownNow();
        }
    }

    private ProbeRemoteResult probeRemote(ArtifactRemote remote, String artifactPath) {
        String url = remote.getBaseUrl() == null ? "" : remote.getBaseUrl().trim().replace(" ", "");
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        URI uri = URI.create(url + artifactPath);
        long started = System.nanoTime();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(remoteService.timeout(remote, properties.getReadTimeoutMs()))
                    .GET();
            String authHeader = remoteService.authHeaderOrNull(remote);
            if (authHeader != null) {
                builder.header("Authorization", authHeader);
            }
            HttpResponse<Void> response = client.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            return new ProbeRemoteResult(remote.getName(), response.statusCode(), latencyMs, null);
        } catch (Exception e) {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            return new ProbeRemoteResult(remote.getName(), 502, latencyMs, e.getMessage());
        }
    }

    private String sanitize(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        String normalized = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
        normalized = normalized.replace('\\', '/');
        if (normalized.isBlank() || normalized.contains("..")) {
            throw new IllegalArgumentException("invalid artifact path");
        }
        return normalized;
    }

    public record ProbeReport(
            String requestedPath,
            String normalizedPath,
            boolean cacheHit,
            boolean negativeCacheActive,
            String winnerRemote,
            int finalStatus,
            List<ProbeRemoteResult> remoteResults,
            Instant negativeCacheTtlUntil
    ) {
    }

    public record ProbeRemoteResult(
            String remoteName,
            int statusCode,
            long latencyMs,
            String error
    ) {
    }
}
