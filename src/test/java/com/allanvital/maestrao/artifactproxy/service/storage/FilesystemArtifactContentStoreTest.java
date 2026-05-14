package com.allanvital.maestrao.artifactproxy.service.storage;

import com.allanvital.maestrao.artifactproxy.config.ArtifactProxyProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FilesystemArtifactContentStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void writesAndReadsAndListsTree() throws Exception {
        ArtifactProxyProperties props = new ArtifactProxyProperties();
        props.setCacheRoot(tempDir.toString());
        FilesystemArtifactContentStore store = new FilesystemArtifactContentStore(props);

        WriteSession session = store.beginWrite("a/b/c/test.jar", new ArtifactWriteMetadata(4L));
        store.write(session, new ByteArrayInputStream(new byte[] {1, 2, 3, 4}));
        store.commit(session);

        StoredArtifact artifact = store.read("a/b/c/test.jar").orElseThrow();
        assertEquals(4L, artifact.sizeBytes());

        ArtifactTreeNode tree = store.listTree("", 6, 100);
        assertTrue(tree.isDirectory());
        assertFalse(tree.getChildren().isEmpty());
    }
}
