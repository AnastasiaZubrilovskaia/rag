package ru.neoflex.rag.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.neoflex.rag.model.entity.DocumentInfo;
import ru.neoflex.rag.model.entity.DocumentStatus;
import ru.neoflex.rag.model.response.DocumentResponse;
import ru.neoflex.rag.parser.DocumentParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class DocumentService {
    private final List<DocumentParser> parsers;
    private final ChunkingService chunkingService;
    private final VectorStoreService vectorStoreService;
    private final Map<UUID, DocumentInfo> documents = new ConcurrentHashMap<>();

    public DocumentResponse upload(MultipartFile file){
        DocumentParser parser = getParser(file.getOriginalFilename());
        String text = parser.parse(file);
        List<String> chunks = chunkingService.chunk(text);
        List<Document> vectorDocuments = createDocuments(chunks, file.getOriginalFilename());

        vectorStoreService.saveDocuments(vectorDocuments);

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

    private DocumentParser getParser(String fileName) {

        return parsers.stream()
                .filter(parser -> parser.supports(fileName))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Unsupported file type"
                        )
                );
    }

    private List<Document> createDocuments(List<String> chunks, String fileName) {
        List<Document> documents = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = Map.of("source", fileName, "chunkIndex", i);

            documents.add(new Document(chunks.get(i), metadata)
            );
        }

        return documents;
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
