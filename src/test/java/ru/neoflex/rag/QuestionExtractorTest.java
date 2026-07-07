package ru.neoflex.rag;

import org.junit.jupiter.api.Test;
import ru.neoflex.rag.model.request.ChatCompletionRequest;
import ru.neoflex.rag.model.request.ChatMessage;
import ru.neoflex.rag.service.QuestionExtractor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuestionExtractorTest {

    private final QuestionExtractor extractor = new QuestionExtractor();

    @Test
    void shouldExtractLastUserMessage() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(List.of(
                createMessage("system", "You are a helper"),
                createMessage("user", "First question"),
                createMessage("assistant", "First answer"),
                createMessage("user", "Second question")
        ));

        String question = extractor.extractLastQuestion(request);

        assertThat(question).isEqualTo("Second question");
    }

    @Test
    void shouldThrowExceptionWhenNoUserMessage() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(List.of(
                createMessage("system", "You are a helper"),
                createMessage("assistant", "Some answer")
        ));

        assertThrows(IllegalArgumentException.class, () -> extractor.extractLastQuestion(request));
    }

    @Test
    void shouldThrowExceptionWhenMessagesEmpty() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(List.of());

        assertThrows(IllegalArgumentException.class, () -> extractor.extractLastQuestion(request));
    }

    @Test
    void shouldBuildHistory() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(List.of(
                createMessage("system", "You are a helper"),
                createMessage("user", "First question"),
                createMessage("assistant", "First answer"),
                createMessage("user", "Second question"),
                createMessage("assistant", "Second answer"),
                createMessage("user", "Third question")
        ));

        String history = extractor.buildHistory(request);

        assertThat(history).contains("Пользователь: First question");
        assertThat(history).contains("Ассистент: First answer");
        assertThat(history).contains("Пользователь: Second question");
        assertThat(history).contains("Ассистент: Second answer");
        assertThat(history).contains("Пользователь: Third question");
        assertThat(history).doesNotContain("You are a helper");
    }

    @Test
    void shouldBuildHistoryWithMaxTurns() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        List<ChatMessage> messages = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            messages.add(createMessage("user", "Question " + i));
            messages.add(createMessage("assistant", "Answer " + i));
        }
        request.setMessages(messages);

        String history = extractor.buildHistory(request);

        int userCount = 0;
        int assistantCount = 0;
        String[] lines = history.split("\n");
        for (String line : lines) {
            if (line.startsWith("Пользователь:")) userCount++;
            if (line.startsWith("Ассистент:")) assistantCount++;
        }
        assertThat(userCount).isLessThanOrEqualTo(10);
        assertThat(assistantCount).isLessThanOrEqualTo(10);
    }

    private ChatMessage createMessage(String role, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setRole(role);
        msg.setContent(content);
        return msg;
    }
}