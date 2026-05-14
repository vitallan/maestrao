package com.allanvital.maestrao.artifactproxy.service;

import com.allanvital.maestrao.artifactproxy.config.ArtifactProxyProperties;
import com.allanvital.maestrao.artifactproxy.model.ArtifactCacheEntry;
import com.allanvital.maestrao.artifactproxy.model.ArtifactCacheStatus;
import com.allanvital.maestrao.artifactproxy.model.ArtifactContentKind;
import com.allanvital.maestrao.artifactproxy.repository.ArtifactCacheEntryRepository;
import com.allanvital.maestrao.artifactproxy.service.storage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

@Service
@ConditionalOnProperty(prefix = "maestrao.artifact-proxy", name = "enabled", havingValue = "true")
public class ArtifactCacheService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactCacheService.class);

    private final ArtifactContentStore contentStore;
    private final ArtifactCacheEntryRepository cacheEntryRepository;
    private final ArtifactRemoteFetchService remoteFetchService;
    private final ArtifactProxyProperties properties;
    private final ConcurrentHashMap<String, ReentrantLock> pathLocks = new ConcurrentHashMap<>();
    private final Semaphore activeStreamPermits;

    public ArtifactCacheService(
            ArtifactContentStore contentStore,
            ArtifactCacheEntryRepository cacheEntryRepository,
            ArtifactRemoteFetchService remoteFetchService,
            ArtifactProxyProperties properties
    ) {
        this.contentStore = contentStore;
        this.cacheEntryRepository = cacheEntryRepository;
        this.remoteFetchService = remoteFetchService;
        this.properties = properties;
        this.activeStreamPermits = new Semaphore(Math.max(1, properties.getMaxInflightFetches() * 4));
    }

    @Transactional
    public ResolutionResult resolve(String rawPath) {
        String artifactPath = sanitize(rawPath);
        boolean metadataPath = isMetadataPath(artifactPath);
        MetadataUpdatePolicy updatePolicy = MetadataUpdatePolicy.parse(properties.getMetadata().getUpdatePolicy());
        try {
            Optional<StoredArtifact> cached = contentStore.read(artifactPath);
            if (cached.isPresent()) {
                Optional<ArtifactCacheEntry> cachedEntry = cacheEntryRepository.findByArtifactPath(artifactPath);
                if (metadataPath && cachedEntry.isPresent() && cachedEntry.get().getStatus() == ArtifactCacheStatus.READY
                        && updatePolicy.shouldRevalidate(cachedEntry.get().getLastValidatedAt(), Instant.now())) {
                    ResolutionResult metadataResult = revalidateMetadata(artifactPath, cached.get(), cachedEntry.get());
                    if (metadataResult != null) {
                        return metadataResult;
                    }
                }
                markHit(artifactPath);
                log.info("artifactProxy.cache hit path={}", artifactPath);
                return ResolutionResult.cached(cached.get().path());
            }

            Optional<ArtifactCacheEntry> negativeEntry = cacheEntryRepository.findByArtifactPath(artifactPath)
                    .filter(e -> e.getStatus() == ArtifactCacheStatus.NEGATIVE && e.getNegativeTtlUntil() != null && e.getNegativeTtlUntil().isAfter(Instant.now()));
            if (negativeEntry.isPresent()) {
                return ResolutionResult.notFound("negative cache active");
            }

            ReentrantLock lock = pathLocks.computeIfAbsent(artifactPath, k -> new ReentrantLock());
            lock.lock();
            try {
                cached = contentStore.read(artifactPath);
                if (cached.isPresent()) {
                    Optional<ArtifactCacheEntry> cachedEntry = cacheEntryRepository.findByArtifactPath(artifactPath);
                    if (metadataPath && cachedEntry.isPresent() && cachedEntry.get().getStatus() == ArtifactCacheStatus.READY
                            && updatePolicy.shouldRevalidate(cachedEntry.get().getLastValidatedAt(), Instant.now())) {
                        ResolutionResult metadataResult = revalidateMetadata(artifactPath, cached.get(), cachedEntry.get());
                        if (metadataResult != null) {
                            return metadataResult;
                        }
                    }
                    markHit(artifactPath);
                    log.info("artifactProxy.cache hit path={}", artifactPath);
                    return ResolutionResult.cached(cached.get().path());
                }

                RemoteFetchResult remote = remoteFetchService.fetchFirstSuccess(artifactPath);
                if (!remote.ok()) {
                    if (remote.statusCode() == 404) {
                        createNegativeEntry(artifactPath);
                        return ResolutionResult.notFound("not found on any remote");
                    }
                    if (remote.statusCode() >= 400 && remote.statusCode() < 500) {
                        createNegativeEntry(artifactPath);
                    }
                    return ResolutionResult.failed(502, remote.error() == null ? "upstream error" : remote.error());
                }

                long expectedSize = remote.contentLength() == null || remote.contentLength() < 0 ? -1L : remote.contentLength();
                WriteSession writeSession = contentStore.beginWrite(artifactPath, new ArtifactWriteMetadata(expectedSize));
                boolean acquired = false;
                try (remote) {
                    acquireStreamPermit();
                    acquired = true;
                    log.debug("artifactProxy.download.start path={} source={} expectedSize={}",
                            artifactPath,
                            remote.remote() == null ? "unknown" : remote.remote().getName(),
                            expectedSize);
                    StreamCopyResult streamCopyResult = contentStore.write(writeSession, remote.body());
                    contentStore.commit(writeSession);
                    upsertReadyEntry(artifactPath, remote, streamCopyResult.sizeBytes(), streamCopyResult.sha1(), streamCopyResult.sha256(), classifyContentKind(artifactPath));
                    log.debug("artifactProxy.download.complete path={} source={} sizeBytes={}",
                            artifactPath,
                            remote.remote() == null ? "unknown" : remote.remote().getName(),
                            streamCopyResult.sizeBytes());
                    log.info("artifactProxy.downloaded path={} source={} sizeBytes={}",
                            artifactPath,
                            remote.remote() == null ? "unknown" : remote.remote().getName(),
                            streamCopyResult.sizeBytes());
                } catch (Exception e) {
                    contentStore.abort(writeSession);
                    log.debug("artifactProxy.download.abort path={} reason={}", artifactPath, e.getMessage());
                    throw e;
                } finally {
                    if (acquired) {
                        activeStreamPermits.release();
                    }
                }
                StoredArtifact storedArtifact = contentStore.read(artifactPath).orElseThrow();
                return ResolutionResult.fetched(storedArtifact.path(), remote.remote() == null ? null : remote.remote().getName());
            } finally {
                lock.unlock();
            }
        } catch (IOException e) {
            return ResolutionResult.failed(500, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ArtifactTreeNode listTree(String prefix, int depth, int limit) throws IOException {
        return contentStore.listTree(prefix, depth, limit);
    }

    @Transactional
    public void purgePath(String path) throws IOException {
        String normalized = sanitize(path);
        contentStore.delete(normalized);
        cacheEntryRepository.findByArtifactPath(normalized).ifPresent(cacheEntryRepository::delete);
    }

    @Transactional
    public int cleanupExpiredNegativeEntries() {
        int deleted = cacheEntryRepository.deleteExpiredNegativeEntries(ArtifactCacheStatus.NEGATIVE, Instant.now());
        if (deleted > 0) {
            log.debug("artifactProxy.cleanup negativeEntriesDeleted={}", deleted);
        }
        return deleted;
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

    private void markHit(String artifactPath) {
        cacheEntryRepository.findByArtifactPath(artifactPath).ifPresent(entry -> {
            entry.setLastAccessAt(Instant.now());
            entry.setHitCount((entry.getHitCount() == null ? 0 : entry.getHitCount()) + 1);
            cacheEntryRepository.save(entry);
        });
    }

    private void createNegativeEntry(String artifactPath) {
        ArtifactCacheEntry entry = cacheEntryRepository.findByArtifactPath(artifactPath).orElseGet(ArtifactCacheEntry::new);
        entry.setArtifactPath(artifactPath);
        entry.setStatus(ArtifactCacheStatus.NEGATIVE);
        entry.setNegativeTtlUntil(Instant.now().plusSeconds(Math.max(10, properties.getNegativeCacheTtlSeconds())));
        entry.setCachedAt(Instant.now());
        entry.setLastAccessAt(Instant.now());
        entry.setLastValidatedAt(Instant.now());
        entry.setContentKind(classifyContentKind(artifactPath));
        cacheEntryRepository.save(entry);
    }

    private void upsertReadyEntry(String artifactPath, RemoteFetchResult remote, long sizeBytes, String sha1, String sha256, ArtifactContentKind contentKind) {
        ArtifactCacheEntry entry = cacheEntryRepository.findByArtifactPath(artifactPath).orElseGet(ArtifactCacheEntry::new);
        entry.setArtifactPath(artifactPath);
        entry.setStatus(ArtifactCacheStatus.READY);
        entry.setNegativeTtlUntil(null);
        entry.setSourceRemote(remote.remote());
        entry.setSizeBytes(sizeBytes);
        entry.setChecksumSha1(sha1);
        entry.setChecksumSha256(sha256);
        entry.setCachedAt(Instant.now());
        entry.setLastAccessAt(Instant.now());
        entry.setLastValidatedAt(Instant.now());
        entry.setEtag(remote.etag());
        entry.setLastModifiedHttp(remote.lastModified());
        entry.setContentKind(contentKind);
        entry.setHitCount((entry.getHitCount() == null ? 0 : entry.getHitCount()) + 1);
        cacheEntryRepository.save(entry);
    }

    private ResolutionResult revalidateMetadata(String artifactPath, StoredArtifact cached, ArtifactCacheEntry entry) throws IOException {
        RemoteFetchResult remote = remoteFetchService.revalidateMetadata(
                artifactPath,
                properties.getMetadata().isConditionalRevalidate() ? entry.getEtag() : null,
                properties.getMetadata().isConditionalRevalidate() ? entry.getLastModifiedHttp() : null
        );

        if (remote.statusCode() == 304) {
            entry.setLastValidatedAt(Instant.now());
            cacheEntryRepository.save(entry);
            markHit(artifactPath);
            log.info("artifactProxy.metadata revalidate304 path={}", artifactPath);
            return ResolutionResult.cached(cached.path());
        }

        if (remote.ok()) {
            long expectedSize = remote.contentLength() == null || remote.contentLength() < 0 ? -1L : remote.contentLength();
            WriteSession writeSession = contentStore.beginWrite(artifactPath, new ArtifactWriteMetadata(expectedSize));
            boolean acquired = false;
            try (remote) {
                acquireStreamPermit();
                acquired = true;
                log.debug("artifactProxy.download.start path={} source={} expectedSize={}",
                        artifactPath,
                        remote.remote() == null ? "unknown" : remote.remote().getName(),
                        expectedSize);
                StreamCopyResult streamCopyResult = contentStore.write(writeSession, remote.body());
                contentStore.commit(writeSession);
                upsertReadyEntry(artifactPath, remote, streamCopyResult.sizeBytes(), streamCopyResult.sha1(), streamCopyResult.sha256(), ArtifactContentKind.METADATA);
                log.debug("artifactProxy.download.complete path={} source={} sizeBytes={}",
                        artifactPath,
                        remote.remote() == null ? "unknown" : remote.remote().getName(),
                        streamCopyResult.sizeBytes());
                log.info("artifactProxy.metadata revalidate200 path={} source={} sizeBytes={}",
                        artifactPath,
                        remote.remote() == null ? "unknown" : remote.remote().getName(),
                        streamCopyResult.sizeBytes());
            } catch (Exception e) {
                contentStore.abort(writeSession);
                log.debug("artifactProxy.download.abort path={} reason={}", artifactPath, e.getMessage());
                throw e;
            } finally {
                if (acquired) {
                    activeStreamPermits.release();
                }
            }
            StoredArtifact storedArtifact = contentStore.read(artifactPath).orElseThrow();
            return ResolutionResult.fetched(storedArtifact.path(), remote.remote() == null ? null : remote.remote().getName());
        }

        if (remote.statusCode() == 404) {
            contentStore.delete(artifactPath);
            cacheEntryRepository.findByArtifactPath(artifactPath).ifPresent(cacheEntryRepository::delete);
            log.info("artifactProxy.metadata invalidated404 path={}", artifactPath);
            return ResolutionResult.notFound("metadata not found on any remote");
        }

        boolean staleAllowed = properties.getMetadata().isServeStaleOnError()
                && entry.getCachedAt() != null
                && entry.getCachedAt().isAfter(Instant.now().minusSeconds(Math.max(1, properties.getMetadata().getMaxStaleMinutes()) * 60L));

        if (staleAllowed) {
            entry.setLastValidatedAt(Instant.now());
            cacheEntryRepository.save(entry);
            markHit(artifactPath);
            log.warn("artifactProxy.metadata stale-served path={} reason={}", artifactPath, remote.error());
            return ResolutionResult.cached(cached.path());
        }

        return ResolutionResult.failed(502, remote.error() == null ? "metadata revalidation failed" : remote.error());
    }

    private boolean isMetadataPath(String artifactPath) {
        return artifactPath.endsWith("maven-metadata.xml");
    }

    private void acquireStreamPermit() {
        try {
            activeStreamPermits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for stream permit", e);
        }
    }

    private ArtifactContentKind classifyContentKind(String artifactPath) {
        if (isMetadataPath(artifactPath)) {
            return ArtifactContentKind.METADATA;
        }
        if (artifactPath.endsWith(".sha1") || artifactPath.endsWith(".sha256") || artifactPath.endsWith(".md5")) {
            return ArtifactContentKind.CHECKSUM;
        }
        if (artifactPath.endsWith(".asc")) {
            return ArtifactContentKind.SIGNATURE;
        }
        if (artifactPath.endsWith(".jar") || artifactPath.endsWith(".pom") || artifactPath.endsWith(".war") || artifactPath.endsWith(".module")) {
            return ArtifactContentKind.ARTIFACT;
        }
        return ArtifactContentKind.OTHER;
    }

    public record ResolutionResult(int status, java.nio.file.Path path, boolean cacheHit, String message, String source) {
        public static ResolutionResult cached(java.nio.file.Path path) {
            return new ResolutionResult(200, path, true, "cache hit", "cache");
        }

        public static ResolutionResult fetched(java.nio.file.Path path, String source) {
            return new ResolutionResult(200, path, false, "fetched from upstream", source);
        }

        public static ResolutionResult notFound(String message) {
            return new ResolutionResult(404, null, false, message, null);
        }

        public static ResolutionResult failed(int status, String message) {
            return new ResolutionResult(status, null, false, message, null);
        }

        public InputStream stream() throws IOException {
            if (path == null) {
                return InputStream.nullInputStream();
            }
            return Files.newInputStream(path);
        }

        public long size() throws IOException {
            if (path == null) {
                return 0L;
            }
            return Files.size(path);
        }
    }
}
