package com.allanvital.maestrao.artifactproxy.service;

import com.allanvital.maestrao.artifactproxy.config.ArtifactProxyProperties;
import com.allanvital.maestrao.artifactproxy.model.ArtifactRemote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Service
@ConditionalOnProperty(prefix = "maestrao.artifact-proxy", name = "enabled", havingValue = "true")
public class ArtifactRemoteFetchService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactRemoteFetchService.class);

    private final ArtifactRemoteService remoteService;
    private final ArtifactProxyProperties properties;
    private final HttpClient client;

    public ArtifactRemoteFetchService(ArtifactRemoteService remoteService, ArtifactProxyProperties properties) {
        this.remoteService = remoteService;
        this.properties = properties;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, properties.getConnectTimeoutMs())))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public RemoteFetchResult fetchFirstSuccess(String artifactPath) {
        return fetchFirstSuccess(artifactPath, null, null, false);
    }

    public RemoteFetchResult revalidateMetadata(String artifactPath, String etag, String lastModified) {
        return fetchFirstSuccess(artifactPath, etag, lastModified, true);
    }

    private RemoteFetchResult fetchFirstSuccess(String artifactPath, String etag, String lastModified, boolean allowNotModified) {
        List<ArtifactRemote> remotes = remoteService.findEnabled();
            if (remotes.isEmpty()) {
                return new RemoteFetchResult(null, 503, null, null, "no enabled remotes", null, null);
            }
        int threads = Math.max(1, Math.min(properties.getMaxInflightFetches(), remotes.size()));
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CompletionService<RemoteFetchResult> completion = new ExecutorCompletionService<>(executor);
        List<Future<RemoteFetchResult>> futures = new ArrayList<>();
        List<RemoteFetchResult> results = new ArrayList<>();
        try {
            for (ArtifactRemote remote : remotes) {
                futures.add(completion.submit(() -> fetchFromRemote(remote, artifactPath, etag, lastModified)));
            }
            RemoteFetchResult last = null;
            for (int i = 0; i < remotes.size(); i++) {
                Future<RemoteFetchResult> f = completion.take();
                RemoteFetchResult result = f.get();
                last = result;
                results.add(result);
                log.debug("artifactProxy.fetch remote={} status={} error={}",
                        result.remote() == null ? "-" : result.remote().getName(),
                        result.statusCode(),
                        result.error());
                if (result.ok() || (allowNotModified && result.statusCode() == 304)) {
                    for (Future<RemoteFetchResult> other : futures) {
                        if (!other.isDone()) {
                            other.cancel(true);
                        }
                    }
                    closeNonWinning(results, result);
                    return result;
                }
            }
            if (last != null && (last.statusCode() == 401 || last.statusCode() == 403)) {
                return new RemoteFetchResult(last.remote(), 502, null, null,
                        "upstream authorization failed (check remote credentials)", null, null);
            }
            boolean allNotFound = !results.isEmpty() && results.stream().allMatch(r -> r.statusCode() == 404);
            if (allNotFound) {
                return last;
            }
            return last == null
                    ? new RemoteFetchResult(null, 502, null, null, "no remote result", null, null)
                    : last;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RemoteFetchResult(null, 502, null, null, "interrupted", null, null);
        } catch (ExecutionException e) {
            return new RemoteFetchResult(null, 502, null, null, e.getMessage(), null, null);
        } finally {
            executor.shutdownNow();
        }
    }

    private void closeNonWinning(List<RemoteFetchResult> results, RemoteFetchResult winner) {
        for (RemoteFetchResult r : results) {
            if (r != winner) {
                r.close();
            }
        }
    }

    private RemoteFetchResult fetchFromRemote(ArtifactRemote remote, String artifactPath, String etag, String lastModified) {
        String path = artifactPath.startsWith("/") ? artifactPath.substring(1) : artifactPath;
        String url = remote.getBaseUrl() == null ? "" : remote.getBaseUrl().trim().replace(" ", "");
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        URI uri = URI.create(url + path);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(remoteService.timeout(remote, properties.getReadTimeoutMs()))
                    .GET();
            String authHeader = remoteService.authHeaderOrNull(remote);
            if (authHeader != null) {
                builder.header("Authorization", authHeader);
            }
            if (etag != null && !etag.isBlank()) {
                builder.header("If-None-Match", etag);
            }
            if (lastModified != null && !lastModified.isBlank()) {
                builder.header("If-Modified-Since", lastModified);
            }
            HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            return new RemoteFetchResult(
                    remote,
                    response.statusCode(),
                    response.headers().firstValueAsLong("Content-Length").orElse(-1),
                    response.body(),
                    null,
                    response.headers().firstValue("ETag").orElse(null),
                    response.headers().firstValue("Last-Modified").orElse(null)
            );
        } catch (IOException e) {
            log.warn("artifactProxy.fetch remote={} failed uri={} error={}", remote.getName(), uri, e.getMessage());
            return new RemoteFetchResult(remote, 502, null, null, e.getMessage(), null, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RemoteFetchResult(remote, 502, null, null, "interrupted", null, null);
        }
    }
}
