package ru.neoflex.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import io.qdrant.client.QdrantClient;
import ru.neoflex.rag.repository.QdrantDocumentRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QdrantDocumentRepositoryTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private QdrantClient qdrantClient;

    @InjectMocks
    private QdrantDocumentRepository repository;

    @Test
    void searchShouldNotFailWithTimeLimiterAnnotationRemoved() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(new Document("test")));

        assertDoesNotThrow(() -> repository.search("spring boot", 3, 0.5));
    }
}
