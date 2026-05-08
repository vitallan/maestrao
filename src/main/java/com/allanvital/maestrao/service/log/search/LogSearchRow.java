package com.allanvital.maestrao.service.log.search;

import java.time.Instant;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public record LogSearchRow(Long logLineId,
                           String logName,
                           String hostName,
                           String hostIp,
                           Instant ingestedAt,
                           String line) {
}
