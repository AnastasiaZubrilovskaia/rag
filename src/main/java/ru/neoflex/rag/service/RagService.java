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
    private final ChatModel chatModel;
    private final RagProperties ragProperties;
    private final WebSearchService webSearchService;

    /**
     * Обычный (непотоковый) режим
     */
    public ChatCompletionResponse chat(ChatCompletionRequest request) {
        String question = questionExtractor.extract(request);
        List<Document> documents = vectorStoreService.search(
                question,
                ragProperties.getSearch().getTopK(),
                ragProperties.getSearch().getSimilarityThreshold()
        );

        log.info("Found {} relevant documents", documents.size());

        List<String> webResults = performWebSearchIfNeeded(question, documents);

        String prompt = promptBuilder.build(question, documents, webResults);

        Prompt promptObject = new Prompt(new UserMessage(prompt));
        ChatResponse response = chatModel.call(promptObject);

        String answer = response.getResult().getOutput().getText();

        return buildResponse(request.getModel(), answer);
    }

    /**
     * Потоковый режим (SSE)
     */
    public Flux<String> chatStream(ChatCompletionRequest request) {
        String question = questionExtractor.extract(request);
        List<Document> documents = vectorStoreService.search(
                question,
                ragProperties.getSearch().getTopK(),
                ragProperties.getSearch().getSimilarityThreshold()
        );

        log.info("Found {} relevant documents for stream", documents.size());

        List<String> webResults = performWebSearchIfNeeded(question, documents);

        String prompt = promptBuilder.build(question, documents, webResults);

        Prompt promptObject = new Prompt(new UserMessage(prompt));

        return chatModel.stream(promptObject)
                .map(chunk -> {
                    String content = chunk.getResult().getOutput().getText();
                    return buildStreamChunk(request.getModel(), content);
                })
                .concatWith(Flux.just(buildStreamDone()));
    }

    /**
     * Выполняет веб-поиск, если:
     * 1. Веб-поиск включен в настройках
     * 2. Найдено меньше minDocuments документов
     */
    private List<String> performWebSearchIfNeeded(String question, List<Document> documents) {
        RagProperties.WebSearch config = ragProperties.getWebSearch();

        if (!config.isEnabled()) {
            log.debug("Web search is disabled");
            return List.of();
        }

        if (documents.size() >= config.getMinDocuments()) {
            log.debug("Found enough documents ({} >= {}), skipping web search",
                    documents.size(), config.getMinDocuments());
            return List.of();
        }

        log.info("Not enough relevant documents ({} < {}), trying web search...",
                documents.size(), config.getMinDocuments());
        return webSearchService.search(question);
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
}