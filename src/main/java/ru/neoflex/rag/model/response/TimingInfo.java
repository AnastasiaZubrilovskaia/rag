package ru.neoflex.rag.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TimingInfo {
    @JsonProperty("retrieval_ms")
    private Long retrievalMs;

    @JsonProperty("prompt_ms")
    private Long promptMs;

    @JsonProperty("generation_ms")
    private Long generationMs;

    @JsonProperty("total_ms")
    private Long totalMs;
}
