package com.allanvital.maestrao.artifactproxy.service.storage;

import java.nio.file.Path;

public record WriteSession(String artifactPath, Path tempFile, Path finalFile) {
}
