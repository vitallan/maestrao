package com.allanvital.maestrao.repository;

import com.allanvital.maestrao.service.log.search.LogSearchRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public interface LogLineRepositoryCustom {

    Page<LogSearchRow> searchAdvanced(String freeText, List<String> kvTerms, String logName, Pageable pageable);

    /**
     * Fetch-only variant used to avoid blocking on COUNT(*) for large datasets.
     * The caller can request (pageSize + 1) rows to determine whether a next page exists.
     */
    List<LogSearchRow> fetchAdvanced(String freeText, List<String> kvTerms, String logName, Pageable pageable, int limit);

    long countAdvanced(String freeText, List<String> kvTerms, String logName);
}
