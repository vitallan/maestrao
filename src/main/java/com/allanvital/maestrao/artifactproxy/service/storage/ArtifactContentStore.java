package com.allanvital.maestrao.artifactproxy.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface ArtifactContentStore {

    Optional<StoredArtifact> read(String artifactPath) throws IOException;

    WriteSession beginWrite(String artifactPath, ArtifactWriteMetadata metadata) throws IOException;

    StreamCopyResult write(WriteSession session, InputStream data) throws IOException;

    void commit(WriteSession session) throws IOException;

    void abort(WriteSession session) throws IOException;

    boolean exists(String artifactPath) throws IOException;

    void delete(String artifactPath) throws IOException;

    ArtifactTreeNode listTree(String prefix, int depth, int limit) throws IOException;
}
