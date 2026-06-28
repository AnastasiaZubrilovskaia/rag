package ru.neoflex.rag.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import ru.neoflex.rag.repository.QdrantRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VectorStoreService {
    private final QdrantRepository repository;

    public void saveDocuments(List<Document> documents) {
        repository.save(documents);
    }

    public List<Document> search(String query, int topK,
                                 double similarityThreshold) {

        return repository.search(query, topK, similarityThreshold);
    }

    public void deleteDocument(UUID documentId) {
        repository.deleteByDocumentId(documentId);
    }
}