package ru.neoflex.rag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import ru.neoflex.rag.interceptor.OllamaQuotaErrorHandler;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Value("${client.searxng.timeout:10s}")
    private String searxngTimeout;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        Duration timeout = parseTimeout(searxngTimeout);
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());

        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setErrorHandler(new OllamaQuotaErrorHandler());

        return restTemplate;
    }

    private Duration parseTimeout(String timeoutStr) {
        if (timeoutStr.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(timeoutStr.replace("s", "")));
        } else if (timeoutStr.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(timeoutStr.replace("ms", "")));
        }
        return Duration.ofSeconds(10);
    }
}