package com.allanvital.maestrao.service.log;

import com.allanvital.maestrao.model.LogType;

/**
 * Collector implementation per {@link LogType}.
 *
 * @author Allan Vital (https://allanvital.com)
 */
public interface LogCollector {

    LogType type();
}
