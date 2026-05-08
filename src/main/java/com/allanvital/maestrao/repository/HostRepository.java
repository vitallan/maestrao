package com.allanvital.maestrao.repository;
import com.allanvital.maestrao.model.Host;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public interface HostRepository extends JpaRepository<Host, Long> {

    @Override
    @EntityGraph(attributePaths = "credential")
    Page<Host> findAll(Pageable pageable);

}
