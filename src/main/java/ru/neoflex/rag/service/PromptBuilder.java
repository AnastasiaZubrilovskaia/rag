package ru.neoflex.rag.service;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class PromptBuilder {

    public String build(String question, List<Document> contextDocuments, List<String> webResults) {

        String context = contextDocuments.stream()
                .map(document -> {

                    String source = document.getMetadata()
                            .getOrDefault("fileName", "unknown")
                            .toString();

                    return document.getText()
                            + "\nИсточник: "
                            + source;

                })
                .collect(Collectors.joining("\n\n---\n\n"));

        if (webResults != null && !webResults.isEmpty()) {
            context += "\n\n---\n\nРезультаты веб-поиска:\n\n" +
                    webResults.stream()
                            .collect(Collectors.joining("\n\n"));
        }

        if (context.isEmpty()) {
            context = "(Нет контекста)";
        }

        return """
                Ты — помощник, отвечающий только на основе предоставленного контекста.

                Правила (НАРУШЕНИЕ ЗАПРЕЩЕНО):
                
                - Ответь на вопрос, используя ТОЛЬКО информацию из контекста.
                - Скопируй соответствующие фрагменты из контекста дословно.
                - Не придумывай факты. Если ответа нет в контексте — честно скажи об этом.
                - Если контекст пустой — напиши: "В предоставленном контексте нет информации по вашему вопросу."
                - Если использованы веб-результаты, укажи это в ответе.
                - По возможности указывай источник.

                Контекст:

                %s

                Вопрос:

                %s
                """.formatted(context, question);
    }

}