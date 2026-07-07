package ru.neoflex.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import ru.neoflex.rag.service.PromptBuilder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {
    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void shouldBuildPromptWithContext() {
        String question = "What is Java?";
        String history = null;
        List<Document> docs = List.of(
                new Document("Java is a programming language.", Map.of("fileName", "java.txt"))
        );
        List<String> webResults = List.of();

        String prompt = promptBuilder.build(question, history, docs, webResults);

        assertThat(prompt).contains("Java is a programming language.");
        assertThat(prompt).contains("Источник: java.txt");
        assertThat(prompt).contains("=== ВОПРОС ===");
    }

    @Test
    void shouldBuildPromptWithWebResults() {
        String question = "What is AI?";
        String history = null;
        List<Document> docs = List.of();
        List<String> webResults = List.of("AI is artificial intelligence.");

        String prompt = promptBuilder.build(question, history, docs, webResults);

        assertThat(prompt).contains("Результаты веб-поиска");
        assertThat(prompt).contains("AI is artificial intelligence.");
        assertThat(prompt).contains("=== ВОПРОС ===");
    }

    @Test
    void shouldBuildPromptWithEmptyContext() {
        String question = "What is Spring?";
        String history = null;
        List<Document> docs = List.of();
        List<String> webResults = List.of();

        String prompt = promptBuilder.build(question, history, docs, webResults);

        assertThat(prompt).contains("(Нет контекста)");
        assertThat(prompt).contains("=== ВОПРОС ===");
    }

    @Test
    void shouldBuildPromptWithHistory() {
        String question = "А про вторую главу?";
        String history = "Пользователь: Расскажи про первую главу\nАссистент: В первой главе описывается...\n";
        List<Document> docs = List.of();
        List<String> webResults = List.of();

        String prompt = promptBuilder.build(question, history, docs, webResults);

        assertThat(prompt).contains("=== ИСТОРИЯ ДИАЛОГА ===");
        assertThat(prompt).contains("Пользователь: Расскажи про первую главу");
        assertThat(prompt).contains("Ассистент: В первой главе описывается...");
        assertThat(prompt).contains("=== ВОПРОС ===");
        assertThat(prompt).contains("А про вторую главу?");
    }
}
