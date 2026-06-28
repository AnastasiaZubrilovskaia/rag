package ru.neoflex.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.neoflex.rag.model.entity.DocumentInfo;
import ru.neoflex.rag.model.response.DocumentResponse;
import ru.neoflex.rag.parser.DocumentParser;
import ru.neoflex.rag.repository.QdrantDocumentRepository;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {
    private final List<DocumentParser> parsers;
    private final DocumentIndexingService documentIndexingService;
    private final QdrantDocumentRepository documentRepository;

    /**
     * Загрузка документа
     */
    public DocumentResponse upload(MultipartFile file) {
        UUID documentId = UUID.randomUUID();
        String fileName = file.getOriginalFilename();

        log.info("Starting upload: {}", fileName);

        try {
            processDocument(documentId, file);
            DocumentInfo docInfo = documentRepository.getAllDocuments().stream()
                    .filter(doc -> doc.getId().equals(documentId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Document not found after processing"));

            log.info("Upload completed: {}, chunks: {}", fileName, docInfo.getChunkCount());
            return mapToResponse(docInfo);

        } catch (Exception e) {
            log.error("Upload failed: {}", fileName, e);
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }
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