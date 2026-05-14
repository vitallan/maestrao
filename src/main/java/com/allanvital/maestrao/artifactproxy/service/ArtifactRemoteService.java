package com.allanvital.maestrao.artifactproxy.service;

import com.allanvital.maestrao.artifactproxy.model.ArtifactRemote;
import com.allanvital.maestrao.artifactproxy.model.ArtifactRemoteAuthType;
import com.allanvital.maestrao.artifactproxy.repository.ArtifactRemoteRepository;
import com.allanvital.maestrao.model.Credential;
import com.allanvital.maestrao.service.CredentialService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "maestrao.artifact-proxy", name = "enabled", havingValue = "true")
public class ArtifactRemoteService {

    private final ArtifactRemoteRepository artifactRemoteRepository;
    private final CredentialService credentialService;

    public ArtifactRemoteService(ArtifactRemoteRepository artifactRemoteRepository, CredentialService credentialService) {
        this.artifactRemoteRepository = artifactRemoteRepository;
        this.credentialService = credentialService;
    }

    @Transactional(readOnly = true)
    public List<ArtifactRemote> findEnabled() {
        return artifactRemoteRepository.findByEnabledTrueOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public List<ArtifactRemote> findAll() {
        return artifactRemoteRepository.findAll();
    }

    @Transactional
    public ArtifactRemote save(ArtifactRemote remote) {
        if (remote.getName() != null) {
            remote.setName(remote.getName().trim());
        }
        if (remote.getBaseUrl() != null) {
            remote.setBaseUrl(normalizeBaseUrl(remote.getBaseUrl()));
        }
        return artifactRemoteRepository.save(remote);
    }

    @Transactional(readOnly = true)
    public Optional<ArtifactRemote> findById(Long id) {
        return artifactRemoteRepository.findById(id);
    }

    @Transactional
    public void delete(Long id) {
        artifactRemoteRepository.deleteById(id);
    }

    public String authHeaderOrNull(ArtifactRemote remote) {
        if (remote.getAuthType() != ArtifactRemoteAuthType.BASIC) {
            return null;
        }
        Credential credential = remote.getCredential();
        if (credential == null || credential.getId() == null || credential.getUsername() == null) {
            return null;
        }
        String plainSecret = credentialService.revealSecret(credential.getId());
        String token = Base64.getEncoder().encodeToString((credential.getUsername() + ":" + plainSecret).getBytes());
        return "Basic " + token;
    }

    public Duration timeout(ArtifactRemote remote, int defaultMs) {
        int value = remote.getTimeoutMs() == null || remote.getTimeoutMs() <= 0 ? defaultMs : remote.getTimeoutMs();
        return Duration.ofMillis(value);
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim().replace(" ", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        return normalized;
    }
}
