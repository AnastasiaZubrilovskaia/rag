package ru.neoflex.rag.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentIndexingService {
    private final ChunkingService chunkingService;
    private final VectorStoreService vectorStoreService;

    public int indexDocument(UUID documentId, String fileName, String content) {
        List<String> chunks = chunkingService.chunk(content);
        List<Document> documents = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            documents.add(createDocument(documentId, fileName, chunks.get(i), i));
        }

        vectorStoreService.saveDocuments(documents);
        return documents.size();
    }

    private Document createDocument(UUID documentId, String fileName, String chunk, int position) {
        return new Document(chunk, Map.of(
                        "documentId", documentId.toString(),
                        "fileName", fileName,
                        "position", position
                )
        );
    }
}