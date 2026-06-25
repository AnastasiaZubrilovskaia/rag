package ru.neoflex.rag.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ChatCompletionRequest {
    @NotBlank
    private String model;

    @Valid
    @NotEmpty
    private List<ChatMessage> messages;

    private Boolean stream = false;
    private Double temperature;
    private Integer maxTokens;

}
