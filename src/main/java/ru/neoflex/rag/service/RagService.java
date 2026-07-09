package ru.neoflex.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import ru.neoflex.rag.exception.QuotaExceededException;
import ru.neoflex.rag.model.entity.AnswerStyle;
import ru.neoflex.rag.model.request.ChatCompletionRequest;
import ru.neoflex.rag.model.response.chat.ChatCompletionResponse;
import ru.neoflex.rag.model.response.chat.ChatResponseMessage;
import ru.neoflex.rag.model.response.chat.Choice;
import ru.neoflex.rag.model.response.chat.Usage;
import ru.neoflex.rag.properties.RagProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {
    private final QuestionExtractor questionExtractor;
    private final VectorStoreService vectorStoreService;
    private final PromptBuilder promptBuilder;
    private final OllamaChatService ollamaChatService;
    private final RagProperties ragProperties;
    private final WebSearchService webSearchService;
    private final QueryClassifier queryClassifier;

    /**
     * Обычный (непотоковый) режим
     */
    public ChatCompletionResponse chat(ChatCompletionRequest request) {
        String lastQuestion = questionExtractor.extractLastQuestion(request);
        String history = questionExtractor.buildHistory(request);

        AnswerStyle style = AnswerStyle.fromModel(request.getModel());
        log.info("Answer style: {} for model: {}", style, request.getModel());

        boolean isChat = queryClassifier.isChat(lastQuestion);
        List<Document> documents = List.of();
        List<String> webResults = List.of();

        if (!isChat) {
            documents = vectorStoreService.search(
                    lastQuestion,
                    ragProperties.getSearch().getTopK(),
                    ragProperties.getSearch().getSimilarityThreshold()
            );

            if (documents.size() < ragProperties.getWebSearch().getMinDocuments()
                    && ragProperties.getWebSearch().isEnabled()) {
                log.info("Not enough documents ({}), trying web search", documents.size());
                webResults = webSearchService.search(lastQuestion);
            }
        } else {
            log.info("Chat query detected, skipping RAG search: {}", lastQuestion);
        }

        String prompt = promptBuilder.build(lastQuestion, history, documents, webResults, style);

        Prompt promptObject = new Prompt(new UserMessage(prompt));
        try {
            ChatResponse response = ollamaChatService.call(promptObject);
            String answer = response.getResult().getOutput().getText();
            return buildResponse(request.getModel(), answer);
        } catch (QuotaExceededException e) {
            log.error("Quota exceeded: {}", e.getMessage());
            return buildQuotaErrorResponse(request.getModel(), e);
        }
    }

    /**
     * Потоковый режим (SSE)
     */
    public Flux<String> chatStream(ChatCompletionRequest request) {
        String lastQuestion = questionExtractor.extractLastQuestion(request);
        String history = questionExtractor.buildHistory(request);

        AnswerStyle style = AnswerStyle.fromModel(request.getModel());
        log.info("Answer style (stream): {} for model: {}", style, request.getModel());
        boolean isChat = queryClassifier.isChat(lastQuestion);

        List<Document> documents = List.of();
        List<String> webResults = List.of();

        if (!isChat) {
            documents = vectorStoreService.search(
                    lastQuestion,
                    ragProperties.getSearch().getTopK(),
                    ragProperties.getSearch().getSimilarityThreshold()
            );

            if (documents.size() < ragProperties.getWebSearch().getMinDocuments()
                    && ragProperties.getWebSearch().isEnabled()) {
                log.info("Not enough documents ({}), trying web search", documents.size());
                webResults = webSearchService.search(lastQuestion);
            }
        } else {
            log.info("Chat query detected (stream), skipping RAG search: {}", lastQuestion);
        }

        String prompt = promptBuilder.build(lastQuestion, history, documents, webResults, style);

        Prompt promptObject = new Prompt(new UserMessage(prompt));

        return ollamaChatService.stream(promptObject)
                .map(chunk -> {
                    String content = chunk.getResult().getOutput().getText();
                    return buildStreamChunk(request.getModel(), content);
                })
                .concatWith(Flux.just(buildStreamDone()))
                .onErrorResume(QuotaExceededException.class, e -> {
                    log.error("Quota exceeded during streaming: {}", e.getMessage());
                    return Flux.just(
                            buildStreamErrorChunk(request.getModel(), e),
                            buildStreamDone()
                    );
                });
    }



    /**
     * Строит SSE чанк для потоковой передачи
     */
    private String buildStreamChunk(String model, String content) {
        return "data: " + """
        {
            "id": "chatcmpl-%s",
            "object": "chat.completion.chunk",
            "created": %d,
            "model": "%s",
            "choices": [
                {
                    "index": 0,
                    "delta": {
                        "role": "assistant",
                        "content": "%s"
                    },
                    "finish_reason": null
                }
            ]
        }
        """.formatted(
                UUID.randomUUID(),
                Instant.now().getEpochSecond(),
                model,
                escapeJson(content)
        ).replace("\n", " ") + "\n\n";
    }


    private String buildStreamDone() {
        return "data: [DONE]\n\n";
    }


    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Строит обычный (непотоковый) ответ
     */
    private ChatCompletionResponse buildResponse(String model, String answer) {
        return ChatCompletionResponse.builder()
                .id("chatcmpl-" + UUID.randomUUID())
                .object("chat.completion")
                .created(Instant.now().getEpochSecond())
                .model(model)
                .choices(List.of(
                        Choice.builder()
                                .index(0)
                                .message(
                                        ChatResponseMessage.builder()
                                                .role("assistant")
                                                .content(answer)
                                                .build()
                                )
                                .finishReason("stop")
                                .build()
                ))
                .usage(
                        Usage.builder()
                                .promptTokens(0)
                                .completionTokens(0)
                                .totalTokens(0)
                                .build()
                )
                .build();
    }

    /**
     * Строит ответ с ошибкой квоты для обычного режима
     */
    private ChatCompletionResponse buildQuotaErrorResponse(String model, QuotaExceededException e) {
        String errorMessage = "Ошибка: " + e.getMessage();
        if (e.getRetryAfterSeconds() != null) {
            errorMessage += " Повторите через " + e.getRetryAfterSeconds() + " секунд.";
        }

        return ChatCompletionResponse.builder()
                .id("chatcmpl-" + UUID.randomUUID())
                .object("chat.completion")
                .created(Instant.now().getEpochSecond())
                .model(model)
                .choices(List.of(
                        Choice.builder()
                                .index(0)
                                .message(
                                        ChatResponseMessage.builder()
                                                .role("assistant")
                                                .content(errorMessage)
                                                .build()
                                )
                                .finishReason("error")
                                .build()
                ))
                .usage(
                        Usage.builder()
                                .promptTokens(0)
                                .completionTokens(0)
                                .totalTokens(0)
                                .build()
                )
                .build();
    }

    /**
     * Строит SSE чанк с ошибкой квоты
     */
    private String buildStreamErrorChunk(String model, QuotaExceededException e) {
        String errorMessage = "Ошибка: " + e.getMessage();
        if (e.getRetryAfterSeconds() != null) {
            errorMessage += " Повторите через " + e.getRetryAfterSeconds() + " секунд.";
        }

        return "data: " + """
        {
            "id": "chatcmpl-%s",
            "object": "chat.completion.chunk",
            "created": %d,
            "model": "%s",
            "choices": [
                {
                    "index": 0,
                    "delta": {
                        "role": "assistant",
                        "content": "%s"
                    },
                    "finish_reason": "error"
                }
            ]
        }
        """.formatted(
                UUID.randomUUID(),
                Instant.now().getEpochSecond(),
                model,
                escapeJson(errorMessage)
        ).replace("\n", " ") + "\n\n";
    }
}