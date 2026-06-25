package ru.neoflex.rag.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class QdrantRepository {
    private final VectorStore vectorStore;

    public void save(List<Document> documents) {
        vectorStore.add(documents);
    }
}
