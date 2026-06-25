package ru.neoflex.rag.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentIndexingService {
    private final ChunkingService chunkingService;
    private final VectorStoreService vectorStoreService;

    public int indexDocument(UUID documentId, String fileName, String content) {
        List<String> chunks = chunkingService.chunk(content);

        List<Document> documents = chunks.stream()
                .map(chunk -> createDocument(
                        documentId,
                        fileName,
                        chunk
                ))
                .toList();

        vectorStoreService.saveDocuments(documents);

        return chunks.size();
    }

    private Document createDocument(UUID documentId, String fileName, String chunk) {
        return new Document(chunk, java.util.Map.of("documentId", documentId.toString(), "fileName", fileName));
    }
}