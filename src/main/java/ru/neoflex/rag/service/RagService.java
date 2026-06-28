package ru.neoflex.rag.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import ru.neoflex.rag.model.request.ChatCompletionRequest;
import ru.neoflex.rag.model.response.chat.ChatCompletionResponse;
import ru.neoflex.rag.model.response.chat.ChatResponseMessage;
import ru.neoflex.rag.model.response.chat.Choice;
import ru.neoflex.rag.model.response.chat.Usage;
import ru.neoflex.rag.properties.RagProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RagService {
    private final QuestionExtractor questionExtractor;
    private final VectorStoreService vectorStoreService;
    private final PromptBuilder promptBuilder;
    private final ChatModel chatModel;
    private final RagProperties ragProperties;

    public ChatCompletionResponse chat(ChatCompletionRequest request) {
        String question = questionExtractor.extract(request);
        List<Document> documents = vectorStoreService.search(
                question,
                ragProperties.getSearch().getTopK(),
                ragProperties.getSearch().getSimilarityThreshold()
        );
        System.out.println("SEARCH RESULT SIZE = " + documents.size());
        String prompt = promptBuilder.build(question, documents);

        String answer = chatModel.call(
                new UserMessage(prompt)
        );

        return ChatCompletionResponse.builder()
                .id("chatcmpl-" + UUID.randomUUID())
                .object("chat.completion")
                .created(Instant.now().getEpochSecond())
                .model(request.getModel())
                .choices(List.of(
                        Choice.builder()
                                .index(0)
                                .message(
                                        ChatResponseMessage.builder()
                                                .role("assistant")
                                                .content(answer)
                                                .build()
                                )
                                .finishReason("stop")
                                .build()
                ))
                .usage(
                        Usage.builder()
                                .promptTokens(0)
                                .completionTokens(0)
                                .totalTokens(0)
                                .build()
                )
                .build();
    }

}