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

        String question = extractor.extract(request);

        assertThat(question).isEqualTo("Second question");
    }

    @Test
    void shouldThrowExceptionWhenNoUserMessage() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(List.of(
                createMessage("system", "You are a helper"),
                createMessage("assistant", "Some answer")
        ));

        assertThrows(IllegalArgumentException.class, () -> extractor.extract(request));
    }

    @Test
    void shouldThrowExceptionWhenMessagesEmpty() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(List.of());

        assertThrows(IllegalArgumentException.class, () -> extractor.extract(request));
    }

    private ChatMessage createMessage(String role, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setRole(role);
        msg.setContent(content);
        return msg;
    }
}