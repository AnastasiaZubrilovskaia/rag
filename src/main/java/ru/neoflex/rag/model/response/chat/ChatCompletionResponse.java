package ru.neoflex.rag.model.response.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import ru.neoflex.rag.model.response.SourceInfo;
import ru.neoflex.rag.model.response.TimingInfo;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionResponse {
    private String id;
    private String object;
    private Long created;
    private String model;
    private List<Choice> choices;
    private Usage usage;

    @JsonProperty("request_id")
    private String requestId;

    private List<SourceInfo> sources;
    private TimingInfo timings;
}