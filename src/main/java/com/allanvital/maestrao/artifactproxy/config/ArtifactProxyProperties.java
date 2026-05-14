package com.allanvital.maestrao.artifactproxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "maestrao.artifact-proxy")
public class ArtifactProxyProperties {

    private boolean enabled = false;
    private String cacheRoot = "./data/artifact-cache";
    private int maxInflightFetches = 3;
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 10000;
    private int negativeCacheTtlSeconds = 120;
    private Cleanup cleanup = new Cleanup();
    private Metadata metadata = new Metadata();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCacheRoot() {
        return cacheRoot;
    }

    public void setCacheRoot(String cacheRoot) {
        this.cacheRoot = cacheRoot;
    }

    public int getMaxInflightFetches() {
        return maxInflightFetches;
    }

    public void setMaxInflightFetches(int maxInflightFetches) {
        this.maxInflightFetches = maxInflightFetches;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getNegativeCacheTtlSeconds() {
        return negativeCacheTtlSeconds;
    }

    public void setNegativeCacheTtlSeconds(int negativeCacheTtlSeconds) {
        this.negativeCacheTtlSeconds = negativeCacheTtlSeconds;
    }

    public Cleanup getCleanup() {
        return cleanup;
    }

    public void setCleanup(Cleanup cleanup) {
        this.cleanup = cleanup;
    }

    public static class Cleanup {
        private boolean enabled = true;
        private int maxSizeGb = 50;
        private int maxIdleDays = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxSizeGb() {
            return maxSizeGb;
        }

        public void setMaxSizeGb(int maxSizeGb) {
            this.maxSizeGb = maxSizeGb;
        }

        public int getMaxIdleDays() {
            return maxIdleDays;
        }

        public void setMaxIdleDays(int maxIdleDays) {
            this.maxIdleDays = maxIdleDays;
        }
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public static class Metadata {
        private String updatePolicy = "interval:5";
        private boolean conditionalRevalidate = true;
        private boolean serveStaleOnError = true;
        private int maxStaleMinutes = 1440;

        public String getUpdatePolicy() {
            return updatePolicy;
        }

        public void setUpdatePolicy(String updatePolicy) {
            this.updatePolicy = updatePolicy;
        }

        public boolean isConditionalRevalidate() {
            return conditionalRevalidate;
        }

        public void setConditionalRevalidate(boolean conditionalRevalidate) {
            this.conditionalRevalidate = conditionalRevalidate;
        }

        public boolean isServeStaleOnError() {
            return serveStaleOnError;
        }

        public void setServeStaleOnError(boolean serveStaleOnError) {
            this.serveStaleOnError = serveStaleOnError;
        }

        public int getMaxStaleMinutes() {
            return maxStaleMinutes;
        }

        public void setMaxStaleMinutes(int maxStaleMinutes) {
            this.maxStaleMinutes = maxStaleMinutes;
        }
    }
}
