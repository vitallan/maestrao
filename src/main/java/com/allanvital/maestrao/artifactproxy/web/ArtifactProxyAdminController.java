package com.allanvital.maestrao.artifactproxy.web;

import com.allanvital.maestrao.artifactproxy.model.ArtifactRemote;
import com.allanvital.maestrao.artifactproxy.model.ArtifactRemoteAuthType;
import com.allanvital.maestrao.artifactproxy.service.ArtifactCacheService;
import com.allanvital.maestrao.artifactproxy.service.ArtifactProxyMetricsService;
import com.allanvital.maestrao.artifactproxy.service.ArtifactRemoteService;
import com.allanvital.maestrao.artifactproxy.service.storage.ArtifactTreeNode;
import com.allanvital.maestrao.model.Credential;
import com.allanvital.maestrao.repository.CredentialRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/artifact-proxy")
@ConditionalOnProperty(prefix = "maestrao.artifact-proxy", name = "enabled", havingValue = "true")
public class ArtifactProxyAdminController {

    private final ArtifactRemoteService remoteService;
    private final ArtifactProxyMetricsService metricsService;
    private final ArtifactCacheService cacheService;
    private final CredentialRepository credentialRepository;

    public ArtifactProxyAdminController(ArtifactRemoteService remoteService,
                                        ArtifactProxyMetricsService metricsService,
                                        ArtifactCacheService cacheService,
                                        CredentialRepository credentialRepository) {
        this.remoteService = remoteService;
        this.metricsService = metricsService;
        this.cacheService = cacheService;
        this.credentialRepository = credentialRepository;
    }

    @GetMapping("/remotes")
    public List<RemoteDto> remotes() {
        return remoteService.findAll().stream().map(RemoteDto::from).toList();
    }

    @PostMapping("/remotes")
    public RemoteDto saveRemote(@RequestBody SaveRemoteRequest request) {
        ArtifactRemote remote = request.id == null
                ? new ArtifactRemote()
                : remoteService.findById(request.id).orElse(new ArtifactRemote());
        remote.setName(request.name);
        remote.setBaseUrl(request.baseUrl);
        remote.setEnabled(request.enabled);
        remote.setTimeoutMs(request.timeoutMs);
        remote.setAuthType(request.authType == null ? ArtifactRemoteAuthType.NONE : request.authType);
        if (request.credentialId != null) {
            Credential credential = credentialRepository.findById(request.credentialId).orElseThrow();
            remote.setCredential(credential);
        } else {
            remote.setCredential(null);
        }
        return RemoteDto.from(remoteService.save(remote));
    }

    @DeleteMapping("/remotes/{id}")
    public void deleteRemote(@PathVariable Long id) {
        remoteService.delete(id);
    }

    @GetMapping("/metrics")
    public ArtifactProxyMetricsService.Snapshot metrics() {
        return metricsService.snapshot();
    }

    @GetMapping("/tree")
    public ArtifactTreeNode tree(@RequestParam(defaultValue = "") String prefix,
                                 @RequestParam(defaultValue = "4") int depth,
                                 @RequestParam(defaultValue = "500") int limit) throws IOException {
        return cacheService.listTree(prefix, depth, limit);
    }

    @DeleteMapping("/cache")
    public ResponseEntity<Void> purge(@RequestParam String path) throws IOException {
        cacheService.purgePath(path);
        return ResponseEntity.noContent().build();
    }

    public record SaveRemoteRequest(
            Long id,
            String name,
            String baseUrl,
            ArtifactRemoteAuthType authType,
            Long credentialId,
            Integer timeoutMs,
            Boolean enabled
    ) {
    }

    public record RemoteDto(
            Long id,
            String name,
            String baseUrl,
            ArtifactRemoteAuthType authType,
            Long credentialId,
            Integer timeoutMs,
            Boolean enabled
    ) {
        static RemoteDto from(ArtifactRemote remote) {
            return new RemoteDto(
                    remote.getId(),
                    remote.getName(),
                    remote.getBaseUrl(),
                    remote.getAuthType(),
                    remote.getCredential() == null ? null : remote.getCredential().getId(),
                    remote.getTimeoutMs(),
                    remote.getEnabled()
            );
        }
    }
}
