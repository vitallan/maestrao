package com.allanvital.maestrao.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class SearchAsyncConfig {

    @Bean
    public Executor searchCountExecutor() {
        return Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setName("maestrao-search-" + t.threadId());
            t.setDaemon(true);
            return t;
        });
    }
}
