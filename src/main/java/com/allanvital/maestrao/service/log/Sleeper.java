package com.allanvital.maestrao.service.log;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public interface Sleeper {
    void sleep(long millis) throws InterruptedException;
}
