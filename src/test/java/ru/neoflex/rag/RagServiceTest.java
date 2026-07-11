package ru.neoflex.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;
import ru.neoflex.rag.exception.QuotaExceededException;
import ru.neoflex.rag.model.entity.AnswerStyle;
import ru.neoflex.rag.model.request.ChatCompletionRequest;
import ru.neoflex.rag.model.request.ChatMessage;
import ru.neoflex.rag.model.response.chat.ChatCompletionResponse;
import ru.neoflex.rag.properties.RagProperties;
import ru.neoflex.rag.service.*;
import ru.neoflex.rag.util.TechnicalTokenExtractor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RagServiceTest {

    @Mock
    private QuestionExtractor questionExtractor;

    @Mock
    private VectorStoreService vectorStoreService;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private RagProperties ragProperties;

    @Mock
    private WebSearchService webSearchService;

    @Mock
    private QueryClassifier queryClassifier;

    @Mock
    private OllamaChatService ollamaChatService;

    @Mock
    private FilterService filterService;

    @Mock
    private TechnicalTokenExtractor tokenExtractor;

    @InjectMocks
    private RagService ragService;

    private RagProperties.Search search;
    private RagProperties.WebSearch webSearch;
    private RagProperties.Filter filter;

    @BeforeEach
    void setUp() {
        search = new RagProperties.Search();
        search.setTopK(5);
        search.setSimilarityThreshold(0.7);

        webSearch = new RagProperties.WebSearch();
        webSearch.setEnabled(false);
        webSearch.setMinDocuments(1);

        filter = new RagProperties.Filter();
        filter.setExactTermGuardEnabled(false);
        filter.setStrongThreshold(0.75);
        filter.setBorderlineThreshold(0.6);
        filter.setMinContextSources(2);
    }

    @Test
    void shouldGenerateResponse() {
        ChatCompletionRequest request = createRequest("test question");

        when(questionExtractor.extractLastQuestion(any())).thenReturn("test question");
        when(questionExtractor.buildHistory(any())).thenReturn("");
        when(queryClassifier.isChat(any())).thenReturn(false);
        when(ragProperties.getSearch()).thenReturn(search);
        when(ragProperties.getWebSearch()).thenReturn(webSearch);
        when(ragProperties.getFilter()).thenReturn(filter);

        Document doc = new Document("test content", Map.of("fileName", "test.txt", "score", 0.8));
        when(vectorStoreService.search(any(), anyInt(), anyDouble())).thenReturn(List.of(doc));

        when(filterService.applyExactTermGuard(anyString(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(filterService.applyFilter(anyList()))
                .thenReturn(FilterService.FilterResult.of(true, "STRONG", "OK"));

        when(promptBuilder.build(any(), any(), any(), any(), any(AnswerStyle.class))).thenReturn("prompt");

        ChatResponse chatResponse = createChatResponse("test answer");
        when(ollamaChatService.call(any())).thenReturn(chatResponse);

        ChatCompletionResponse response = ragService.chat(request);

        assertThat(response).isNotNull();
        assertThat(response.getChoices().get(0).getMessage().getContent()).isEqualTo("test answer");
    }

    @Test
    void shouldSkipSearchForChatQuery() {
        ChatCompletionRequest request = createRequest("Привет");

        when(questionExtractor.extractLastQuestion(any())).thenReturn("Привет");
        when(questionExtractor.buildHistory(any())).thenReturn("");
        when(queryClassifier.isChat(any())).thenReturn(true);
        when(ragProperties.getWebSearch()).thenReturn(webSearch);

        when(promptBuilder.build(any(), any(), any(), any(), any(AnswerStyle.class))).thenReturn("prompt for chat");

        ChatResponse chatResponse = createChatResponse("Привет!");
        when(ollamaChatService.call(any())).thenReturn(chatResponse);

        ChatCompletionResponse response = ragService.chat(request);

        assertThat(response).isNotNull();
        assertThat(response.getChoices().get(0).getMessage().getContent()).isEqualTo("Привет!");
        verify(vectorStoreService, never()).search(any(), anyInt(), anyDouble());
    }

    @Test
    void shouldGenerateStreamResponse() {
        ChatCompletionRequest request = createStreamRequest("Stream question");

        when(questionExtractor.extractLastQuestion(any())).thenReturn("test question");
        when(questionExtractor.buildHistory(any())).thenReturn("");
        when(queryClassifier.isChat(any())).thenReturn(false);
        when(ragProperties.getSearch()).thenReturn(search);
        when(ragProperties.getWebSearch()).thenReturn(webSearch);
        when(ragProperties.getFilter()).thenReturn(filter);

        Document doc = new Document("test content", Map.of("fileName", "test.txt", "score", 0.8));
        when(vectorStoreService.search(any(), anyInt(), anyDouble())).thenReturn(List.of(doc));

        when(filterService.applyExactTermGuard(anyString(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(filterService.applyFilter(anyList()))
                .thenReturn(FilterService.FilterResult.of(true, "STRONG", "OK"));

        when(promptBuilder.build(any(), any(), any(), any(), any(AnswerStyle.class))).thenReturn("prompt");

        ChatResponse chatResponse = createChatResponse("stream answer");
        when(ollamaChatService.stream(any())).thenReturn(Flux.just(chatResponse));

        List<String> events = ragService.chatStream(request).collectList().block();

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).contains("stream answer");
        assertThat(events.get(1)).isEqualTo("data: [DONE]\n\n");
    }

    @Test
    void shouldHandleQuotaExceededException() {
        ChatCompletionRequest request = createRequest("test question");

        when(questionExtractor.extractLastQuestion(any())).thenReturn("test question");
        when(questionExtractor.buildHistory(any())).thenReturn("");
        when(queryClassifier.isChat(any())).thenReturn(false);
        when(ragProperties.getSearch()).thenReturn(search);
        when(ragProperties.getWebSearch()).thenReturn(webSearch);
        when(ragProperties.getFilter()).thenReturn(filter);

        Document doc = new Document("test content", Map.of("fileName", "test.txt", "score", 0.8));
        when(vectorStoreService.search(any(), anyInt(), anyDouble())).thenReturn(List.of(doc));

        when(filterService.applyExactTermGuard(anyString(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(filterService.applyFilter(anyList()))
                .thenReturn(FilterService.FilterResult.of(true, "STRONG", "OK"));

        when(promptBuilder.build(any(), any(), any(), any(), any(AnswerStyle.class))).thenReturn("prompt");

        QuotaExceededException quotaEx = new QuotaExceededException(
                "Лимит запросов к Ollama превышен. Повторите попытку через 300 секунд.", 300L);
        when(ollamaChatService.call(any())).thenThrow(quotaEx);

        ChatCompletionResponse response = ragService.chat(request);

        assertThat(response).isNotNull();
        assertThat(response.getChoices().get(0).getMessage().getContent())
                .contains("Лимит запросов к Ollama превышен");
        assertThat(response.getChoices().get(0).getFinishReason()).isEqualTo("error");
    }

    @Test
    void shouldHandleQuotaExceededExceptionInStream() {
        ChatCompletionRequest request = createStreamRequest("Stream question");

        when(questionExtractor.extractLastQuestion(any())).thenReturn("test question");
        when(questionExtractor.buildHistory(any())).thenReturn("");
        when(queryClassifier.isChat(any())).thenReturn(false);
        when(ragProperties.getSearch()).thenReturn(search);
        when(ragProperties.getWebSearch()).thenReturn(webSearch);
        when(ragProperties.getFilter()).thenReturn(filter);

        Document doc = new Document("test content", Map.of("fileName", "test.txt", "score", 0.8));
        when(vectorStoreService.search(any(), anyInt(), anyDouble())).thenReturn(List.of(doc));

        when(filterService.applyExactTermGuard(anyString(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(filterService.applyFilter(anyList()))
                .thenReturn(FilterService.FilterResult.of(true, "STRONG", "OK"));

        when(promptBuilder.build(any(), any(), any(), any(), any(AnswerStyle.class))).thenReturn("prompt");

        QuotaExceededException quotaEx = new QuotaExceededException(
                "Лимит запросов к Ollama превышен. Повторите попытку через 300 секунд.", 300L);
        when(ollamaChatService.stream(any())).thenReturn(Flux.error(quotaEx));

        List<String> events = ragService.chatStream(request).collectList().block();

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).contains("Лимит запросов к Ollama превышен");
        assertThat(events.get(1)).isEqualTo("data: [DONE]\n\n");
    }

    @Test
    void shouldHandleLongMetadataPositionInSources() {
        ChatCompletionRequest request = createRequest("test question");

        when(questionExtractor.extractLastQuestion(any())).thenReturn("test question");
        when(questionExtractor.buildHistory(any())).thenReturn("");
        when(queryClassifier.isChat(any())).thenReturn(false);
        when(ragProperties.getSearch()).thenReturn(search);
        when(ragProperties.getWebSearch()).thenReturn(webSearch);
        when(ragProperties.getFilter()).thenReturn(filter);

        Document doc = new Document("test content", Map.of("fileName", "test.txt", "position", 5L, "score", 0.8));
        when(vectorStoreService.search(any(), anyInt(), anyDouble())).thenReturn(List.of(doc));

        when(filterService.applyExactTermGuard(anyString(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(filterService.applyFilter(anyList()))
                .thenReturn(FilterService.FilterResult.of(true, "STRONG", "OK"));

        when(promptBuilder.build(any(), any(), any(), any(), any(AnswerStyle.class))).thenReturn("prompt");

        ChatResponse chatResponse = createChatResponse("test answer");
        when(ollamaChatService.call(any())).thenReturn(chatResponse);

        ChatCompletionResponse response = ragService.chat(request);

        assertThat(response).isNotNull();
        assertThat(response.getChoices().get(0).getMessage().getContent()).isEqualTo("test answer");
    }

    // ==================== Helper Methods ====================

    private ChatCompletionRequest createRequest(String content) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("qwen2.5:7b");
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent(content);
        request.setMessages(List.of(message));
        return request;
    }

    private ChatCompletionRequest createStreamRequest(String content) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("qwen2.5:7b");
        request.setStream(true);
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent(content);
        request.setMessages(List.of(message));
        return request;
    }

    private ChatResponse createChatResponse(String content) {
        AssistantMessage assistantMessage = new AssistantMessage(content);
        Generation generation = new Generation(assistantMessage);
        return new ChatResponse(List.of(generation));
    }
}