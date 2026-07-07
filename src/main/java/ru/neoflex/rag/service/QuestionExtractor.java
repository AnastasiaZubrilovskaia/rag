package ru.neoflex.rag.service;

import org.springframework.stereotype.Component;
import ru.neoflex.rag.model.request.ChatCompletionRequest;
import ru.neoflex.rag.model.request.ChatMessage;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class QuestionExtractor {
    private static final int MAX_HISTORY_TURNS = 10;

    public String extractLastQuestion(ChatCompletionRequest request) {

        return request.getMessages()
                .stream()
                .filter(message -> "user".equals(message.getRole()))
                .reduce((first, second) -> second)
                .map(ChatMessage::getContent)
                .orElseThrow(() ->
                        new IllegalArgumentException("User message not found"));

    }

    public String buildHistory(ChatCompletionRequest request) {
        List<ChatMessage> messages = request.getMessages();

        if (messages == null || messages.isEmpty()) {
            return "";
        }

        List<ChatMessage> recentMessages = messages.stream()
                .skip(Math.max(0, messages.size() - MAX_HISTORY_TURNS * 2))
                .collect(Collectors.toList());

        StringBuilder history = new StringBuilder();
        for (ChatMessage msg : recentMessages) {
            String role = msg.getRole();
            String content = msg.getContent();

            if ("user".equals(role)) {
                history.append("Пользователь: ").append(content).append("\n");
            } else if ("assistant".equals(role)) {
                history.append("Ассистент: ").append(content).append("\n");
            }
        }

        return history.toString();
    }

    //Проверяет, является ли вопрос уточняющим (содержит ссылку на предыдущий контекст)
    public boolean isFollowUpQuestion(String question, String history) {
        if (history == null || history.isEmpty()) {
            return false;
        }

        String[] followUpIndicators = {"а про", "а что насчет", "а если", "а как", "а где", "а когда"};
        String lowerQuestion = question.toLowerCase();

        for (String indicator : followUpIndicators) {
            if (lowerQuestion.contains(indicator)) {
                return true;
            }
        }

        return false;
    }

}
