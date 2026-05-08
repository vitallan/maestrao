package com.allanvital.maestrao.service;

import com.allanvital.maestrao.TestConfig;
import com.allanvital.maestrao.model.Credential;
import com.allanvital.maestrao.model.CredentialType;
import com.allanvital.maestrao.model.DecryptedCredential;
import com.allanvital.maestrao.model.Host;
import com.allanvital.maestrao.repository.CredentialRepository;
import com.allanvital.maestrao.repository.HostRepository;
import com.allanvital.maestrao.ssh.FakeSshClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@SpringBootTest
@Import(TestConfig.class)
@Transactional
public class HostServiceTest {

    @Autowired
    private HostService hostService;

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private HostRepository hostRepository;

    @Autowired
    private CredentialRepository credentialRepository;

    @Autowired
    private FakeSshClient fakeSshClient;

    @BeforeEach
    void setUp() {
        hostRepository.deleteAll();
        hostRepository.flush();

        credentialRepository.deleteAll();
        credentialRepository.flush();

        fakeSshClient.reset();
    }

    @Test
    void shouldCreateHostWithDefaultSshPortAndNormalizedValues() {
        Credential credential = createPasswordCredential("Main credential", "root", "secret");

        Host host = hostService.create(
                "  Production server  ",
                "  192.168.0.10  ",
                null,
                "  Main app server  ",
                credential.getId()
        );

        assertNotNull(host.getId());
        assertEquals("Production server", host.getName());
        assertEquals("192.168.0.10", host.getIp());
        assertEquals(22, host.getSshPort());
        assertEquals("Main app server", host.getDescription());
        assertEquals(credential.getId(), host.getCredential().getId());
        assertNotNull(host.getCreatedAt());
        assertNotNull(host.getUpdatedAt());
    }

    @Test
    void shouldCreateHostWithCustomSshPortAndNullDescription() {
        Credential credential = createPasswordCredential("Main credential", "root", "secret");

        Host host = hostService.create(
                "Production server",
                "server.local",
                2222,
                "   ",
                credential.getId()
        );

        assertNotNull(host.getId());
        assertEquals("Production server", host.getName());
        assertEquals("server.local", host.getIp());
        assertEquals(2222, host.getSshPort());
        assertNull(host.getDescription());
    }

    @Test
    void shouldUpdateHost() {
        Credential oldCredential = createPasswordCredential("Old credential", "old-user", "old-secret");
        Credential newCredential = createPasswordCredential("New credential", "new-user", "new-secret");

        Host host = hostService.create(
                "Old host",
                "192.168.0.10",
                22,
                "Old description",
                oldCredential.getId()
        );

        Host updatedHost = hostService.update(
                host.getId(),
                "  New host  ",
                "  192.168.0.20  ",
                2222,
                "  New description  ",
                newCredential.getId()
        );

        assertEquals(host.getId(), updatedHost.getId());
        assertEquals("New host", updatedHost.getName());
        assertEquals("192.168.0.20", updatedHost.getIp());
        assertEquals(2222, updatedHost.getSshPort());
        assertEquals("New description", updatedHost.getDescription());
        assertEquals(newCredential.getId(), updatedHost.getCredential().getId());
    }

    @Test
    void shouldDeleteHost() {
        Credential credential = createPasswordCredential("Main credential", "root", "secret");

        Host host = hostService.create(
                "Host to delete",
                "192.168.0.10",
                22,
                null,
                credential.getId()
        );

        hostService.delete(host.getId());

        assertFalse(hostRepository.existsById(host.getId()));
        assertEquals(0, hostService.count());
    }

    @Test
    void shouldFindAllWithPagination() {
        Credential credential = createPasswordCredential("Main credential", "root", "secret");

        hostService.create("Host C", "192.168.0.30", 22, null, credential.getId());
        hostService.create("Host A", "192.168.0.10", 22, null, credential.getId());
        hostService.create("Host B", "192.168.0.20", 22, null, credential.getId());

        Page<Host> page = hostService.findAll(
                PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "name"))
        );

        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        assertEquals("Host A", page.getContent().get(0).getName());
        assertEquals("Host B", page.getContent().get(1).getName());
    }

    @Test
    void shouldRejectInvalidHostData() {
        Credential credential = createPasswordCredential("Main credential", "root", "secret");

        assertThrows(IllegalArgumentException.class, () ->
                hostService.create("   ", "192.168.0.10", 22, null, credential.getId())
        );

        assertThrows(IllegalArgumentException.class, () ->
                hostService.create("Host", "   ", 22, null, credential.getId())
        );

        assertThrows(IllegalArgumentException.class, () ->
                hostService.create("Host", "192.168.0.10", 0, null, credential.getId())
        );

        assertThrows(IllegalArgumentException.class, () ->
                hostService.create("Host", "192.168.0.10", 65536, null, credential.getId())
        );

        assertThrows(IllegalArgumentException.class, () ->
                hostService.create("Host", "192.168.0.10", 22, null, null)
        );

        assertThrows(IllegalArgumentException.class, () ->
                hostService.create("Host", "192.168.0.10", 22, null, 999L)
        );
    }

    @Test
    void shouldTestSavedHostConnectionUsingDecryptedCredential() {
        Credential credential = createPasswordCredential("Main credential", "root", "super-secret");

        Host host = hostService.create(
                "Production server",
                "192.168.0.10",
                2222,
                null,
                credential.getId()
        );

        fakeSshClient.setNextResult(HostConnectionTestResult.success("Connection successful"));

        HostConnectionTestResult result = hostService.testConnection(host.getId());

        assertTrue(result.success());
        assertEquals("Connection successful", result.message());

        assertEquals("192.168.0.10", fakeSshClient.getLastIp());
        assertEquals(2222, fakeSshClient.getLastSshPort());

        DecryptedCredential lastCredential = fakeSshClient.getLastCredential();

        assertEquals(credential.getId(), lastCredential.getId());
        assertEquals("root", lastCredential.getUsername());
        assertEquals("super-secret", lastCredential.getDecryptedSecret());
    }

    @Test
    void shouldTestUnsavedHostConnectionDataUsingCredentialId() {
        Credential credential = createPasswordCredential("Main credential", "root", "super-secret");

        fakeSshClient.setNextResult(HostConnectionTestResult.success("Connection successful"));

        HostConnectionTestResult result = hostService.testConnection(
                "  server.local  ",
                null,
                credential.getId()
        );

        assertTrue(result.success());
        assertEquals("server.local", fakeSshClient.getLastIp());
        assertEquals(22, fakeSshClient.getLastSshPort());
        assertEquals("super-secret", fakeSshClient.getLastCredential().getDecryptedSecret());
    }

    @Test
    void shouldReturnFailureWhenSshClientFails() {
        Credential credential = createPasswordCredential("Main credential", "root", "super-secret");

        Host host = hostService.create(
                "Production server",
                "192.168.0.10",
                22,
                null,
                credential.getId()
        );

        fakeSshClient.setNextResult(HostConnectionTestResult.failure("SSH connection failed"));
        HostConnectionTestResult result = hostService.testConnection(host.getId());

        assertFalse(result.success());
        assertEquals("SSH connection failed", result.message());
    }

    private Credential createPasswordCredential(String name, String username, String secret) {
        return credentialService.create(
                name,
                CredentialType.PASSWORD,
                username,
                secret,
                null
        );
    }

}
