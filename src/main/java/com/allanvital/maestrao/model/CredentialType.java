package com.allanvital.maestrao.model;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public enum CredentialType {

    PASSWORD("Password"),
    SECRET_KEY("Secret key");

    private final String label;

    CredentialType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

}
