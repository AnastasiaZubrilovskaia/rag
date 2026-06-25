package ru.neoflex.rag.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {
    @NotBlank
    private String role;

    @NotBlank
    private String content;
}
