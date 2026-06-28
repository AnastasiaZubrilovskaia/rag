package ru.neoflex.rag.model.response.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class Choice {
    private Integer index;
    private ChatResponseMessage message;

    @JsonProperty("finish_reason")
    private String finishReason;

}