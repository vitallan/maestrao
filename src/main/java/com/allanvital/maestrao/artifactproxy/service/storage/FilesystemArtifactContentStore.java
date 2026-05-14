package com.allanvital.maestrao.artifactproxy.service.storage;

import com.allanvital.maestrao.artifactproxy.config.ArtifactProxyProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "maestrao.artifact-proxy", name = "enabled", havingValue = "true")
public class FilesystemArtifactContentStore implements ArtifactContentStore {

    private final Path root;

    public FilesystemArtifactContentStore(ArtifactProxyProperties properties) throws IOException {
        this.root = Paths.get(properties.getCacheRoot()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public Optional<StoredArtifact> read(String artifactPath) throws IOException {
        Path path = resolveSafe(artifactPath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        return Optional.of(new StoredArtifact(path, Files.size(path)));
    }

    @Override
    public WriteSession beginWrite(String artifactPath, ArtifactWriteMetadata metadata) throws IOException {
        Path finalPath = resolveSafe(artifactPath);
        Path parent = finalPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String fileName = finalPath.getFileName().toString();
        Path tempFile = Files.createTempFile(parent == null ? root : parent, fileName + ".", ".part");
        return new WriteSession(artifactPath, tempFile, finalPath);
    }

    @Override
    public StreamCopyResult write(WriteSession session, InputStream data) throws IOException {
        MessageDigest sha1;
        MessageDigest sha256;
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException("unable to init digest", e);
        }
        long size = 0L;
        byte[] buffer = new byte[64 * 1024];
        try (var out = Files.newOutputStream(session.tempFile(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            int read;
            while ((read = data.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                out.write(buffer, 0, read);
                sha1.update(buffer, 0, read);
                sha256.update(buffer, 0, read);
                size += read;
            }
        }
        return new StreamCopyResult(
                size,
                HexFormat.of().formatHex(sha1.digest()),
                HexFormat.of().formatHex(sha256.digest())
        );
    }

    @Override
    public void commit(WriteSession session) throws IOException {
        Files.move(session.tempFile(), session.finalFile(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public void abort(WriteSession session) throws IOException {
        Files.deleteIfExists(session.tempFile());
    }

    @Override
    public boolean exists(String artifactPath) throws IOException {
        return Files.exists(resolveSafe(artifactPath));
    }

    @Override
    public void delete(String artifactPath) throws IOException {
        Path target = resolveSafe(artifactPath);
        if (Files.isDirectory(target)) {
            try (var walk = Files.walk(target)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
            }
            return;
        }
        Files.deleteIfExists(target);
    }

    @Override
    public ArtifactTreeNode listTree(String prefix, int depth, int limit) throws IOException {
        Path base = prefix == null || prefix.isBlank() ? root : resolveSafe(prefix);
        if (!Files.exists(base)) {
            return new ArtifactTreeNode(normalizedRelative(base), true, 0);
        }
        return walk(base, depth, limit <= 0 ? 200 : limit, new int[] {0});
    }

    private ArtifactTreeNode walk(Path path, int depth, int limit, int[] count) throws IOException {
        boolean dir = Files.isDirectory(path);
        long size = dir ? 0 : Files.size(path);
        ArtifactTreeNode node = new ArtifactTreeNode(normalizedRelative(path), dir, size, new ArrayList<>());
        if (!dir || depth <= 0 || count[0] >= limit) {
            return node;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            List<Path> entries = new ArrayList<>();
            for (Path p : stream) {
                entries.add(p);
            }
            entries.sort(Comparator.comparing(p -> p.getFileName().toString()));
            for (Path child : entries) {
                if (count[0] >= limit) {
                    break;
                }
                count[0]++;
                node.getChildren().add(walk(child, depth - 1, limit, count));
            }
        }
        return node;
    }

    private Path resolveSafe(String artifactPath) {
        if (artifactPath == null || artifactPath.isBlank()) {
            throw new IllegalArgumentException("artifactPath is required");
        }
        String normalized = artifactPath.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("../") || normalized.contains("..\\") || normalized.equals("..")) {
            throw new IllegalArgumentException("invalid artifact path");
        }
        Path resolved = root.resolve(normalized).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("invalid artifact path");
        }
        return resolved;
    }

    private String normalizedRelative(Path path) {
        if (path.equals(root)) {
            return "";
        }
        return root.relativize(path).toString().replace('\\', '/');
    }
}
