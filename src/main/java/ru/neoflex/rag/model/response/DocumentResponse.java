package ru.neoflex.rag.model.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class DocumentResponse {
    private UUID id;
    private String fileName;
    private String status;
    private Integer chunkCount;
}
