package com.allanvital.maestrao.artifactproxy.repository;

import com.allanvital.maestrao.artifactproxy.model.ArtifactRemote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtifactRemoteRepository extends JpaRepository<ArtifactRemote, Long> {
    List<ArtifactRemote> findByEnabledTrueOrderByIdAsc();
}
