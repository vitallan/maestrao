package com.allanvital.maestrao.service.log;

import org.springframework.stereotype.Component;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@Component
public class DefaultSleeper implements Sleeper {
    @Override
    public void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}
