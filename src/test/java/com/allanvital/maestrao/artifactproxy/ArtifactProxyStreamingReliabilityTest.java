package com.allanvital.maestrao.artifactproxy;

import com.allanvital.maestrao.artifactproxy.model.ArtifactRemote;
import com.allanvital.maestrao.artifactproxy.model.ArtifactRemoteAuthType;
import com.allanvital.maestrao.artifactproxy.service.ArtifactRemoteService;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "maestrao.artifact-proxy.enabled=true"
})
class ArtifactProxyStreamingReliabilityTest {

    private static Path cacheDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        cacheDir = Files.createTempDirectory("artifact-proxy-streaming");
        registry.add("maestrao.artifact-proxy.cache-root", () -> cacheDir.toString());
        registry.add("maestrao.artifact-proxy.metadata.update-policy", () -> "interval:5");
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
    }

    @AfterEach
    void teardown() throws Exception {
        server.shutdown();
    }

    @Test
    void handlesLargeArtifactAndConcurrentRequestsWithSingleUpstreamFetch() throws Exception {
        byte[] payload = new byte[5 * 1024 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath() != null && request.getPath().contains("big/demo/1.0/demo-1.0.jar")) {
                    return new MockResponse().setResponseCode(200).setBody(new okio.Buffer().write(payload));
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        ArtifactRemote remote = new ArtifactRemote();
        remote.setName("large");
        remote.setBaseUrl(server.url("/repo/").toString());
        remote.setAuthType(ArtifactRemoteAuthType.NONE);
        remote.setEnabled(true);
        remote.setTimeoutMs(10000);
        remoteService.save(remote);

        HttpClient client = HttpClient.newHttpClient();
        List<CompletableFuture<HttpResponse<byte[]>>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/maven/big/demo/1.0/demo-1.0.jar"))
                    .GET()
                    .build();
            futures.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()));
        }

        for (CompletableFuture<HttpResponse<byte[]>> future : futures) {
            HttpResponse<byte[]> response = future.get(20, TimeUnit.SECONDS);
            assertEquals(200, response.statusCode());
            assertArrayEquals(payload, response.body());
        }

        assertEquals(1, server.getRequestCount(), "single-flight should avoid duplicate upstream downloads");
    }

    @Test
    void interruptedUpstreamDoesNotLeaveCorruptedCachedFile() throws Exception {
        byte[] partial = new byte[1024 * 64];
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(new okio.Buffer().write(partial))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));

        ArtifactRemote remote = new ArtifactRemote();
        remote.setName("flaky");
        remote.setBaseUrl(server.url("/repo/").toString());
        remote.setAuthType(ArtifactRemoteAuthType.NONE);
        remote.setEnabled(true);
        remote.setTimeoutMs(5000);
        remoteService.save(remote);

        HttpResponse<byte[]> first = get("/maven/flaky/demo/1.0/demo-1.0.jar");
        assertTrue(first.statusCode() >= 500);

        // second request with no queued success should fail again, but not serve corrupted cache
        HttpResponse<byte[]> second = get("/maven/flaky/demo/1.0/demo-1.0.jar");
        assertTrue(second.statusCode() >= 500 || second.statusCode() == 404);
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
