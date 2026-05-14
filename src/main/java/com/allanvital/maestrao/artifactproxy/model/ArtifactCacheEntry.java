package com.allanvital.maestrao.artifactproxy.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "artifact_cache_entries", indexes = {
        @Index(name = "idx_artifact_cache_path", columnList = "artifact_path", unique = true),
        @Index(name = "idx_artifact_cache_status_ttl", columnList = "status,negative_ttl_until")
})
public class ArtifactCacheEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "artifact_path", nullable = false, length = 700, unique = true)
    private String artifactPath;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "checksum_sha1", length = 64)
    private String checksumSha1;

    @Column(name = "checksum_sha256", length = 128)
    private String checksumSha256;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_remote_id")
    private ArtifactRemote sourceRemote;

    @Column(name = "cached_at")
    private Instant cachedAt;

    @Column(name = "last_access_at")
    private Instant lastAccessAt;

    @Column(name = "hit_count", nullable = false)
    private Long hitCount = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArtifactCacheStatus status = ArtifactCacheStatus.READY;

    @Column(name = "negative_ttl_until")
    private Instant negativeTtlUntil;

    @Column(name = "etag", length = 300)
    private String etag;

    @Column(name = "last_modified_http", length = 120)
    private String lastModifiedHttp;

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_kind", nullable = false, length = 20)
    private ArtifactContentKind contentKind = ArtifactContentKind.OTHER;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getArtifactPath() {
        return artifactPath;
    }

    public void setArtifactPath(String artifactPath) {
        this.artifactPath = artifactPath;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getChecksumSha1() {
        return checksumSha1;
    }

    public void setChecksumSha1(String checksumSha1) {
        this.checksumSha1 = checksumSha1;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public void setChecksumSha256(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }

    public ArtifactRemote getSourceRemote() {
        return sourceRemote;
    }

    public void setSourceRemote(ArtifactRemote sourceRemote) {
        this.sourceRemote = sourceRemote;
    }

    public Instant getCachedAt() {
        return cachedAt;
    }

    public void setCachedAt(Instant cachedAt) {
        this.cachedAt = cachedAt;
    }

    public Instant getLastAccessAt() {
        return lastAccessAt;
    }

    public void setLastAccessAt(Instant lastAccessAt) {
        this.lastAccessAt = lastAccessAt;
    }

    public Long getHitCount() {
        return hitCount;
    }

    public void setHitCount(Long hitCount) {
        this.hitCount = hitCount;
    }

    public ArtifactCacheStatus getStatus() {
        return status;
    }

    public void setStatus(ArtifactCacheStatus status) {
        this.status = status;
    }

    public Instant getNegativeTtlUntil() {
        return negativeTtlUntil;
    }

    public void setNegativeTtlUntil(Instant negativeTtlUntil) {
        this.negativeTtlUntil = negativeTtlUntil;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public String getLastModifiedHttp() {
        return lastModifiedHttp;
    }

    public void setLastModifiedHttp(String lastModifiedHttp) {
        this.lastModifiedHttp = lastModifiedHttp;
    }

    public Instant getLastValidatedAt() {
        return lastValidatedAt;
    }

    public void setLastValidatedAt(Instant lastValidatedAt) {
        this.lastValidatedAt = lastValidatedAt;
    }

    public ArtifactContentKind getContentKind() {
        return contentKind;
    }

    public void setContentKind(ArtifactContentKind contentKind) {
        this.contentKind = contentKind;
    }
}
