package ru.neoflex.rag.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SourceInfo {
    private String documentId;
    private String fileName;
    private Integer position;
    private Double score;
    private String text;
}
