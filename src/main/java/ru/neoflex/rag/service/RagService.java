package ru.neoflex.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import ru.neoflex.rag.exception.QuotaExceededException;
import ru.neoflex.rag.model.entity.AnswerStyle;
import ru.neoflex.rag.model.request.ChatCompletionRequest;
import ru.neoflex.rag.model.response.DebugResponse;
import ru.neoflex.rag.model.response.SourceInfo;
import ru.neoflex.rag.model.response.TimingInfo;
import ru.neoflex.rag.model.response.chat.ChatCompletionResponse;
import ru.neoflex.rag.model.response.chat.ChatResponseMessage;
import ru.neoflex.rag.model.response.chat.Choice;
import ru.neoflex.rag.model.response.chat.Usage;
import ru.neoflex.rag.properties.RagProperties;
import ru.neoflex.rag.util.TechnicalTokenExtractor;
import ru.neoflex.rag.util.TimingTracker;

import java.time.Instant;
import java.util.ArrayList;
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
    private final FilterService filterService;
    private final TechnicalTokenExtractor tokenExtractor;

    private static final String REQUEST_ID_KEY = "request_id";

    /**
     * Обычный (непотоковый) режим
     */
    public ChatCompletionResponse chat(ChatCompletionRequest request) {
        String requestId = getRequestId();
        TimingTracker timings = new TimingTracker();

        RagContext context = buildContext(request, timings);

        if (shouldSkipGeneration(context)) {
            log.info("[{}] No context found, skipping LLM generation", requestId);
            return buildResponse(
                    request.getModel(),
                    "В предоставленном контексте нет информации для ответа на ваш вопрос.",
                    requestId,
                    context.getSources(),
                    timings,
                    "no_context"
            );
        }

        timings.startPrompt();
        String prompt = promptBuilder.build(
                context.getQuestion(),
                context.getHistory(),
                context.getDocuments(),
                context.getWebResults(),
                context.getStyle()
        );
        timings.endPrompt();

        timings.startGeneration();
        try {
            ChatResponse response = ollamaChatService.call(new Prompt(new UserMessage(prompt)));
            timings.endGeneration();

            log.info("[{}] Generation completed, answer length: {}", requestId, response.getResult().getOutput().getText().length());

            return buildResponse(
                    request.getModel(),
                    response.getResult().getOutput().getText(),
                    requestId,
                    context.getSources(),
                    timings,
                    "stop"
            );
        } catch (QuotaExceededException e) {
            timings.endGeneration();
            log.error("[{}] Quota exceeded", requestId, e);
            return buildResponse(
                    request.getModel(),
                    formatErrorMessage(e),
                    requestId,
                    context.getSources(),
                    timings,
                    "error"
            );
        }
    }

    /**
     * Потоковый режим (SSE)
     */
    public Flux<String> chatStream(ChatCompletionRequest request) {
        String requestId = getRequestId();
        final String finalRequestId = requestId;
        TimingTracker timings = new TimingTracker();

        RagContext context = buildContext(request, timings);

        if (shouldSkipGeneration(context)) {
            log.info("[{}] No context found (stream), skipping LLM generation", finalRequestId);
            String chunk = buildStreamChunkWithDetails(
                    request.getModel(),
                    "В предоставленном контексте нет информации для ответа на ваш вопрос.",
                    finalRequestId,
                    context.getSources(),
                    timings,
                    "no_context"
            );
            return Flux.just(chunk, buildStreamDone());
        }

        timings.startPrompt();
        String prompt = promptBuilder.build(
                context.getQuestion(),
                context.getHistory(),
                context.getDocuments(),
                context.getWebResults(),
                context.getStyle()
        );
        timings.endPrompt();

        timings.startGeneration();

        return ollamaChatService.stream(new Prompt(new UserMessage(prompt)))
                .map(chunk -> buildStreamChunk(request.getModel(), chunk.getResult().getOutput().getText()))
                .concatWith(Flux.just(buildStreamDone()))
                .onErrorResume(QuotaExceededException.class, e -> {
                    log.error("[{}] Quota exceeded during streaming", finalRequestId, e);
                    timings.endGeneration();
                    return Flux.just(
                            buildStreamErrorChunk(request.getModel(), formatErrorMessage(e)),
                            buildStreamDone()
                    );
                });
    }

    private String getRequestId() {
        String id = MDC.get(REQUEST_ID_KEY);
        return id != null ? id : "req-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private RagContext buildContext(ChatCompletionRequest request, TimingTracker timings) {
        timings.startRetrieval();

        String question = questionExtractor.extractLastQuestion(request);
        String history = questionExtractor.buildHistory(request);
        AnswerStyle style = AnswerStyle.fromModel(request.getModel());

        List<Document> documents = new ArrayList<>();
        List<String> webResults = new ArrayList<>();
        List<SourceInfo> sources = new ArrayList<>();

        if (!queryClassifier.isChat(question)) {
            documents = vectorStoreService.search(
                    question,
                    ragProperties.getSearch().getTopK(),
                    ragProperties.getSearch().getSimilarityThreshold()
            );

            documents = filterService.applyExactTermGuard(question, documents);
            sources.addAll(buildSources(documents));

            FilterService.FilterResult filterResult = filterService.applyFilter(documents);
            if (!filterResult.isAllowed()) {
                log.info("[{}] Filter blocked generation: {}", getRequestId(), filterResult.getReason());
                documents = List.of();
            }

            if (!documents.isEmpty() && ragProperties.getWebSearch().isEnabled() && shouldUseWebSearch(documents)) {
                webResults = webSearchService.search(question);
            }
        } else {
            log.info("Chat query detected, skipping RAG search: {}", question);
        }

        timings.endRetrieval();

        return RagContext.builder()
                .question(question)
                .history(history)
                .style(style)
                .documents(documents)
                .webResults(webResults)
                .sources(sources)
                .build();
    }

    /**
     * Проверяет, нужно ли использовать веб-поиск
     * если релевантность документов ниже порога — используем веб-поиск
     */
    private boolean shouldUseWebSearch(List<Document> documents) {
        if (!ragProperties.getWebSearch().isEnabled()) {
            return false;
        }

        if (documents.isEmpty()) {
            log.info("No documents found, using web search");
            return true;
        }

        double threshold = ragProperties.getSearch().getSimilarityThreshold();

        boolean hasRelevantDocument = documents.stream()
                .anyMatch(doc -> {
                    Double score = (Double) doc.getMetadata().getOrDefault("score", 0.0);
                    return score >= threshold;
                });

        if (!hasRelevantDocument) {
            log.info("No documents with sufficient relevance (>= {}), using web search", threshold);
            return true;
        }

        log.info("Found relevant documents (score >= {}), skipping web search", threshold);
        return false;
    }

    private List<SourceInfo> buildSources(List<Document> documents) {
        List<SourceInfo> sources = new ArrayList<>();
        for (Document doc : documents) {
            Object positionValue = doc.getMetadata().getOrDefault("position", 0);
            int position = positionValue instanceof Number number ? number.intValue() : 0;

            Object scoreValue = doc.getMetadata().getOrDefault("score", 0.0);
            double score = scoreValue instanceof Number number ? number.doubleValue() : 0.0;

            sources.add(SourceInfo.builder()
                    .documentId((String) doc.getMetadata().getOrDefault("documentId", "unknown"))
                    .fileName((String) doc.getMetadata().getOrDefault("fileName", "unknown"))
                    .position(position)
                    .score(score)
                    .text(doc.getText())
                    .build());
        }
        return sources;
    }

    private boolean shouldSkipGeneration(RagContext context) {
        return context.getDocuments().isEmpty()
                && context.getWebResults().isEmpty()
                && !queryClassifier.isChat(context.getQuestion());
    }

    private String formatErrorMessage(QuotaExceededException e) {
        String msg = "Ошибка: " + e.getMessage();
        if (e.getRetryAfterSeconds() != null) {
            msg += " Повторите через " + e.getRetryAfterSeconds() + " секунд.";
        }
        return msg;
    }

    /**
     * Строит ответ с деталями (request_id, sources, timings)
     */
    private ChatCompletionResponse buildResponse(
            String model,
            String answer,
            String requestId,
            List<SourceInfo> sources,
            TimingTracker timings,
            String finishReason) {

        return ChatCompletionResponse.builder()
                .id("chatcmpl-" + UUID.randomUUID().toString().substring(0, 8))
                .object("chat.completion")
                .created(Instant.now().getEpochSecond())
                .model(model)
                .requestId(requestId)
                .sources(sources.isEmpty() ? null : sources)
                .timings(TimingInfo.builder()
                        .retrievalMs(timings.getRetrievalMs())
                        .promptMs(timings.getPromptMs())
                        .generationMs(timings.getGenerationMs())
                        .totalMs(timings.getTotalMs())
                        .build())
                .choices(List.of(
                        Choice.builder()
                                .index(0)
                                .message(
                                        ChatResponseMessage.builder()
                                                .role("assistant")
                                                .content(answer)
                                                .build()
                                )
                                .finishReason(finishReason)
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
     * Строит SSE чанк с деталями (для no_context)
     */
    private String buildStreamChunkWithDetails(
            String model,
            String content,
            String requestId,
            List<SourceInfo> sources,
            TimingTracker timings,
            String finishReason) {

        return "data: " + """
        {
            "id": "chatcmpl-%s",
            "object": "chat.completion.chunk",
            "created": %d,
            "model": "%s",
            "request_id": "%s",
            "sources": %s,
            "timings": {
                "retrieval_ms": %d,
                "prompt_ms": %d,
                "generation_ms": %d,
                "total_ms": %d
            },
            "choices": [
                {
                    "index": 0,
                    "delta": {
                        "role": "assistant",
                        "content": "%s"
                    },
                    "finish_reason": "%s"
                }
            ]
        }
        """.formatted(
                UUID.randomUUID().toString().substring(0, 8),
                Instant.now().getEpochSecond(),
                model,
                requestId,
                sourcesToJson(sources),
                timings.getRetrievalMs(),
                timings.getPromptMs(),
                timings.getGenerationMs(),
                timings.getTotalMs(),
                escapeJson(content),
                finishReason
        ).replace("\n", " ") + "\n\n";
    }

    /**
     * Строит обычный SSE чанк
     */
    private String buildStreamChunk(String model, String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
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

    /**
     * Строит SSE чанк с ошибкой
     */
    private String buildStreamErrorChunk(String model, String errorMessage) {
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

    /**
     * Завершение SSE стрима
     */
    private String buildStreamDone() {
        return "data: [DONE]\n\n";
    }

    /**
     * Сериализует список источников в JSON
     */
    private String sourcesToJson(List<SourceInfo> sources) {
        if (sources == null || sources.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < sources.size(); i++) {
            SourceInfo s = sources.get(i);
            sb.append("{")
                    .append("\"documentId\":\"").append(escapeJson(s.getDocumentId())).append("\",")
                    .append("\"fileName\":\"").append(escapeJson(s.getFileName())).append("\",")
                    .append("\"position\":").append(s.getPosition()).append(",")
                    .append("\"score\":").append(s.getScore()).append(",")
                    .append("\"text\":\"").append(escapeJson(s.getText())).append("\"")
                    .append("}");
            if (i < sources.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Экранирует специальные символы для JSON
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Диагностический метод для отладки RAG-пайплайна
     */
    public DebugResponse debug(ChatCompletionRequest request) {
        String requestId = getRequestId();
        TimingTracker timings = new TimingTracker();

        String question = questionExtractor.extractLastQuestion(request);

        timings.startRetrieval();
        List<Document> documents = vectorStoreService.search(
                question,
                ragProperties.getSearch().getTopK(),
                ragProperties.getSearch().getSimilarityThreshold()
        );
        timings.endRetrieval();

        List<SourceInfo> sources = buildSources(documents);

        List<String> tokens = new ArrayList<>();
        if (ragProperties.getFilter() != null && ragProperties.getFilter().isExactTermGuardEnabled()) {
            tokens = tokenExtractor.extractTokens(question);
        }

        List<Document> filteredDocs = filterService.applyExactTermGuard(question, documents);
        FilterService.FilterResult filterResult = filterService.applyFilter(filteredDocs);

        return DebugResponse.builder()
                .requestId(requestId)
                .question(question)
                .sources(sources)
                .timings(TimingInfo.builder()
                        .retrievalMs(timings.getRetrievalMs())
                        .promptMs(0L)
                        .generationMs(0L)
                        .totalMs(timings.getTotalMs())
                        .build())
                .exactTermMatch(!filteredDocs.isEmpty())
                .filterLevel(filterResult.getLevel())
                .generationAllowed(filterResult.isAllowed())
                .message(filterResult.getReason())
                .extractedTokens(tokens)
                .build();
    }


    @lombok.Builder
    @lombok.Getter
    private static class RagContext {
        private final String question;
        private final String history;
        private final AnswerStyle style;
        private final List<Document> documents;
        private final List<String> webResults;
        private final List<SourceInfo> sources;
    }
}