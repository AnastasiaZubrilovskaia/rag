package ru.neoflex.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import ru.neoflex.rag.repository.QdrantDocumentRepository;
import ru.neoflex.rag.service.EmbeddingCacheService;
import ru.neoflex.rag.service.VectorStoreService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorStoreServiceTest {

    @Mock
    private QdrantDocumentRepository documentRepository;

    @Mock
    private EmbeddingCacheService embeddingCacheService;

    @Mock
    private EmbeddingModel embeddingModel;

    @InjectMocks
    private VectorStoreService vectorStoreService;

    @Test
    void shouldSaveDocumentsAndCacheMissingEmbeddings() {
        Document cachedDocument = new Document("cached", java.util.Map.of("fileName", "cached.txt"));
        Document newDocument = new Document("new", java.util.Map.of("fileName", "new.txt"));

        when(embeddingCacheService.contains("cached")).thenReturn(true);
        when(embeddingCacheService.contains("new")).thenReturn(false);
        when(embeddingModel.embed("new")).thenReturn(new float[]{1f, 2f});

        vectorStoreService.saveDocuments(List.of(cachedDocument, newDocument));

        verify(embeddingCacheService).contains("cached");
        verify(embeddingCacheService).contains("new");
        verify(embeddingModel).embed("new");
        verify(embeddingCacheService).put(eq("new"), any(float[].class));
        verify(documentRepository).save(List.of(cachedDocument, newDocument));
    }

    @Test
    void shouldCacheQueryEmbeddingAndSearch() {
        String query = "search query";
        when(embeddingCacheService.contains(query)).thenReturn(false);
        when(embeddingModel.embed(query)).thenReturn(new float[]{3f, 4f});
        when(documentRepository.search(query, 3, 0.7)).thenReturn(List.of(new Document("hit", java.util.Map.of("fileName", "file.txt"))));

        List<Document> results = vectorStoreService.search(query, 3, 0.7);

        assertThat(results).hasSize(1);
        verify(embeddingModel).embed(query);
        verify(embeddingCacheService).put(eq(query), any(float[].class));
        verify(documentRepository).search(query, 3, 0.7);
    }

    @Test
    void shouldUseCachedQueryEmbeddingWhenSearching() {
        String query = "cached query";
        when(embeddingCacheService.contains(query)).thenReturn(true);
        when(documentRepository.search(query, 1, 0.5)).thenReturn(List.of(new Document("hit", java.util.Map.of("fileName", "file2.txt"))));

        List<Document> results = vectorStoreService.search(query, 1, 0.5);

        assertThat(results).hasSize(1);
        verify(embeddingModel, never()).embed(query);
        verify(embeddingCacheService, never()).put(eq(query), any(float[].class));
        verify(documentRepository).search(query, 1, 0.5);
    }

    @Test
    void shouldDeleteDocument() {
        UUID documentId = UUID.randomUUID();

        vectorStoreService.deleteDocument(documentId);

        verify(documentRepository).deleteByDocumentId(documentId);
    }
}
