package com.allanvital.maestrao.repository;

import com.allanvital.maestrao.service.log.search.LogSearchRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public class LogLineRepositoryImpl implements LogLineRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<LogSearchRow> searchAdvanced(String freeText, List<String> kvTerms, String logName, Pageable pageable) {
        // Kept for compatibility with existing callers; delegates to interface default.
        return LogLineRepositoryCustom.super.searchAdvanced(freeText, kvTerms, logName, pageable);
    }

    @Override
    public List<LogSearchRow> fetchAdvanced(String freeText, List<String> kvTerms, String logName, Instant since, Pageable pageable, int limit) {
        if (kvTerms == null) {
            kvTerms = List.of();
        }

        QueryParts parts = buildSearchQueryParts(freeText, kvTerms, logName, since);
        TypedQuery<LogSearchRow> query = entityManager.createQuery(parts.jpql, LogSearchRow.class);
        for (Param p : parts.params) {
            query.setParameter(p.name, p.value);
        }

        int pageNumber = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        query.setFirstResult(Math.max(0, pageNumber) * pageSize);
        query.setMaxResults(Math.max(0, limit));
        return query.getResultList();
    }

    @Override
    public long countAdvanced(String freeText, List<String> kvTerms, String logName, Instant since) {
        if (kvTerms == null) {
            kvTerms = List.of();
        }

        StringBuilder jpql = new StringBuilder();
        jpql.append("select count(l) from LogLine l join l.logSource s where 1=1 ");

        List<Param> params = new ArrayList<>();

        if (since != null) {
            jpql.append("and l.ingestedAt >= :since ");
            params.add(new Param("since", since));
        }

        if (freeText != null && !freeText.isBlank()) {
            jpql.append("and lower(cast(l.line as string)) like :freeText ");
            params.add(new Param("freeText", like(freeText)));
        }

        if (logName != null && !logName.isBlank()) {
            jpql.append("and lower(s.name) = :logName ");
            params.add(new Param("logName", logName.toLowerCase()));
        }

        for (int i = 0; i < kvTerms.size(); i++) {
            String term = kvTerms.get(i);
            if (term == null || term.isBlank()) {
                continue;
            }
            String name = "kv" + i;
            jpql.append("and lower(cast(l.line as string)) like :").append(name).append(" ");
            params.add(new Param(name, like(term)));
        }

        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);
        for (Param p : params) {
            query.setParameter(p.name, p.value);
        }
        Long result = query.getSingleResult();
        return result == null ? 0 : result;
    }

    @Override
    public List<LogSearchRow> fetchFullText(String booleanQuery, String logName, Instant since, Pageable pageable, int limit) {
        if (booleanQuery == null || booleanQuery.isBlank()) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder();
        sql.append("select l.id, s.name as log_name, h.name as host_name, h.ip as host_ip, l.ingested_at, l.line ");
        sql.append("from log_lines l ");
        sql.append("join log_sources s on s.id = l.log_source_id ");
        sql.append("join hosts h on h.id = s.host_id ");
        sql.append("where match(l.line) against (? in boolean mode) ");

        List<Object> params = new ArrayList<>();
        params.add(booleanQuery);

        if (logName != null && !logName.isBlank()) {
            sql.append("and lower(s.name) = ? ");
            params.add(logName.toLowerCase());
        }

        if (since != null) {
            sql.append("and l.ingested_at >= ? ");
            params.add(since);
        }

        sql.append("order by l.ingested_at desc ");
        sql.append("limit ? offset ?");

        int pageNumber = Math.max(0, pageable.getPageNumber());
        int pageSize = pageable.getPageSize();
        int offset = pageNumber * pageSize;

        params.add(Math.max(0, limit));
        params.add(offset);

        Query q = entityManager.createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            q.setParameter(i + 1, params.get(i));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        List<LogSearchRow> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(new LogSearchRow(
                    ((Number) r[0]).longValue(),
                    (String) r[1],
                    (String) r[2],
                    (String) r[3],
                    ((java.sql.Timestamp) r[4]).toInstant(),
                    (String) r[5]
            ));
        }
        return out;
    }

    @Override
    public long countFullText(String booleanQuery, String logName, Instant since) {
        if (booleanQuery == null || booleanQuery.isBlank()) {
            return 0;
        }

        StringBuilder sql = new StringBuilder();
        sql.append("select count(*) ");
        sql.append("from log_lines l ");
        sql.append("join log_sources s on s.id = l.log_source_id ");
        sql.append("where match(l.line) against (? in boolean mode) ");

        List<Object> params = new ArrayList<>();
        params.add(booleanQuery);

        if (logName != null && !logName.isBlank()) {
            sql.append("and lower(s.name) = ? ");
            params.add(logName.toLowerCase());
        }

        if (since != null) {
            sql.append("and l.ingested_at >= ? ");
            params.add(since);
        }

        Query q = entityManager.createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            q.setParameter(i + 1, params.get(i));
        }
        Object res = q.getSingleResult();
        return res == null ? 0 : ((Number) res).longValue();
    }

    private String like(String value) {
        return "%" + value.toLowerCase() + "%";
    }

    private QueryParts buildSearchQueryParts(String freeText, List<String> kvTerms, String logName, Instant since) {
        StringBuilder jpql = new StringBuilder();
        jpql.append("select new com.allanvital.maestrao.service.log.search.LogSearchRow(");
        jpql.append("l.id, s.name, h.name, h.ip, l.ingestedAt, l.line");
        jpql.append(") ");
        jpql.append("from LogLine l ");
        jpql.append("join l.logSource s ");
        jpql.append("join s.host h ");
        jpql.append("where 1=1 ");

        List<Param> params = new ArrayList<>();

        if (since != null) {
            jpql.append("and l.ingestedAt >= :since ");
            params.add(new Param("since", since));
        }

        if (freeText != null && !freeText.isBlank()) {
            jpql.append("and lower(cast(l.line as string)) like :freeText ");
            params.add(new Param("freeText", like(freeText)));
        }

        if (logName != null && !logName.isBlank()) {
            jpql.append("and lower(s.name) = :logName ");
            params.add(new Param("logName", logName.toLowerCase()));
        }

        for (int i = 0; i < kvTerms.size(); i++) {
            String term = kvTerms.get(i);
            if (term == null || term.isBlank()) {
                continue;
            }
            String name = "kv" + i;
            jpql.append("and lower(cast(l.line as string)) like :").append(name).append(" ");
            params.add(new Param(name, like(term)));
        }

        jpql.append("order by l.ingestedAt desc");
        return new QueryParts(jpql.toString(), params);
    }

    private record QueryParts(String jpql, List<Param> params) {
    }

    private record Param(String name, Object value) {
    }
}
