package ru.neoflex.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import ru.neoflex.rag.repository.QdrantDocumentRepository;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {
    private final QdrantDocumentRepository documentRepository;

    public void saveDocuments(List<Document> documents) {
        documentRepository.save(documents);
    }

    public List<Document> search(String query, int topK, double similarityThreshold) {
        return documentRepository.search(query, topK, similarityThreshold);
    }

}