package ru.neoflex.rag.service;

import org.springframework.stereotype.Component;
import ru.neoflex.rag.model.request.ChatCompletionRequest;
import ru.neoflex.rag.model.request.ChatMessage;

@Component
public class QuestionExtractor {

    public String extract(ChatCompletionRequest request) {

        return request.getMessages()
                .stream()
                .filter(message -> "user".equals(message.getRole()))
                .reduce((first, second) -> second)
                .map(ChatMessage::getContent)
                .orElseThrow(() ->
                        new IllegalArgumentException("User message not found"));

    }

}
