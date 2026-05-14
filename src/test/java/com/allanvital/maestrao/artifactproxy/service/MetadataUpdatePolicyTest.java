package com.allanvital.maestrao.artifactproxy.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MetadataUpdatePolicyTest {

    @Test
    void parsesAndAppliesPolicies() {
        Instant now = Instant.parse("2026-05-13T12:00:00Z");
        assertTrue(MetadataUpdatePolicy.parse("always").shouldRevalidate(now.minusSeconds(10), now));
        assertFalse(MetadataUpdatePolicy.parse("never").shouldRevalidate(now.minusSeconds(86400), now));
        assertTrue(MetadataUpdatePolicy.parse("interval:5").shouldRevalidate(now.minusSeconds(301), now));
        assertFalse(MetadataUpdatePolicy.parse("interval:5").shouldRevalidate(now.minusSeconds(120), now));
        assertTrue(MetadataUpdatePolicy.parse("daily").shouldRevalidate(now.minusSeconds(86400), now));
    }

    @Test
    void rejectsInvalidPolicy() {
        assertThrows(IllegalArgumentException.class, () -> MetadataUpdatePolicy.parse("interval:0"));
        assertThrows(IllegalArgumentException.class, () -> MetadataUpdatePolicy.parse("sometimes"));
    }
}
