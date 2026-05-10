package com.allanvital.maestrao.service.log.search;

import java.time.Duration;
import java.time.Instant;

public enum SearchWindow {
    H1("Last 1h", Duration.ofHours(1)),
    H3("Last 3h", Duration.ofHours(3)),
    H6("Last 6h", Duration.ofHours(6)),
    H24("Last 24h", Duration.ofHours(24)),
    D3("Last 3d", Duration.ofDays(3)),
    D7("Last 7d", Duration.ofDays(7)),
    ALL("All", null);

    private final String label;
    private final Duration duration;

    SearchWindow(String label, Duration duration) {
        this.label = label;
        this.duration = duration;
    }

    public String label() {
        return label;
    }

    public Instant since() {
        if (duration == null) {
            return null;
        }
        return Instant.now().minus(duration);
    }
}
