package ru.neoflex.rag.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.neoflex.rag.model.entity.DocumentInfo;
import ru.neoflex.rag.model.entity.DocumentStatus;
import ru.neoflex.rag.model.response.DocumentResponse;
import ru.neoflex.rag.parser.DocumentParser;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class DocumentService {
    private final List<DocumentParser> parsers;
    private final DocumentIndexingService documentIndexingService;
    private final VectorStoreService vectorStoreService;

    /**
     * Пока используем in-memory хранилище.
     * Позже его можно заменить на БД без изменения контроллера.
     */
    private final Map<UUID, DocumentInfo> documents = new ConcurrentHashMap<>();

    public DocumentResponse upload(MultipartFile file) {
        UUID documentId = UUID.randomUUID();

        DocumentInfo documentInfo = DocumentInfo.builder()
                .id(documentId)
                .fileName(file.getOriginalFilename())
                .status(DocumentStatus.PROCESSING)
                .chunkCount(0)
                .build();

        documents.put(documentId, documentInfo);

        try {
            processDocument(documentId, file);
        } catch (Exception e) {

            documentInfo = DocumentInfo.builder()
                    .id(documentInfo.getId())
                    .fileName(documentInfo.getFileName())
                    .status(DocumentStatus.FAILED)
                    .chunkCount(0)
                    .build();

            documents.put(documentId, documentInfo);
            throw e;
        }

        return mapToResponse(documents.get(documentId));
    }

    /**
     * Сейчас выполняется синхронно.
     * Позже достаточно будет заменить вызов:
     * CompletableFuture.runAsync(() -> processDocument(...))
     * и сервис станет асинхронным.
     */
    private void processDocument(UUID documentId, MultipartFile file) {
        DocumentParser parser = getParser(file.getOriginalFilename());
        String text = parser.parse(file);

        int chunkCount = documentIndexingService.indexDocument(
                documentId,
                file.getOriginalFilename(),
                text
        );

        DocumentInfo completed = DocumentInfo.builder()
                .id(documentId)
                .fileName(file.getOriginalFilename())
                .status(DocumentStatus.COMPLETED)
                .chunkCount(chunkCount)
                .build();

        documents.put(documentId, completed);
    }

    public List<DocumentResponse> getDocuments() {
        return documents.values()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public void deleteDocument(UUID id) {
        documents.remove(id);

        vectorStoreService.deleteDocument(id);
    }

    private DocumentParser getParser(String fileName) {
        return parsers.stream()
                .filter(parser -> parser.supports(fileName))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unsupported file type: " + fileName
                        ));
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