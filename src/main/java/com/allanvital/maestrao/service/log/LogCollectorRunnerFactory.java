package com.allanvital.maestrao.service.log;

import com.allanvital.maestrao.model.LogSource;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public interface LogCollectorRunnerFactory extends LogCollector {

    LogCollectorManager.LogCollectorRunner create(LogSource source, LogCollectorManager.CollectorControl control);

}
