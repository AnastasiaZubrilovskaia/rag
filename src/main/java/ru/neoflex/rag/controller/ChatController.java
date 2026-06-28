package ru.neoflex.rag.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.neoflex.rag.model.request.ChatCompletionRequest;
import ru.neoflex.rag.model.response.chat.ChatCompletionResponse;
import ru.neoflex.rag.service.RagService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1")
public class ChatController {
    private final RagService ragService;

    @PostMapping("/chat/completions")
    public ChatCompletionResponse chat(@Valid @RequestBody ChatCompletionRequest request) {

        return ragService.chat(request);
    }

}