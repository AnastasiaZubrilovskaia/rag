package ru.neoflex.rag.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.neoflex.rag.model.response.ModelListResponse;
import ru.neoflex.rag.model.response.ModelResponse;

import java.util.List;

@RestController
@RequestMapping("/v1")
public class ModelController {
    @Operation(summary = "Get available models")
    @GetMapping("/models")
    public ResponseEntity<ModelListResponse> getModels() {

        ModelResponse model = new ModelResponse(
                "glm-4.7",
                "model",
                "local"
        );

        ModelListResponse response = new ModelListResponse(
                "list",
                List.of(model)
        );

        return ResponseEntity.ok(response);
    }
}
