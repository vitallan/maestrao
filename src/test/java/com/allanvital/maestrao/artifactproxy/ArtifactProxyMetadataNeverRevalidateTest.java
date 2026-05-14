package com.allanvital.maestrao.artifactproxy;

import com.allanvital.maestrao.artifactproxy.model.ArtifactRemote;
import com.allanvital.maestrao.artifactproxy.model.ArtifactRemoteAuthType;
import com.allanvital.maestrao.artifactproxy.service.ArtifactRemoteService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "maestrao.artifact-proxy.enabled=true",
        "maestrao.artifact-proxy.metadata.update-policy=never",
        "maestrao.artifact-proxy.metadata.conditional-revalidate=true"
})
class ArtifactProxyMetadataNeverRevalidateTest {

    private static Path cacheDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        cacheDir = Files.createTempDirectory("artifact-proxy-meta-never");
        registry.add("maestrao.artifact-proxy.cache-root", () -> cacheDir.toString());
    }

    @Autowired
    private ArtifactRemoteService remoteService;

    @LocalServerPort
    private int port;

    private MockWebServer server;

    @BeforeEach
    void setup() throws Exception {
        server = new MockWebServer();
        server.start();

        server.enqueue(new MockResponse().setResponseCode(200).setBody("<metadata/>"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("<metadata-changed/>"));

        ArtifactRemote remote = new ArtifactRemote();
        remote.setName("central");
        remote.setBaseUrl(server.url("/repo/").toString());
        remote.setAuthType(ArtifactRemoteAuthType.NONE);
        remote.setEnabled(true);
        remote.setTimeoutMs(5000);
        remoteService.save(remote);
    }

    @AfterEach
    void teardown() throws Exception {
        server.shutdown();
    }

    @Test
    void metadataNeverPolicyServesCachedWithoutRevalidation() throws Exception {
        HttpResponse<byte[]> first = get("/maven/org/acme/demo/maven-metadata.xml");
        assertEquals(200, first.statusCode());

        HttpResponse<byte[]> second = get("/maven/org/acme/demo/maven-metadata.xml");
        assertEquals(200, second.statusCode());
        assertEquals("<metadata/>", new String(second.body()));

        assertEquals(1, server.getRequestCount());
    }

    private HttpResponse<byte[]> get(String path) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }
}
