package ru.neoflex.rag.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.neoflex.rag.model.response.ModelListResponse;
import ru.neoflex.rag.model.response.ModelResponse;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class ModelController {
    private final OllamaApi ollamaApi;

    @Operation(summary = "Get available models")
    @GetMapping("/models")
    public ResponseEntity<ModelListResponse> getModels() {
        try {
            OllamaApi.ListModelResponse listModelResponse = ollamaApi.listModels();
            List<OllamaApi.Model> models = listModelResponse.models();

            if (models.isEmpty()) {
                return ResponseEntity.ok(getDefaultResponse());
            }

            List<ModelResponse> modelResponses = models.stream()
                    .map(model -> new ModelResponse(
                            model.name(),
                            "model",
                            "local"
                    ))
                    .toList();

            ModelListResponse response = new ModelListResponse(
                    "list",
                    modelResponses
            );

            log.info("Returned {} available models", modelResponses.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.warn("Failed to fetch models from Ollama, using defaults: {}", e.getMessage());
            return ResponseEntity.ok(getDefaultResponse());
        }
    }

    private ModelListResponse getDefaultResponse() {
        ModelResponse defaultModel = new ModelResponse(
                "qwen2.5:7b",
                "model",
                "local"
        );

        return new ModelListResponse(
                "list",
                List.of(defaultModel)
        );
    }
}