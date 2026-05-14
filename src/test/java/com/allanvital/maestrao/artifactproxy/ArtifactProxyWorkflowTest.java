package com.allanvital.maestrao.artifactproxy;

import com.allanvital.maestrao.artifactproxy.model.ArtifactRemote;
import com.allanvital.maestrao.artifactproxy.model.ArtifactRemoteAuthType;
import com.allanvital.maestrao.artifactproxy.service.ArtifactCacheService;
import com.allanvital.maestrao.artifactproxy.service.ArtifactProxyMetricsService;
import com.allanvital.maestrao.artifactproxy.service.ArtifactRemoteService;
import com.allanvital.maestrao.model.Credential;
import com.allanvital.maestrao.model.CredentialType;
import com.allanvital.maestrao.service.CredentialService;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArtifactProxyWorkflowTest {

    private static Path cacheDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        cacheDir = Files.createTempDirectory("artifact-proxy-test");
        registry.add("maestrao.artifact-proxy.enabled", () -> "true");
        registry.add("maestrao.artifact-proxy.cache-root", () -> cacheDir.toString());
        registry.add("maestrao.artifact-proxy.max-inflight-fetches", () -> "3");
    }

    @Autowired
    private ArtifactRemoteService artifactRemoteService;

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private ArtifactProxyMetricsService metricsService;

    @Autowired
    private ArtifactCacheService cacheService;

    private MockWebServer slowServer;
    private MockWebServer fastServer;
    private MockWebServer authServer;

    @LocalServerPort
    private int port;

    @BeforeEach
    void setup() throws Exception {
        slowServer = new MockWebServer();
        fastServer = new MockWebServer();
        authServer = new MockWebServer();

        slowServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.contains("demo/artifact/1.0/artifact-1.0.jar")) {
                    return new MockResponse().setBody("slow-jar").setHeadersDelay(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        fastServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.contains("demo/artifact/1.0/artifact-1.0.jar")) {
                    return new MockResponse().setBody("fast-jar");
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        authServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String auth = request.getHeader("Authorization");
                String path = request.getPath();
                if (path != null && path.contains("private/demo/1.0/demo-1.0.pom") && auth != null && auth.startsWith("Basic ")) {
                    return new MockResponse().setBody("<project/>");
                }
                if (path != null && path.contains("private/demo/1.0/demo-1.0.pom")) {
                    return new MockResponse().setResponseCode(401);
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        slowServer.start();
        fastServer.start();
        authServer.start();

        Credential cred = credentialService.create("artifact-auth", CredentialType.PASSWORD, "user", "pass", "artifact test auth");

        ArtifactRemote slow = new ArtifactRemote();
        slow.setName("slow");
        slow.setBaseUrl(slowServer.url("/repo/").toString());
        slow.setAuthType(ArtifactRemoteAuthType.NONE);
        slow.setEnabled(true);
        slow.setTimeoutMs(5_000);
        artifactRemoteService.save(slow);

        ArtifactRemote fast = new ArtifactRemote();
        fast.setName("fast");
        fast.setBaseUrl(fastServer.url("/repo/").toString());
        fast.setAuthType(ArtifactRemoteAuthType.NONE);
        fast.setEnabled(true);
        fast.setTimeoutMs(5_000);
        artifactRemoteService.save(fast);

        ArtifactRemote auth = new ArtifactRemote();
        auth.setName("auth");
        auth.setBaseUrl(authServer.url("/repo/").toString());
        auth.setAuthType(ArtifactRemoteAuthType.BASIC);
        auth.setCredential(cred);
        auth.setEnabled(true);
        auth.setTimeoutMs(5_000);
        artifactRemoteService.save(auth);
    }

    @AfterEach
    void cleanup() throws Exception {
        slowServer.shutdown();
        fastServer.shutdown();
        authServer.shutdown();
    }

    @Test
    void fullWorkflow_fetchesFastestAndCachesAndSupportsAuthAndTree() throws Exception {
        HttpResponse<byte[]> first = get("/maven/demo/artifact/1.0/artifact-1.0.jar");
        assertEquals(200, first.statusCode());
        assertArrayEquals("fast-jar".getBytes(), first.body());

        int fastCallsAfterFirst = fastServer.getRequestCount();
        int slowCallsAfterFirst = slowServer.getRequestCount();

        HttpResponse<byte[]> second = get("/maven/demo/artifact/1.0/artifact-1.0.jar");
        assertEquals(200, second.statusCode());
        assertArrayEquals("fast-jar".getBytes(), second.body());

        assertEquals(fastCallsAfterFirst, fastServer.getRequestCount(), "second request should be cache hit");
        assertEquals(slowCallsAfterFirst, slowServer.getRequestCount(), "second request should not hit slow remote");

        HttpResponse<byte[]> authResult = get("/maven/private/demo/1.0/demo-1.0.pom");
        assertEquals(200, authResult.statusCode());
        assertArrayEquals("<project/>".getBytes(), authResult.body());

        HttpResponse<byte[]> miss = get("/maven/unknown/demo/0.0.1/demo-0.0.1.jar");
        assertEquals(404, miss.statusCode());

        ArtifactProxyMetricsService.Snapshot snapshot = metricsService.snapshot();
        assertTrue(snapshot.fileCount() >= 2);

        var tree = cacheService.listTree("", 6, 1000);
        assertNotNull(tree);
        assertTrue(tree.isDirectory());
        assertFalse(tree.getChildren().isEmpty());
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
