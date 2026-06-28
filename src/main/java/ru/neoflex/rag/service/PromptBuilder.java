package ru.neoflex.rag.service;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class PromptBuilder {

    public String build(String question,
                        List<Document> contextDocuments) {

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

        return """
                Ты — помощник, отвечающий только на основе предоставленного контекста.

                Правила:
                
                - Отвечай только по контексту.
                - Не придумывай факты.
                - Если ответа нет в контексте — честно скажи об этом.
                - По возможности указывай источник.

                Контекст:

                %s

                Вопрос:

                %s
                """.formatted(context, question);
    }

}