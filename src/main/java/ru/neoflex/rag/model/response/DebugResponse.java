package ru.neoflex.rag.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class DebugResponse {
    private String requestId;
    private String question;
    private List<SourceInfo> sources;
    private TimingInfo timings;
    private Boolean exactTermMatch;
    private String filterLevel;
    private Boolean generationAllowed;
    private String message;
    private List<String> extractedTokens;
}