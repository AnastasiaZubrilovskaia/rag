package ru.neoflex.rag.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ModelListResponse {
    private String object;

    private List<ModelResponse> data;
}
