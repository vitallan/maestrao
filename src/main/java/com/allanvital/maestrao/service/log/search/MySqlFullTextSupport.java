package com.allanvital.maestrao.service.log.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

@Service
public class MySqlFullTextSupport {

    private static final Logger log = LoggerFactory.getLogger(MySqlFullTextSupport.class);

    private static final String INDEX_NAME = "ft_log_lines_line";

    private final DataSource dataSource;
    private volatile Boolean fullTextAvailable;

    public MySqlFullTextSupport(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean isFullTextAvailable() {
        Boolean cached = fullTextAvailable;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            cached = fullTextAvailable;
            if (cached != null) {
                return cached;
            }

            boolean available = computeAndEnsure();
            fullTextAvailable = available;
            return available;
        }
    }

    private boolean computeAndEnsure() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String product = meta.getDatabaseProductName();
            String productLower = product == null ? "" : product.toLowerCase();
            boolean mysqlFamily = productLower.contains("mysql") || productLower.contains("mariadb");
            if (!mysqlFamily) {
                return false;
            }

            if (hasIndex(conn)) {
                return true;
            }

            log.info("search.fulltext creating index {} on log_lines(line)", INDEX_NAME);
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE log_lines ADD FULLTEXT INDEX " + INDEX_NAME + " (line)");
            }

            if (hasIndex(conn)) {
                log.info("search.fulltext index {} created", INDEX_NAME);
                return true;
            }

            log.warn("search.fulltext index {} was not found after creation attempt", INDEX_NAME);
            return false;
        } catch (Exception e) {
            log.warn("search.fulltext unavailable: {}", e.getMessage());
            return false;
        }
    }

    private boolean hasIndex(Connection conn) {
        String sql = """
                select count(*)
                from information_schema.statistics
                where table_schema = database()
                  and table_name = 'log_lines'
                  and index_name = ?
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, INDEX_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                return rs.getLong(1) > 0;
            }
        } catch (Exception e) {
            log.warn("search.fulltext index check failed: {}", e.getMessage());
            return false;
        }
    }
}
