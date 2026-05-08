package com.allanvital.maestrao.repository;

import com.allanvital.maestrao.service.log.search.LogSearchRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public class LogLineRepositoryImpl implements LogLineRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<LogSearchRow> searchAdvanced(String freeText, List<String> kvTerms, String logName, Pageable pageable) {
        if (kvTerms == null) {
            kvTerms = List.of();
        }

        StringBuilder jpql = new StringBuilder();
        jpql.append("select new com.allanvital.maestrao.service.log.search.LogSearchRow(");
        jpql.append("l.id, s.name, h.name, h.ip, l.ingestedAt, l.line");
        jpql.append(") ");
        jpql.append("from LogLine l ");
        jpql.append("join l.logSource s ");
        jpql.append("join s.host h ");
        jpql.append("where 1=1 ");

        List<Param> params = new ArrayList<>();

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

        TypedQuery<LogSearchRow> query = entityManager.createQuery(jpql.toString(), LogSearchRow.class);
        for (Param p : params) {
            query.setParameter(p.name, p.value);
        }

        int pageNumber = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        query.setFirstResult(Math.max(0, pageNumber) * pageSize);
        query.setMaxResults(pageSize);

        List<LogSearchRow> content = query.getResultList();
        long total = countAdvanced(freeText, kvTerms, logName);
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public long countAdvanced(String freeText, List<String> kvTerms, String logName) {
        if (kvTerms == null) {
            kvTerms = List.of();
        }

        StringBuilder jpql = new StringBuilder();
        jpql.append("select count(l) from LogLine l join l.logSource s where 1=1 ");

        List<Param> params = new ArrayList<>();

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

    private String like(String value) {
        return "%" + value.toLowerCase() + "%";
    }

    private record Param(String name, String value) {
    }
}
