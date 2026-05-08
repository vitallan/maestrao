package com.allanvital.maestrao.service.job.schedule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public class Cron5ToQuartzCronConverterTest {

    private final Cron5ToQuartzCronConverter converter = new Cron5ToQuartzCronConverter();

    @Test
    void shouldConvertWildcardDomToQuartzQuestionDow() {
        assertEquals("0 */5 * * * ?", converter.toQuartzCron("*/5 * * * *"));
        assertEquals("0 0 2 * * ?", converter.toQuartzCron("0 2 * * *"));
    }

    @Test
    void shouldConvertRestrictedDowToQuartzQuestionDom() {
        assertEquals("0 0 2 ? * MON", converter.toQuartzCron("0 2 * * MON"));
    }

    @Test
    void shouldNormalizeSunday() {
        assertEquals("0 0 2 ? * SUN", converter.toQuartzCron("0 2 * * 0"));
        assertEquals("0 0 2 ? * SUN", converter.toQuartzCron("0 2 * * 7"));
    }

    @Test
    void shouldRejectDomAndDowBothRestricted() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> converter.toQuartzCron("0 2 1 * MON"));
        assertTrue(ex.getMessage().toLowerCase().contains("both"));
    }
}
