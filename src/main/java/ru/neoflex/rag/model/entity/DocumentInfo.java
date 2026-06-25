package ru.neoflex.rag.model.entity;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class DocumentInfo {
    private UUID id;
    private String fileName;
    private DocumentStatus status;
    private Integer chunkCount;
}
