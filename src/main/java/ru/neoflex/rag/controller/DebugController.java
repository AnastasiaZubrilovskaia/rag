package ru.neoflex.rag.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.neoflex.rag.model.request.ChatCompletionRequest;
import ru.neoflex.rag.model.response.DebugResponse;
import ru.neoflex.rag.service.RagService;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/rag")
public class DebugController {

    private final RagService ragService;

    @PostMapping("/debug")
    public ResponseEntity<DebugResponse> debug(@RequestBody ChatCompletionRequest request) {
        log.info("Debug request: {}", request);
        DebugResponse response = ragService.debug(request);
        return ResponseEntity.ok(response);
    }
}