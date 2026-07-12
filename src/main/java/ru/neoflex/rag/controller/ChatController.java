package ru.neoflex.rag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.neoflex.rag.model.request.ChatCompletionRequest;
import ru.neoflex.rag.model.response.chat.ChatCompletionResponse;
import ru.neoflex.rag.service.RagService;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1")
public class ChatController {
    private final RagService ragService;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/chat/completions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void chat(@Valid @RequestBody ChatCompletionRequest request, HttpServletResponse response) throws IOException {
        log.info("Chat request: model={}, messages={}, stream={}",
                request.getModel(),
                request.getMessages().size(),
                request.getStream());

        if (Boolean.TRUE.equals(request.getStream())) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            ragService.chatStream(request)
                    .onErrorResume(error -> {
                        log.error("Streaming failed", error);
                        return Flux.just(
                                "data: {\"error\":\"INTERNAL_ERROR\"}\n\n",
                                "data: [DONE]\n\n"
                        );
                    })
                    .doOnNext(chunk -> {
                        try {
                            response.getOutputStream().write(chunk.getBytes(StandardCharsets.UTF_8));
                            response.getOutputStream().flush();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .blockLast();
            return;
        }

        ChatCompletionResponse chatResponse = ragService.chat(request);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), chatResponse);
    }
}
