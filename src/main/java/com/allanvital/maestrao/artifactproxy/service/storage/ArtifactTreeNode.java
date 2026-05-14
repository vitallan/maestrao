package com.allanvital.maestrao.artifactproxy.service.storage;

import java.util.ArrayList;
import java.util.List;

public class ArtifactTreeNode {
    private final String path;
    private final boolean directory;
    private final long sizeBytes;
    private final List<ArtifactTreeNode> children;

    public ArtifactTreeNode(String path, boolean directory, long sizeBytes) {
        this(path, directory, sizeBytes, new ArrayList<>());
    }

    public ArtifactTreeNode(String path, boolean directory, long sizeBytes, List<ArtifactTreeNode> children) {
        this.path = path;
        this.directory = directory;
        this.sizeBytes = sizeBytes;
        this.children = children;
    }

    public String getPath() {
        return path;
    }

    public boolean isDirectory() {
        return directory;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public List<ArtifactTreeNode> getChildren() {
        return children;
    }
}
