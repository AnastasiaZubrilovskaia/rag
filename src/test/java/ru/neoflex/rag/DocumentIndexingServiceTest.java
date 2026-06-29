package ru.neoflex.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import ru.neoflex.rag.service.ChunkingService;
import ru.neoflex.rag.service.DocumentIndexingService;
import ru.neoflex.rag.service.EmbeddingCacheService;
import ru.neoflex.rag.service.VectorStoreService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentIndexingServiceTest {

    @Mock
    private ChunkingService chunkingService;

    @Mock
    private EmbeddingCacheService embeddingCacheService;

    @Mock
    private VectorStoreService vectorStoreService;

    @InjectMocks
    private DocumentIndexingService indexingService;

    @Test
    void shouldIndexDocumentAndSaveAllChunks() {
        UUID documentId = UUID.randomUUID();
        when(chunkingService.chunk("content")).thenReturn(List.of("chunk1", "chunk2"));
        when(embeddingCacheService.contains("chunk1")).thenReturn(true);
        when(embeddingCacheService.contains("chunk2")).thenReturn(false);

        int count = indexingService.indexDocument(documentId, "test.txt", "content");

        assertThat(count).isEqualTo(2);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStoreService).saveDocuments(captor.capture());

        List<Document> savedDocuments = captor.getValue();
        assertThat(savedDocuments).hasSize(2);
        assertThat(savedDocuments.get(0).getText()).isEqualTo("chunk1");
        assertThat(savedDocuments.get(1).getText()).isEqualTo("chunk2");
        assertThat(savedDocuments.get(0).getMetadata()).containsEntry("fileName", "test.txt");
        assertThat(savedDocuments.get(0).getMetadata()).containsEntry("position", 0);
        assertThat(savedDocuments.get(1).getMetadata()).containsEntry("position", 1);
    }
}
