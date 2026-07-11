package ru.neoflex.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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

    @InjectMocks
    private RagService ragService;

    @Test
    void shouldGenerateResponse() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("qwen2.5:7b");
        request.setMessages(List.of(new ChatMessage()));

        when(questionExtractor.extractLastQuestion(any())).thenReturn("test question");
        when(questionExtractor.buildHistory(any())).thenReturn("");
        when(queryClassifier.isChat(any())).thenReturn(false);

        RagProperties.Search search = new RagProperties.Search();
        search.setTopK(5);
        search.setSimilarityThreshold(0.7);
        when(ragProperties.getSearch()).thenReturn(search);

        RagProperties.WebSearch webSearch = new RagProperties.WebSearch();
        webSearch.setEnabled(false);
        webSearch.setMinDocuments(1);
        when(ragProperties.getWebSearch()).thenReturn(webSearch);

        when(vectorStoreService.search(any(), anyInt(), anyDouble()))
                .thenReturn(List.of(new Document("test content", Map.of("fileName", "test.txt"))));

        when(promptBuilder.build(any(), any(), any(), any(), any(AnswerStyle.class))).thenReturn("prompt");

        AssistantMessage assistantMessage = new AssistantMessage("test answer");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(ollamaChatService.call(any())).thenReturn(chatResponse);

        ChatCompletionResponse response = ragService.chat(request);

        assertThat(response).isNotNull();
        assertThat(response.getChoices()).hasSize(1);
        assertThat(response.getChoices().get(0).getMessage().getContent()).isEqualTo("test answer");
        assertThat(response.getModel()).isEqualTo("qwen2.5:7b");
    }

    @Test
    void shouldSkipSearchForChatQuery() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("qwen2.5:7b");
        request.setMessages(List.of(new ChatMessage()));

        when(questionExtractor.extractLastQuestion(any())).thenReturn("Привет");
        when(questionExtractor.buildHistory(any())).thenReturn("");
        when(queryClassifier.isChat(any())).thenReturn(true);

        when(promptBuilder.build(any(), any(), any(), any(), any(AnswerStyle.class))).thenReturn("prompt for chat");

        AssistantMessage assistantMessage = new AssistantMessage("Привет!");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(ollamaChatService.call(any())).thenReturn(chatResponse);

        ChatCompletionResponse response = ragService.chat(request);

        assertThat(response).isNotNull();
        assertThat(response.getChoices().get(0).getMessage().getContent()).isEqualTo("Привет!");

        verify(vectorStoreService, never()).search(any(), anyInt(), anyDouble());
        verify(webSearchService, never()).search(any());
    }

    @Test
    void shouldUseWebSearchWhenNoDocumentsFound() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("qwen2.5:7b");
        request.setMessages(List.of(new ChatMessage()));

        when(questionExtractor.extractLastQuestion(any())).thenReturn("test question");
        when(questionExtractor.buildHistory(any())).thenReturn("");
        when(queryClassifier.isChat(any())).thenReturn(false);

        RagProperties.Search search = new RagProperties.Search();
        search.setTopK(5);
        search.setSimilarityThreshold(0.7);
        when(ragProperties.getSearch()).thenReturn(search);

        RagProperties.WebSearch webSearch = new RagProperties.WebSearch();
        webSearch.setEnabled(true);
        webSearch.setMinDocuments(1);
        when(ragProperties.getWebSearch()).thenReturn(webSearch);

        when(vectorStoreService.search(any(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        when(webSearchService.search(any())).thenReturn(List.of("web result"));
        when(promptBuilder.build(any(), any(), any(), any(), any(AnswerStyle.class)))
                .thenReturn("prompt with web results");

        AssistantMessage assistantMessage = new AssistantMessage("answer from web");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(ollamaChatService.call(any())).thenReturn(chatResponse);

        ChatCompletionResponse response = ragService.chat(request);

        assertThat(response).isNotNull();
        assertThat(response.getChoices().get(0).getMessage().getContent()).isEqualTo("answer from web");
    }

    @Test
    void shouldGenerateStreamResponse() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("qwen2.5:7b");
        ChatMessage userMessage = new ChatMessage();
        userMessage.setRole("user");
        userMessage.setContent("Stream question");
        request.setMessages(List.of(userMessage));

        when(questionExtractor.extractLastQuestion(any())).thenReturn("test question");
        when(questionExtractor.buildHistory(any())).thenReturn("");
        when(queryClassifier.isChat(any())).thenReturn(false);

        RagProperties.Search search = new RagProperties.Search();
        search.setTopK(5);
        search.setSimilarityThreshold(0.7);
        when(ragProperties.getSearch()).thenReturn(search);

        RagProperties.WebSearch webSearch = new RagProperties.WebSearch();
        webSearch.setEnabled(false);
        webSearch.setMinDocuments(1);
        when(ragProperties.getWebSearch()).thenReturn(webSearch);

        when(vectorStoreService.search(any(), anyInt(), anyDouble()))
                .thenReturn(List.of(new Document("test content", Map.of("fileName", "test.txt"))));

        when(promptBuilder.build(any(), any(), any(), any(), any(AnswerStyle.class))).thenReturn("prompt");

        AssistantMessage assistantMessage = new AssistantMessage("stream answer");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(ollamaChatService.stream(any())).thenReturn(Flux.just(chatResponse));

        List<String> events = ragService.chatStream(request).collectList().block();

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).contains("data:").contains("stream answer").contains("\"model\": \"qwen2.5:7b\"");
        assertThat(events.get(1)).isEqualTo("data: [DONE]\n\n");
    }

    @Test
    void shouldHandleLongMetadataPositionInSources() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("qwen2.5:7b");
        request.setMessages(List.of(new ChatMessage()));

        when(questionExtractor.extractLastQuestion(any())).thenReturn("test question");
        when(questionExtractor.buildHistory(any())).thenReturn("");
        when(queryClassifier.isChat(any())).thenReturn(false);

        RagProperties.Search search = new RagProperties.Search();
        search.setTopK(5);
        search.setSimilarityThreshold(0.7);
        when(ragProperties.getSearch()).thenReturn(search);

        RagProperties.WebSearch webSearch = new RagProperties.WebSearch();
        webSearch.setEnabled(false);
        webSearch.setMinDocuments(1);
        when(ragProperties.getWebSearch()).thenReturn(webSearch);

        when(vectorStoreService.search(any(), anyInt(), anyDouble()))
                .thenReturn(List.of(new Document("test content", Map.of(
                        "documentId", "doc-1",
                        "fileName", "test.txt",
                        "position", 42L,
                        "score", 0.91
                ))));

        when(promptBuilder.build(any(), any(), any(), any(), any(AnswerStyle.class))).thenReturn("prompt");

        AssistantMessage assistantMessage = new AssistantMessage("test answer");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(ollamaChatService.call(any())).thenReturn(chatResponse);

        ChatCompletionResponse response = ragService.chat(request);

        assertThat(response).isNotNull();
        assertThat(response.getSources()).hasSize(1);
        assertThat(response.getSources().get(0).getPosition()).isEqualTo(42);
    }

    @Test
    void shouldUseWebSearchInStreamWhenNoDocumentsFound() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("qwen2.5:7b");
        ChatMessage userMessage = new ChatMessage();
        userMessage.setRole("user");
        userMessage.setContent("Stream question");
        request.setMessages(List.of(userMessage));

        when(questionExtractor.extractLastQuestion(any())).thenReturn("test question");
        when(questionExtractor.buildHistory(any())).thenReturn("");
        when(queryClassifier.isChat(any())).thenReturn(false);

        RagProperties.Search search = new RagProperties.Search();
        search.setTopK(5);
        search.setSimilarityThreshold(0.7);
        when(ragProperties.getSearch()).thenReturn(search);

        RagProperties.WebSearch webSearch = new RagProperties.WebSearch();
        webSearch.setEnabled(true);
        webSearch.setMinDocuments(1);
        when(ragProperties.getWebSearch()).thenReturn(webSearch);

        when(vectorStoreService.search(any(), anyInt(), anyDouble()))
                .thenReturn(List.of());
        when(webSearchService.search(any())).thenReturn(List.of("web result"));
        when(promptBuilder.build(any(), any(), any(), any(), any(AnswerStyle.class)))
                .thenReturn("prompt with web results");

        AssistantMessage assistantMessage = new AssistantMessage("answer from web stream");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(ollamaChatService.stream(any())).thenReturn(Flux.just(chatResponse));

        List<String> events = ragService.chatStream(request).collectList().block();

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).contains("answer from web stream");
    }

    @Test
    void shouldHandleQuotaExceededException() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("qwen2.5:7b");
        request.setMessages(List.of(new ChatMessage()));

        when(questionExtractor.extractLastQuestion(any())).thenReturn("test question");
        when(questionExtractor.buildHistory(any())).thenReturn("");
        when(queryClassifier.isChat(any())).thenReturn(false);

        RagProperties.Search search = new RagProperties.Search();
        search.setTopK(5);
        search.setSimilarityThreshold(0.7);
        when(ragProperties.getSearch()).thenReturn(search);

        RagProperties.WebSearch webSearch = new RagProperties.WebSearch();
        webSearch.setEnabled(false);
        webSearch.setMinDocuments(1);
        when(ragProperties.getWebSearch()).thenReturn(webSearch);

        when(vectorStoreService.search(any(), anyInt(), anyDouble()))
                .thenReturn(List.of(new Document("test content", Map.of("fileName", "test.txt"))));

        when(promptBuilder.build(any(), any(), any(), any(), any(AnswerStyle.class))).thenReturn("prompt");

        QuotaExceededException quotaEx = new QuotaExceededException(
                "Лимит запросов к Ollama превышен. Повторите попытку через 300 секунд.",
                300L
        );
        when(ollamaChatService.call(any())).thenThrow(quotaEx);

        ChatCompletionResponse response = ragService.chat(request);

        assertThat(response).isNotNull();
        assertThat(response.getChoices().get(0).getMessage().getContent())
                .contains("Лимит запросов к Ollama превышен");
        assertThat(response.getChoices().get(0).getFinishReason()).isEqualTo("error");
    }

    @Test
    void shouldHandleQuotaExceededExceptionInStream() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("qwen2.5:7b");
        ChatMessage userMessage = new ChatMessage();
        userMessage.setRole("user");
        userMessage.setContent("Stream question");
        request.setMessages(List.of(userMessage));

        when(questionExtractor.extractLastQuestion(any())).thenReturn("test question");
        when(questionExtractor.buildHistory(any())).thenReturn("");
        when(queryClassifier.isChat(any())).thenReturn(false);

        RagProperties.Search search = new RagProperties.Search();
        search.setTopK(5);
        search.setSimilarityThreshold(0.7);
        when(ragProperties.getSearch()).thenReturn(search);

        RagProperties.WebSearch webSearch = new RagProperties.WebSearch();
        webSearch.setEnabled(false);
        webSearch.setMinDocuments(1);
        when(ragProperties.getWebSearch()).thenReturn(webSearch);

        when(vectorStoreService.search(any(), anyInt(), anyDouble()))
                .thenReturn(List.of(new Document("test content", Map.of("fileName", "test.txt"))));

        when(promptBuilder.build(any(), any(), any(), any(), any(AnswerStyle.class))).thenReturn("prompt");

        QuotaExceededException quotaEx = new QuotaExceededException(
                "Лимит запросов к Ollama превышен. Повторите попытку через 300 секунд.",
                300L
        );
        when(ollamaChatService.stream(any())).thenReturn(Flux.error(quotaEx));

        List<String> events = ragService.chatStream(request).collectList().block();

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).contains("Лимит запросов к Ollama превышен");
        assertThat(events.get(1)).isEqualTo("data: [DONE]\n\n");
    }
}