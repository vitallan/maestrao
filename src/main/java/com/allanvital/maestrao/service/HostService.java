package com.allanvital.maestrao.service;

import com.allanvital.maestrao.model.Credential;
import com.allanvital.maestrao.model.Host;
import com.allanvital.maestrao.repository.CredentialRepository;
import com.allanvital.maestrao.repository.HostRepository;
import com.allanvital.maestrao.service.ssh.SshConnectionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Service
public class HostService {

    private static final Logger log = LoggerFactory.getLogger(HostService.class);

    private final HostRepository hostRepository;
    private final CredentialRepository credentialRepository;
    private final SshConnectionService sshConnectionService;

    public HostService(HostRepository hostRepository,
                       CredentialRepository credentialRepository,
                       SshConnectionService sshConnectionService) {
        this.hostRepository = hostRepository;
        this.credentialRepository = credentialRepository;
        this.sshConnectionService = sshConnectionService;
    }

    @Transactional(readOnly = true)
    public Page<Host> findAll(Pageable pageable) {
        return hostRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public long count() {
        return hostRepository.count();
    }

    @Transactional(readOnly = true)
    public List<Host> findAllForSelection() {
        return hostRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    @Transactional
    public Host create(String name,
                       String ip,
                       Integer sshPort,
                       String description,
                       Long credentialId,
                       boolean gatherHealthMetrics) {
        Host host = new Host();
        setHostInternals(host, name, ip, sshPort, description, credentialId, gatherHealthMetrics);

        Host saved = hostRepository.save(host);
        log.info("host.create id={} name={} ip={} port={} credentialId={}",
                saved.getId(), saved.getName(), saved.getIp(), saved.getSshPort(),
                saved.getCredential() == null ? null : saved.getCredential().getId());
        return saved;
    }

    @Transactional
    public Host update(Long id,
                       String name,
                       String ip,
                       Integer sshPort,
                       String description,
                       Long credentialId,
                       boolean gatherHealthMetrics) {

        Host host = hostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Host not found: " + id));
        setHostInternals(host, name, ip, sshPort, description, credentialId, gatherHealthMetrics);

        Host saved = hostRepository.save(host);
        log.info("host.update id={} name={} ip={} port={} credentialId={}",
                saved.getId(), saved.getName(), saved.getIp(), saved.getSshPort(),
                saved.getCredential() == null ? null : saved.getCredential().getId());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        hostRepository.deleteById(id);
        log.info("host.delete id={}", id);
    }

    @Transactional(readOnly = true)
    public HostConnectionTestResult testConnection(Long id) {
        Host host = hostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Host not found: " + id));

        log.info("host.testConnection id={} ip={} port={}", id, host.getIp(), host.getSshPort());
        HostConnectionTestResult result = sshConnectionService.test(host.getIp(), host.getSshPort(), host.getCredential());
        log.info("host.testConnectionResult id={} ok={} message={}", id, result.success(), result.message());
        return result;
    }

    @Transactional(readOnly = true)
    public HostConnectionTestResult testConnection(String ip, Integer sshPort, Long credentialId) {
        Credential credential = findCredential(credentialId);
        String normalizedIp = normalizeRequired(ip, "ip");
        Integer port = normalizePort(sshPort);
        log.info("host.testConnection ip={} port={} credentialId={}", normalizedIp, port, credentialId);
        HostConnectionTestResult result = sshConnectionService.test(normalizedIp, port, credential);
        log.info("host.testConnectionResult ip={} ok={} message={}", normalizedIp, result.success(), result.message());
        return result;
    }

    private Credential findCredential(Long credentialId) {
        if (credentialId == null) {
            throw new IllegalArgumentException("credential is required");
        }

        return credentialRepository.findById(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found: " + credentialId));
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

    private Integer normalizePort(Integer port) {
        if (port == null) {
            return 22;
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("ssh port must be between 1 and 65535");
        }

        return port;
    }

    private void setHostInternals(Host host,
                                  String name,
                                  String ip,
                                  Integer sshPort,
                                  String description,
                                  Long credentialId,
                                  boolean gatherHealthMetrics) {
        host.setName(normalizeRequired(name, "name"));
        host.setIp(normalizeRequired(ip, "ip"));
        host.setSshPort(normalizePort(sshPort));
        host.setDescription(normalizeOptional(description));
        host.setCredential(findCredential(credentialId));
        host.setGatherHealthMetrics(gatherHealthMetrics);
    }

}
