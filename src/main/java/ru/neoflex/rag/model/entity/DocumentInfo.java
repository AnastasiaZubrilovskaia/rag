package ru.neoflex.rag.model.entity;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentInfo {
    private UUID id;
    private String fileName;
    private DocumentStatus status;
    private Integer chunkCount;
}
