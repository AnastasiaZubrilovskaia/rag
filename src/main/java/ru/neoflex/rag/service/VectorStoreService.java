package ru.neoflex.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import ru.neoflex.rag.repository.QdrantDocumentRepository;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {
    private final QdrantDocumentRepository documentRepository;
    private final EmbeddingCacheService embeddingCacheService;
    private final EmbeddingModel embeddingModel;

    public void saveDocuments(List<Document> documents) {
        for (Document doc : documents) {
            String text = doc.getText();
            if (!embeddingCacheService.contains(text)) {
                float[] embedding = embeddingModel.embed(text);
                embeddingCacheService.put(text, embedding);
            }
        }
        documentRepository.save(documents);
    }

    public List<Document> search(String query, int topK, double similarityThreshold) {
        if (!embeddingCacheService.contains(query)) {
            float[] embedding = embeddingModel.embed(query);
            embeddingCacheService.put(query, embedding);
            log.info("Query embedding cached: {}", query.length());
        } else {
            log.info("Query embedding found in cache: {}", query.length());
        }

        return documentRepository.search(query, topK, similarityThreshold);
    }

    public void deleteDocument(UUID documentId) {
        documentRepository.deleteByDocumentId(documentId);
        log.info("Document deleted: {}", documentId);
    }
}