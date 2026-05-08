package com.allanvital.maestrao.repository;

import com.allanvital.maestrao.model.Credential;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public interface CredentialRepository extends JpaRepository<Credential, Long> {
}
