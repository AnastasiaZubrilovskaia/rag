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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import ru.neoflex.rag.model.request.ChatCompletionRequest;
import ru.neoflex.rag.model.response.chat.ChatCompletionResponse;
import ru.neoflex.rag.service.RagService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1")
public class ChatController {
    private final RagService ragService;

    @PostMapping(value = "/chat/completions",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatCompletionResponse> chat(@Valid @RequestBody ChatCompletionRequest request) {
        log.info("Chat request: model={}, messages={}",
                request.getModel(),
                request.getMessages().size());

        return ResponseEntity.ok(ragService.chat(request));
    }

    @PostMapping(value = "/chat/completions-stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chatStream(@Valid @RequestBody ChatCompletionRequest request) {
        log.info("Chat stream request: model={}, messages={}",
                request.getModel(),
                request.getMessages().size());

        StreamingResponseBody responseBody = outputStream -> {
            ragService.chatStream(request)
                    .doOnNext(chunk -> {
                        try {
                            outputStream.write(chunk.getBytes(StandardCharsets.UTF_8));
                            outputStream.flush();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .doOnError(error -> {
                        try {
                            outputStream.write("data: {\"error\":\"INTERNAL_ERROR\"}\n\ndata: [DONE]\n\n".getBytes());
                            outputStream.flush();
                        } catch (IOException ignored) {}
                    })
                    .blockLast();
        };

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(responseBody);
    }
}