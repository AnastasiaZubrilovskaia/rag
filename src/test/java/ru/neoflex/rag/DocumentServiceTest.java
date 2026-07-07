package ru.neoflex.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import ru.neoflex.rag.model.entity.DocumentInfo;
import ru.neoflex.rag.model.entity.DocumentStatus;
import ru.neoflex.rag.model.response.DocumentResponse;
import ru.neoflex.rag.parser.DocumentParser;
import ru.neoflex.rag.repository.QdrantDocumentRepository;
import ru.neoflex.rag.service.DocumentIndexingService;
import ru.neoflex.rag.service.DocumentService;
import ru.neoflex.rag.service.EmbeddingCacheService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private List<DocumentParser> parsers = new ArrayList<>();

    @Mock
    private DocumentIndexingService indexingService;

    @Mock
    private QdrantDocumentRepository repository;

    @Mock
    private MultipartFile file;

    @Mock
    private EmbeddingCacheService embeddingCacheService;

    private ExecutorService documentExecutor = Executors.newSingleThreadExecutor();

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(parsers, indexingService, repository, documentExecutor, embeddingCacheService);
    }

    @Test
    void shouldUploadDocumentSuccessfully() throws IOException, InterruptedException {
        String fileName = "test.txt";
        when(file.getOriginalFilename()).thenReturn(fileName);

        DocumentParser parser = mock(DocumentParser.class);
        when(parser.supports(fileName)).thenReturn(true);
        when(parser.parse(any())).thenReturn("content");
        parsers.add(parser);

        DocumentResponse response = documentService.upload(file);

        assertThat(response).isNotNull();
        assertThat(response.getFileName()).isEqualTo(fileName);
        assertThat(response.getStatus()).isEqualTo("PROCESSING");
        assertThat(response.getChunkCount()).isEqualTo(0);

        Thread.sleep(100);

        verify(indexingService, timeout(1000)).indexDocument(any(), any(), any());
    }

    @Test
    void shouldGetDocuments() {
        UUID docId = UUID.randomUUID();
        DocumentInfo docInfo = DocumentInfo.builder()
                .id(docId)
                .fileName("test.txt")
                .status(DocumentStatus.COMPLETED)
                .chunkCount(1)
                .build();
        when(repository.getAllDocuments()).thenReturn(List.of(docInfo));

        List<DocumentResponse> docs = documentService.getDocuments();

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getFileName()).isEqualTo("test.txt");
    }

    @Test
    void shouldDeleteDocument() {
        UUID docId = UUID.randomUUID();
        documentService.deleteDocument(docId);
        verify(repository).deleteByDocumentId(docId);
        verify(embeddingCacheService).evictDocument(docId);
    }
}