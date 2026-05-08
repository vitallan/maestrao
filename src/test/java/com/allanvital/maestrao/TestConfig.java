package com.allanvital.maestrao;

import com.allanvital.maestrao.service.log.Sleeper;
import com.allanvital.maestrao.ssh.FakeSshClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Allan Vital (https://allanvital.com)
 */
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public FakeSshClient fakeSshClient() {
        return new FakeSshClient();
    }

    @Bean
    @Primary
    public CapturingSleeper capturingSleeper() {
        return new CapturingSleeper();
    }

    public static class CapturingSleeper implements Sleeper {
        private final AtomicLong lastSleptMillis = new AtomicLong(-1);
        private final CountDownLatch sleptLatch = new CountDownLatch(1);

        @Override
        public void sleep(long millis) {
            lastSleptMillis.set(millis);
            sleptLatch.countDown();
        }

        public long getLastSleptMillis() {
            return lastSleptMillis.get();
        }

        public boolean awaitSleep(long timeoutMillis) {
            try {
                return sleptLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

}
