package ru.neoflex.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryClassifier {
    private final ChatModel chatModel;

    private static final String CLASSIFICATION_PROMPT = """
            Определи тип запроса пользователя. Ответь только одним словом:
            CHAT - если это приветствие, прощание, благодарность или светская беседа
            SEARCH - во всех остальных случаях (вопросы, просьбы, запросы информации)
            
            Запрос: %s
            Ответ:
            """;

    public boolean isChat(String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }

        try {
            String prompt = String.format(CLASSIFICATION_PROMPT, query);
            Prompt promptObject = new Prompt(new UserMessage(prompt));
            String response = chatModel.call(promptObject)
                    .getResult()
                    .getOutput()
                    .getText()
                    .trim()
                    .toUpperCase();

            boolean isChat = response.contains("CHAT");
            log.info("Query '{}' classified as: {}", query, isChat ? "CHAT" : "SEARCH");
            return isChat;

        } catch (Exception e) {
            log.warn("Classification failed for '{}', defaulting to SEARCH", query);
            return false;
        }
    }
}