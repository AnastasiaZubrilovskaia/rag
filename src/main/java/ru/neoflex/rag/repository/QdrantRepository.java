package ru.neoflex.rag.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class QdrantRepository {

    private final VectorStore vectorStore;

    public void save(List<Document> documents) {
        vectorStore.add(documents);
    }

    public List<Document> search(String query, int topK, double threshold) {

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .build();

        return vectorStore.similaritySearch(request);
    }

    public void deleteByDocumentId(UUID documentId) {
        // TODO:
        // Spring AI пока не умеет красиво удалять документы
        // по metadata через универсальный VectorStore.
        //
        // Для Qdrant сделаем отдельную реализацию.
    }
}