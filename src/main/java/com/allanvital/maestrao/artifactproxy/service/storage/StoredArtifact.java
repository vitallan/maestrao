package com.allanvital.maestrao.artifactproxy.service.storage;

import java.nio.file.Path;

public record StoredArtifact(Path path, long sizeBytes) {
}
