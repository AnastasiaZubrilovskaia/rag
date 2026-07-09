package ru.neoflex.rag.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import ru.neoflex.rag.model.response.ModelListResponse;
import ru.neoflex.rag.model.response.ModelResponse;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class ModelController {

    private final OllamaApi ollamaApi;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.ollama.chat.api-key:}")
    private String apiKey;

    @Operation(summary = "Get available models (local + cloud)")
    @GetMapping("/models")
    public ResponseEntity<ModelListResponse> getModels() {
        List<ModelResponse> modelResponses = new ArrayList<>();
        try {
            var listModelResponse = ollamaApi.listModels();
            List<OllamaApi.Model> models = listModelResponse.models();
            if (models != null) {
                for (OllamaApi.Model model : models) {
                    modelResponses.add(new ModelResponse(
                            model.name(),
                            "model",
                            "local"
                    ));
                }
                log.info("Found {} local models", models.size());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch local models: {}", e.getMessage());
        }

        if (apiKey != null && !apiKey.isEmpty()) {
            try {
                String url = "https://ollama.com/api/tags";
                String response = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(response);
                JsonNode modelsNode = root.path("models");

                if (modelsNode.isArray()) {
                    int cloudCount = 0;
                    for (JsonNode modelNode : modelsNode) {
                        String name = modelNode.path("name").asText();
                        if (!name.isEmpty()) {
                            modelResponses.add(new ModelResponse(
                                    name,
                                    "model",
                                    "cloud"
                            ));
                            cloudCount++;
                        }
                    }
                    log.info("Found {} cloud models", cloudCount);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch cloud models: {}", e.getMessage());
            }
        } else {
            log.warn("API key not configured, skipping cloud models");
        }

        ModelListResponse response = new ModelListResponse("list", modelResponses);
        log.info("Returned {} total models", modelResponses.size());
        return ResponseEntity.ok(response);
    }
}