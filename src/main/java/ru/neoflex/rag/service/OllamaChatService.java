package ru.neoflex.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import ru.neoflex.rag.exception.QuotaExceededException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaChatService {
    private final ChatModel chatModel;


    public ChatResponse call(Prompt prompt) {
        try {
            return chatModel.call(prompt);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    public Flux<ChatResponse> stream(Prompt prompt) {
        return chatModel.stream(prompt)
                .onErrorResume(e -> {
                    Throwable handled = handleException(e);
                    return Flux.error(handled);
                });
    }

    private RuntimeException handleException(Throwable e) {
        String message = e.getMessage();
        if (message == null) {
            return new RuntimeException("Unknown Ollama error", e);
        }

        if (isQuotaError(message)) {
            log.error("Ollama quota exceeded: {}", message);
            Long retryAfter = extractRetryAfter(message);

            return new QuotaExceededException(
                    "Лимит запросов к Ollama превышен. Повторите попытку через " +
                            (retryAfter != null ? retryAfter + " секунд" : "некоторое время") + ".",
                    retryAfter
            );
        }
        return new RuntimeException("Ollama API error: " + message, e);
    }

    private boolean isQuotaError(String message) {
        String lower = message.toLowerCase();
        return lower.contains("429") ||
                lower.contains("too many requests") ||
                lower.contains("quota exceeded") ||
                lower.contains("rate limit");
    }

    private Long extractRetryAfter(String message) {
        try {
            Pattern pattern = Pattern.compile("retry after (\\d+) seconds?", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }
        } catch (Exception e) {
            log.warn("Failed to extract retry-after from: {}", message);
        }
        return 300L;
    }
}