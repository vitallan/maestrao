package com.allanvital.maestrao.artifactproxy.service;

import com.allanvital.maestrao.artifactproxy.model.ArtifactRemote;

import java.io.IOException;
import java.io.InputStream;

public class RemoteFetchResult implements AutoCloseable {

    private final ArtifactRemote remote;
    private final int statusCode;
    private final Long contentLength;
    private final InputStream body;
    private final String error;
    private final String etag;
    private final String lastModified;

    public RemoteFetchResult(ArtifactRemote remote,
                             int statusCode,
                             Long contentLength,
                             InputStream body,
                             String error,
                             String etag,
                             String lastModified) {
        this.remote = remote;
        this.statusCode = statusCode;
        this.contentLength = contentLength;
        this.body = body;
        this.error = error;
        this.etag = etag;
        this.lastModified = lastModified;
    }

    public ArtifactRemote remote() {
        return remote;
    }

    public int statusCode() {
        return statusCode;
    }

    public Long contentLength() {
        return contentLength;
    }

    public InputStream body() {
        return body;
    }

    public String error() {
        return error;
    }

    public String etag() {
        return etag;
    }

    public String lastModified() {
        return lastModified;
    }

    public boolean ok() {
        return statusCode == 200 && body != null;
    }

    @Override
    public void close() {
        if (body != null) {
            try {
                body.close();
            } catch (IOException ignored) {
            }
        }
    }
}
