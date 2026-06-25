package ru.neoflex.rag.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ModelResponse {
    private String id;
    private String object;

    @JsonProperty("owned_by")
    private String ownedBy;
}
