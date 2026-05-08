package com.allanvital.maestrao.service;

import com.allanvital.maestrao.model.Credential;
import com.allanvital.maestrao.model.CredentialType;
import com.allanvital.maestrao.repository.CredentialRepository;
import com.allanvital.maestrao.security.CredentialCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Service
public class CredentialService {

    private static final Logger log = LoggerFactory.getLogger(CredentialService.class);

    private final CredentialRepository credentialRepository;
    private final CredentialCryptoService credentialCryptoService;

    public CredentialService(CredentialRepository credentialRepository,
                             CredentialCryptoService credentialCryptoService) {
        this.credentialRepository = credentialRepository;
        this.credentialCryptoService = credentialCryptoService;
    }

    @Transactional(readOnly = true)
    public Page<Credential> findAll(Pageable pageable) {
        return credentialRepository.findAll(pageable);
    }

    @Transactional
    public Credential create(String name,
                             CredentialType type,
                             String username,
                             String plainSecret,
                             String description) {
        Credential credential = new Credential();
        credential.setName(normalizeRequired(name, "name"));
        credential.setType(type);
        credential.setUsername(normalizeOptional(username));
        credential.setEncryptedSecret(credentialCryptoService.encrypt(normalizeRequired(plainSecret, "secret")));
        credential.setDescription(normalizeOptional(description));

        Credential saved = credentialRepository.save(credential);
        log.info("credential.create id={} name={} type={} usernameSet={}",
                saved.getId(), saved.getName(), saved.getType(), saved.getUsername() != null);
        return saved;
    }

    @Transactional
    public Credential update(Long id,
                             String name,
                             CredentialType type,
                             String username,
                             String plainSecret,
                             String description) {
        Credential credential = credentialRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found: " + id));

        credential.setName(normalizeRequired(name, "name"));
        credential.setType(type);
        credential.setUsername(normalizeOptional(username));
        credential.setDescription(normalizeOptional(description));

        boolean secretChanged = plainSecret != null && !plainSecret.isBlank();
        if (secretChanged) {
            credential.setEncryptedSecret(credentialCryptoService.encrypt(plainSecret.trim()));
        }

        Credential saved = credentialRepository.save(credential);
        log.info("credential.update id={} name={} type={} secretChanged={}",
                saved.getId(), saved.getName(), saved.getType(), secretChanged);
        return saved;
    }

    @Transactional(readOnly = true)
    public long count() {
        return credentialRepository.count();
    }

    @Transactional
    public void delete(Long id) {
        credentialRepository.deleteById(id);
        log.info("credential.delete id={}", id);
    }

    @Transactional(readOnly = true)
    public String revealSecret(Long id) {
        Credential credential = credentialRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found: " + id));

        return credentialCryptoService.decrypt(credential.getEncryptedSecret());
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

}
