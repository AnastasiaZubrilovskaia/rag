package ru.neoflex.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.neoflex.rag.model.entity.DocumentInfo;
import ru.neoflex.rag.model.entity.DocumentStatus;
import ru.neoflex.rag.model.response.DocumentResponse;
import ru.neoflex.rag.parser.DocumentParser;
import ru.neoflex.rag.repository.QdrantDocumentRepository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {
    private final List<DocumentParser> parsers;
    private final DocumentIndexingService documentIndexingService;
    private final QdrantDocumentRepository documentRepository;
    private final ExecutorService documentExecutor;

    /**
     * Загрузка документа (асинхронная)
     * Сразу возвращает статус PROCESSING, обработка идет в фоне
     */
    public DocumentResponse upload(MultipartFile file) {
        UUID documentId = UUID.randomUUID();
        String fileName = file.getOriginalFilename();

        log.info("Starting async upload: {}", fileName);

        CompletableFuture.runAsync(() -> {
            try {
                log.info("Processing document: {}", fileName);
                processDocument(documentId, file);
                log.info("Document processed successfully: {}", fileName);
            } catch (Exception e) {
                log.error("Failed to process document: {}", fileName, e);
            }
        }, documentExecutor);

        return DocumentResponse.builder()
                .id(documentId)
                .fileName(fileName)
                .status(DocumentStatus.PROCESSING.name())
                .chunkCount(0)
                .build();
    }

    private void processDocument(UUID documentId, MultipartFile file) {
        DocumentParser parser = getParser(file.getOriginalFilename());
        String text = parser.parse(file);

        documentIndexingService.indexDocument(
                documentId,
                file.getOriginalFilename(),
                text
        );
    }

    /**
     * Список документов из Qdrant
     */
    public List<DocumentResponse> getDocuments() {
        List<DocumentInfo> documents = documentRepository.getAllDocuments();
        log.info("Retrieved {} documents", documents.size());
        return documents.stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Удаление документа из Qdrant
     */
    public void deleteDocument(UUID id) {
        documentRepository.deleteByDocumentId(id);
        log.info("Document deleted: {}", id);
    }

    private DocumentParser getParser(String fileName) {
        return parsers.stream()
                .filter(parser -> parser.supports(fileName))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("Unsupported file type: " + fileName));
    }

    private DocumentResponse mapToResponse(DocumentInfo docInfo) {
        return DocumentResponse.builder()
                .id(docInfo.getId())
                .fileName(docInfo.getFileName())
                .status(docInfo.getStatus().name())
                .chunkCount(docInfo.getChunkCount())
                .build();
    }
}