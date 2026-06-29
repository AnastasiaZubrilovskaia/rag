package ru.neoflex.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    @Bean(name = "documentExecutor")
    public ExecutorService documentExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}