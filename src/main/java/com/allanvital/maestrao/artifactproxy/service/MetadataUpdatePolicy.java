package com.allanvital.maestrao.artifactproxy.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;

public record MetadataUpdatePolicy(Kind kind, int intervalMinutes) {
    public enum Kind {
        ALWAYS,
        DAILY,
        INTERVAL,
        NEVER
    }

    public static MetadataUpdatePolicy parse(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "always" -> new MetadataUpdatePolicy(Kind.ALWAYS, 0);
            case "daily" -> new MetadataUpdatePolicy(Kind.DAILY, 0);
            case "never" -> new MetadataUpdatePolicy(Kind.NEVER, 0);
            default -> {
                if (!value.startsWith("interval:")) {
                    throw new IllegalArgumentException("invalid metadata update policy: " + raw);
                }
                int minutes = Integer.parseInt(value.substring("interval:".length()));
                if (minutes <= 0) {
                    throw new IllegalArgumentException("interval minutes must be > 0");
                }
                yield new MetadataUpdatePolicy(Kind.INTERVAL, minutes);
            }
        };
    }

    public boolean shouldRevalidate(Instant lastValidatedAt, Instant now) {
        if (kind == Kind.ALWAYS) {
            return true;
        }
        if (kind == Kind.NEVER) {
            return false;
        }
        if (lastValidatedAt == null) {
            return true;
        }
        if (kind == Kind.DAILY) {
            ZonedDateTime last = ZonedDateTime.ofInstant(lastValidatedAt, ZoneOffset.UTC);
            ZonedDateTime current = ZonedDateTime.ofInstant(now, ZoneOffset.UTC);
            return last.getYear() != current.getYear() || last.getDayOfYear() != current.getDayOfYear();
        }
        return Duration.between(lastValidatedAt, now).toMinutes() >= intervalMinutes;
    }
}
