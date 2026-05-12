package com.allanvital.maestrao.service.hosthealth;

import java.time.Duration;
import java.time.Instant;

public enum HostHealthWindow {
    M15("Last 15m", Duration.ofMinutes(15)),
    H1("Last 1h", Duration.ofHours(1)),
    H6("Last 6h", Duration.ofHours(6)),
    H24("Last 24h", Duration.ofHours(24)),
    D3("Last 3d", Duration.ofDays(3)),
    D7("Last 7d", Duration.ofDays(7));

    private final String label;
    private final Duration duration;

    HostHealthWindow(String label, Duration duration) {
        this.label = label;
        this.duration = duration;
    }

    public String label() {
        return label;
    }

    public Instant since() {
        return Instant.now().minus(duration);
    }
}
