package ru.neoflex.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.neoflex.rag.exception.DocumentNotFoundException;
import ru.neoflex.rag.exception.QdrantException;
import ru.neoflex.rag.model.entity.DocumentInfo;
import ru.neoflex.rag.model.entity.DocumentStatus;
import ru.neoflex.rag.model.response.DocumentResponse;
import ru.neoflex.rag.parser.DocumentParser;
import ru.neoflex.rag.repository.QdrantDocumentRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {
    private final List<DocumentParser> parsers;
    private final DocumentIndexingService documentIndexingService;
    private final QdrantDocumentRepository documentRepository;
    private final ExecutorService documentExecutor;
    private final EmbeddingCacheService embeddingCacheService;

    private final Map<UUID, DocumentInfo> documentStore = new ConcurrentHashMap<>();

    /**
     * Загрузка документа (асинхронная)
     * Сразу возвращает статус PROCESSING, обработка идет в фоне
     */
    public DocumentResponse upload(MultipartFile file) {
        UUID documentId = UUID.randomUUID();
        String fileName = file.getOriginalFilename();

        DocumentInfo docInfo = DocumentInfo.builder()
                .id(documentId)
                .fileName(fileName)
                .status(DocumentStatus.PROCESSING)
                .chunkCount(0)
                .build();
        documentStore.put(documentId, docInfo);

        log.info("Starting async upload: {}", fileName);

        CompletableFuture.runAsync(() -> {
            try {
                processDocument(documentId, file);
                docInfo.setStatus(DocumentStatus.COMPLETED);
                log.info("Document processed successfully: {}", fileName);
            } catch (Exception e) {
                log.error("Failed to process document: {}", fileName, e);
                docInfo.setStatus(DocumentStatus.FAILED);
            }
        }, documentExecutor);

        return mapToResponse(docInfo);
    }

    private void processDocument(UUID documentId, MultipartFile file) {
        DocumentParser parser = getParser(file.getOriginalFilename());
        String text = parser.parse(file);

        int chunkCount = documentIndexingService.indexDocument(
                documentId,
                file.getOriginalFilename(),
                text
        );

        DocumentInfo docInfo = documentStore.get(documentId);
        if (docInfo != null) {
            docInfo.setChunkCount(chunkCount);
        }
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
        log.info("Starting delete for document: {}", id);

        boolean existsInQdrant = false;
        try {
            List<DocumentInfo> qdrantDocs = documentRepository.getAllDocuments();
            existsInQdrant = qdrantDocs.stream()
                    .anyMatch(doc -> doc.getId().equals(id));
        } catch (Exception e) {
            log.warn("Failed to check Qdrant for document: {}", id, e);
        }
        if (!existsInQdrant) {
            log.warn("Document not found in Qdrant: {}", id);
            throw new DocumentNotFoundException("Документ с id " + id + " не найден");
        }


        try {
            documentRepository.deleteByDocumentId(id);
            log.info("Successfully deleted from Qdrant: {}", id);
        } catch (Exception e) {
            log.error("Failed to delete from Qdrant. Document {} remains in system.", id, e);
            throw new QdrantException(
                    "Не удалось удалить документ из векторной БД. " +
                            "Повторите попытку позже. ", e);
        }

        try {
            embeddingCacheService.evictDocument(id);
            documentStore.remove(id);
            log.info("Document fully deleted: {}", id);
        } catch (Exception e) {
            log.warn("Failed to evict from cache: {}", id, e);
        }
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