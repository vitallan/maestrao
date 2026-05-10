package com.allanvital.maestrao.service.log.search;

import com.allanvital.maestrao.repository.LogLineRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Service
public class LogSearchService {

    private final LogLineRepository logLineRepository;

    public LogSearchService(LogLineRepository logLineRepository) {
        this.logLineRepository = logLineRepository;
    }

    public Page<LogSearchRow> search(String query, Pageable pageable) {
        ParsedQuery parsed = parse(query);
        return logLineRepository.searchAdvanced(parsed.freeText, parsed.kvTerms, parsed.logName, pageable);
    }

    public SearchPage fetchPage(String query, Pageable pageable) {
        ParsedQuery parsed = parse(query);

        int pageSize = pageable.getPageSize();
        List<LogSearchRow> rows = logLineRepository.fetchAdvanced(parsed.freeText, parsed.kvTerms, parsed.logName, pageable, pageSize + 1);
        boolean hasNext = rows.size() > pageSize;
        if (hasNext) {
            rows = rows.subList(0, pageSize);
        }
        return new SearchPage(rows, hasNext);
    }

    public long countTotal(String query) {
        ParsedQuery parsed = parse(query);
        return logLineRepository.countAdvanced(parsed.freeText, parsed.kvTerms, parsed.logName);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ParsedQuery parse(String raw) {
        String normalized = normalizeOptional(raw);
        if (normalized == null) {
            return new ParsedQuery(null, List.of(), null);
        }

        List<Token> tokens = tokenize(normalized);
        List<String> nonKv = new ArrayList<>();
        List<String> kvTerms = new ArrayList<>();
        String logName = null;

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            String token = t.value;
            if (token == null || token.isBlank()) {
                continue;
            }

            if (token.startsWith("log:")) {
                String value = normalizeOptional(token.substring("log:".length()));
                if (value == null) {
                    // Allow whitespace between operator and value: `log: dnsao1` and `log: "My Log"`.
                    if (i + 1 < tokens.size()) {
                        Token next = tokens.get(i + 1);
                        String nextValue = normalizeOptional(next.value);
                        if (nextValue != null) {
                            value = nextValue;
                            i++; // consume next token
                        }
                    }
                }
                if (value == null) {
                    // While typing, the query can temporarily contain `log:` without a value.
                    // Be resilient: treat it as free text until the user provides a value.
                    nonKv.add(token);
                    continue;
                }
                if (logName != null) {
                    // Be forgiving: treat additional log: operators as plain text.
                    nonKv.add(token);
                    continue;
                }
                // Whitespace is required between tokens.
                // If the user wrote: log:"My Log"level=error the tokenizer will produce a single token and
                // mark it as having trailing chars after a quoted segment.
                if (t.hadQuote && t.trailingAfterQuote) {
                    // While typing, this can also happen transiently. Treat as plain text.
                    nonKv.add(token);
                    continue;
                }
                logName = value;
                continue;
            }

            int eq = token.indexOf('=');
            if (eq > 0 && eq < token.length() - 1) {
                String key = token.substring(0, eq).trim();
                String value = token.substring(eq + 1).trim();
                if (!key.isBlank() && !value.isBlank()) {
                    kvTerms.add(key + "=" + value);
                    continue;
                }
            }
            nonKv.add(token);
        }

        String freeText = nonKv.isEmpty() ? null : String.join(" ", nonKv);
        freeText = normalizeOptional(freeText);

        return new ParsedQuery(freeText, kvTerms, logName);
    }

    private List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        char quote = 0;
        boolean escaping = false;

        boolean hadQuote = false;
        boolean justClosedQuote = false;
        boolean trailingAfterQuote = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (quote == 0) {
                if (Character.isWhitespace(c)) {
                    if (!current.isEmpty()) {
                        tokens.add(new Token(current.toString(), hadQuote, trailingAfterQuote));
                        current.setLength(0);
                        hadQuote = false;
                        justClosedQuote = false;
                        trailingAfterQuote = false;
                    }
                    continue;
                }
                if (c == '\'' || c == '"') {
                    quote = c;
                    hadQuote = true;
                    justClosedQuote = false;
                    continue;
                }
                if (justClosedQuote) {
                    trailingAfterQuote = true;
                    justClosedQuote = false;
                }
                current.append(c);
                continue;
            }

            // inside quotes
            if (escaping) {
                current.append(c);
                escaping = false;
                continue;
            }
            if (c == '\\') {
                escaping = true;
                continue;
            }
            if (c == quote) {
                quote = 0;
                justClosedQuote = true;
                continue;
            }
            current.append(c);
        }

        // Be resilient while typing: treat unterminated quotes as literal content.
        if (escaping) {
            // Treat a trailing backslash as a literal backslash.
            current.append('\\');
        }

        if (!current.isEmpty()) {
            tokens.add(new Token(current.toString(), hadQuote, trailingAfterQuote));
        }
        return tokens;
    }

    private record Token(String value, boolean hadQuote, boolean trailingAfterQuote) {
    }

    private record ParsedQuery(String freeText, List<String> kvTerms, String logName) {
    }

    public record SearchPage(List<LogSearchRow> items, boolean hasNext) {
    }
}
