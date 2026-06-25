package ru.neoflex.rag.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import ru.neoflex.rag.repository.QdrantRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VectorStoreService {
    private final QdrantRepository qdrantRepository;

    public void saveDocuments(List<Document> documents) {
        qdrantRepository.save(documents);
    }
}