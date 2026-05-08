package com.allanvital.maestrao.model;

import java.util.Objects;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public class DecryptedCredential {

    private final Long id;
    private final String name;
    private final CredentialType type;
    private final String username;
    private final String decryptedSecret;
    private final String description;

    public DecryptedCredential(Credential credential, String decryptedSecret) {
        this.id = credential.getId();
        this.name = credential.getName();
        this.type = credential.getType();
        this.username = credential.getUsername();
        this.decryptedSecret = decryptedSecret;
        this.description = credential.getDescription();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CredentialType getType() {
        return type;
    }

    public String getUsername() {
        return username;
    }

    public String getDecryptedSecret() {
        return decryptedSecret;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        DecryptedCredential that = (DecryptedCredential) o;
        return Objects.equals(id, that.id) && Objects.equals(name, that.name) && type == that.type && Objects.equals(username, that.username) && Objects.equals(decryptedSecret, that.decryptedSecret) && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, type, username, decryptedSecret, description);
    }

}
