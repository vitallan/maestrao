package com.allanvital.maestrao.repository;

import com.allanvital.maestrao.service.log.search.LogSearchRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;

import java.util.List;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public interface LogLineRepositoryCustom {

    default Page<LogSearchRow> searchAdvanced(String freeText, List<String> kvTerms, String logName, Pageable pageable) {
        int pageSize = pageable.getPageSize();
        List<LogSearchRow> content = fetchAdvanced(freeText, kvTerms, logName, null, pageable, pageSize);
        long total = countAdvanced(freeText, kvTerms, logName, null);
        return new PageImpl<>(content, pageable, total);
    }

    default List<LogSearchRow> fetchAdvanced(String freeText, List<String> kvTerms, String logName, Pageable pageable, int limit) {
        return fetchAdvanced(freeText, kvTerms, logName, null, pageable, limit);
    }

    default long countAdvanced(String freeText, List<String> kvTerms, String logName) {
        return countAdvanced(freeText, kvTerms, logName, null);
    }

    /**
     * Fetch-only variant used to avoid blocking on COUNT(*) for large datasets.
     * The caller can request (pageSize + 1) rows to determine whether a next page exists.
     */
    List<LogSearchRow> fetchAdvanced(String freeText, List<String> kvTerms, String logName, Instant since, Pageable pageable, int limit);

    long countAdvanced(String freeText, List<String> kvTerms, String logName, Instant since);

    List<LogSearchRow> fetchFullText(String booleanQuery, String logName, Instant since, Pageable pageable, int limit);

    long countFullText(String booleanQuery, String logName, Instant since);
}
