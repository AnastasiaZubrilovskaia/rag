package ru.neoflex.rag.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.neoflex.rag.model.request.ChatCompletionRequest;
import ru.neoflex.rag.model.response.chat.ChatCompletionResponse;
import ru.neoflex.rag.service.RagService;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1")
public class ChatController {
    private final RagService ragService;

    /**
     * Обрабатывает запросы к /v1/chat/completions.
     * Автоматически выбирает режим ответа на основе поля stream в запросе.
     */
    @PostMapping(value = "/chat/completions",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public ResponseEntity<?> chat(@Valid @RequestBody ChatCompletionRequest request) {
        log.info("Chat request: model={}, messages={}, stream={}",
                request.getModel(),
                request.getMessages().size(),
                request.getStream());

        if (Boolean.TRUE.equals(request.getStream())) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(ragService.chatStream(request));
        } else {
            ChatCompletionResponse response = ragService.chat(request);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        }
    }
}