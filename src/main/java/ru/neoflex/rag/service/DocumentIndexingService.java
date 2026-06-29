package ru.neoflex.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIndexingService {
    private final ChunkingService chunkingService;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingCacheService embeddingCacheService;

    public int indexDocument(UUID documentId, String fileName, String content) {
        List<String> chunks = chunkingService.chunk(content);
        List<Document> documents = new ArrayList<>();

        int cachedCount = 0;

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);

            if (embeddingCacheService.contains(chunk)) {
                cachedCount++;
                log.debug("Chunk {} already in cache, position: {}", chunk.length(), i);
            }

            documents.add(createDocument(documentId, fileName, chunk, i));
        }

        log.info("Document {}: chunks={}, cached={}", fileName, chunks.size(), cachedCount);

        vectorStoreService.saveDocuments(documents);
        return documents.size();
    }

    private Document createDocument(UUID documentId, String fileName, String chunk, int position) {
        return new Document(chunk, Map.of(
                "documentId", documentId.toString(),
                "fileName", fileName,
                "position", position
        ));
    }
}