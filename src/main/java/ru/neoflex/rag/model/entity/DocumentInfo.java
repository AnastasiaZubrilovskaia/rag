package ru.neoflex.rag.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentInfo {
    private UUID id;
    private String fileName;
    private DocumentStatus status;
    private Integer chunkCount;
}
