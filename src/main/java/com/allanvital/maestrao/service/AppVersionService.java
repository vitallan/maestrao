package com.allanvital.maestrao.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class AppVersionService {

    private final Environment environment;
    private final ObjectProvider<BuildProperties> buildPropertiesProvider;

    public AppVersionService(Environment environment, ObjectProvider<BuildProperties> buildPropertiesProvider) {
        this.environment = environment;
        this.buildPropertiesProvider = buildPropertiesProvider;
    }

    public String getDisplayVersion() {
        String injected = environment.getProperty("maestrao.app.version");
        if (injected != null && !injected.isBlank()) {
            return injected.trim();
        }

        BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
        if (buildProperties != null) {
            String v = buildProperties.getVersion();
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }

        return "unknown";
    }

}
