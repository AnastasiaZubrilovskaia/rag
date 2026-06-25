package ru.neoflex.rag.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.neoflex.rag.model.entity.DocumentInfo;
import ru.neoflex.rag.model.entity.DocumentStatus;
import ru.neoflex.rag.model.response.DocumentResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DocumentService {
    private final Map<UUID, DocumentInfo> documents = new ConcurrentHashMap<>();

    public DocumentResponse upload(MultipartFile file){
        UUID documentId = UUID.randomUUID();

        DocumentInfo documentInfo = DocumentInfo.builder()
                .id(documentId)
                .fileName(file.getOriginalFilename())
                .status(DocumentStatus.COMPLETED)
                .chunkCount(0)
                .build();

        documents.put(documentId, documentInfo);

        return mapToResponse(documentInfo);
    }

    public List<DocumentResponse> getDocuments(){
        return  documents.values()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public void deleteDocument(UUID id) {
        documents.remove(id);
    }

    private DocumentResponse mapToResponse(DocumentInfo documentInfo) {

        return DocumentResponse.builder()
                .id(documentInfo.getId())
                .fileName(documentInfo.getFileName())
                .status(documentInfo.getStatus().name())
                .chunkCount(documentInfo.getChunkCount())
                .build();
    }
}
