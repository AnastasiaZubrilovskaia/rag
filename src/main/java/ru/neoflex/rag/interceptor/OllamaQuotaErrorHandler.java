package ru.neoflex.rag.interceptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import ru.neoflex.rag.exception.QuotaExceededException;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Slf4j
public class OllamaQuotaErrorHandler implements ResponseErrorHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        return response.getStatusCode().isError();
    }

    @Override
    public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
        HttpStatus statusCode = (HttpStatus) response.getStatusCode();
        String responseBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);

        log.error("Ollama API error: url={}, method={}, status={}, body={}",
                url, method, statusCode, responseBody);

        if (statusCode == HttpStatus.TOO_MANY_REQUESTS) {
            Long retryAfterSeconds = parseRetryAfter(response, responseBody);
            throw new QuotaExceededException(
                    "Лимит запросов к Ollama превышен. Повторите попытку через " +
                            (retryAfterSeconds != null ? retryAfterSeconds + " секунд" : "некоторое время") + ".",
                    retryAfterSeconds
            );
        }

        throw new RuntimeException(
                "Ошибка при вызове Ollama API: " + statusCode + " - " + responseBody
        );
    }



    private Long parseRetryAfter(ClientHttpResponse response, String responseBody) {
        try {
            String retryAfterHeader = response.getHeaders().getFirst("Retry-After");
            if (retryAfterHeader != null) {
                try {
                    return Long.parseLong(retryAfterHeader);
                } catch (NumberFormatException e) {

                }
            }

            JsonNode root = objectMapper.readTree(responseBody);

            if (root.has("retry_after")) {
                return root.get("retry_after").asLong();
            }

            if (root.has("retry-after")) {
                return root.get("retry-after").asLong();
            }
            if (root.has("error")) {
                String error = root.get("error").asText();
                java.util.regex.Pattern pattern =
                        java.util.regex.Pattern.compile("retry after (\\d+) seconds");
                java.util.regex.Matcher matcher = pattern.matcher(error);
                if (matcher.find()) {
                    return Long.parseLong(matcher.group(1));
                }
            }

        } catch (Exception e) {
            log.warn("Failed to parse retry-after: {}", e.getMessage());
        }
        return 300L;
    }
}