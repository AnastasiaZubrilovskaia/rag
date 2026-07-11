package ru.neoflex.rag.repository;

import static io.qdrant.client.ConditionFactory.matchKeyword;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import io.qdrant.client.grpc.Points.ScrollPoints;
import io.qdrant.client.grpc.Points.ScrollResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import ru.neoflex.rag.exception.QdrantException;
import ru.neoflex.rag.exception.RetryableException;
import ru.neoflex.rag.model.entity.DocumentInfo;
import ru.neoflex.rag.model.entity.DocumentStatus;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Репозиторий для работы с Qdrant векторной БД.
 * Использует гибридный подход:
 * - Spring AI VectorStore для простых операций (сохранение, поиск)
 * - Qdrant Client для сложных операций (удаление по фильтру, получение всех документов)
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class QdrantDocumentRepository {
    private static final String COLLECTION_NAME = "rag-documents";
    private static final String DOCUMENT_ID_KEY = "documentId";
    private static final String FILE_NAME_KEY = "fileName";
    private final VectorStore vectorStore;
    private final QdrantClient qdrantClient;

    @Value("${client.qdrant.timeout:5s}")
    private String qdrantTimeout;

    /**
     * Сохраняет документы в векторную БД.
     * Использует Spring AI VectorStore.
     */
    @Retry(name = "qdrantRetry")
    @CircuitBreaker(name = "qdrantCircuitBreaker")
    @Bulkhead(name = "qdrantBulkhead")
    public void save(List<Document> documents) {
        try{
            vectorStore.add(documents);
            log.info("Saved {} documents to Qdrant", documents.size());
        } catch (Exception e) {
            log.error("Failed to save to Qdrant", e);
            throw new RetryableException("Failed to save  documents to Qdrant", e);
        }

    }

    /**
     * Выполняет поиск релевантных документов по текстовому запросу.
     * Использует Spring AI VectorStore.
     *
     * @param query текстовый запрос пользователя
     * @param topK количество возвращаемых результатов
     * @param threshold порог сходства (0.0 - 1.0)
     * @return список найденных документов
     */
    @Retry(name = "qdrantRetry", fallbackMethod = "searchFallback")
    @CircuitBreaker(name = "qdrantCircuitBreaker", fallbackMethod = "searchFallback")
    @Bulkhead(name = "qdrantBulkhead")
    public List<Document> search(String query, int topK, double threshold) {
        try{
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(threshold)
                    .build();

            List<Document> results = vectorStore.similaritySearch(request);
            log.info("Found {} documents for query: {}", results.size(), query);
            return results;
        } catch (Exception e) {
            log.error("Search failed for query: {}", query, e);
            throw new RetryableException("Search failed: " + query, e);
        }

    }

    public List<Document> searchFallback(String query, int topK, double threshold, Exception e) {
        log.warn("Search fallback activated for query: {}", query);
        return Collections.emptyList();
    }

    /**
     * Удаляет все чанки документа по его идентификатору.
     * Использует Qdrant Client для удаления по фильтру.
     */
    @Retry(name = "qdrantRetry", fallbackMethod = "deleteFallback")
    @CircuitBreaker(name = "qdrantCircuitBreaker", fallbackMethod = "deleteFallback")
    @Bulkhead(name = "qdrantBulkhead")
    public void deleteByDocumentId(UUID documentId) {
        try {
            Points.Filter filter = Points.Filter.newBuilder()
                    .addMust(matchKeyword(DOCUMENT_ID_KEY, documentId.toString()))
                    .build();

            Points.PointsSelector pointsSelector = Points.PointsSelector.newBuilder()
                    .setFilter(filter)
                    .build();

            Points.DeletePoints deletePoints = Points.DeletePoints.newBuilder()
                    .setCollectionName(COLLECTION_NAME)
                    .setPoints(pointsSelector)
                    .build();

            qdrantClient.deleteAsync(deletePoints).get(parseTimeout(qdrantTimeout), TimeUnit.SECONDS);

            log.info("Deleted all chunks for document: {}", documentId);
        } catch (TimeoutException e) {
            log.error("Timeout deleting document from Qdrant: {}", documentId, e);
            throw new RetryableException("Qdrant timeout for document: " + documentId, e);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to delete document from Qdrant: {}", documentId, e);
            throw new RetryableException("Qdrant error: " + documentId, e);
        }
    }

    public void deleteFallback(UUID documentId, Exception e) {
        log.error("Delete fallback for document: {}", documentId, e);
        throw new QdrantException(
                "Failed to delete document from Qdrant after retries: " + documentId,
                e
        );
    }

    /**
     * Получает список всех документов из векторной БД.
     * Использует Qdrant Client для обхода всех точек.
     * Документы группируются по documentId, для каждого вычисляется количество чанков.
     */
    public List<DocumentInfo> getAllDocuments() {
        try {
            ScrollPoints scrollRequest = ScrollPoints.newBuilder()
                    .setCollectionName(COLLECTION_NAME)
                    .setLimit(1000)
                    .build();

            ScrollResponse response = qdrantClient.scrollAsync(scrollRequest).get();
            List<RetrievedPoint> allPoints = response.getResultList();

            if (allPoints.isEmpty()) {
                log.info("No documents found in Qdrant");
                return Collections.emptyList();
            }

            Map<UUID, List<RetrievedPoint>> groupedByDocId = new HashMap<>();
            for (RetrievedPoint point : allPoints) {
                if (point.getPayloadMap().containsKey(DOCUMENT_ID_KEY)) {
                    String docIdStr = point.getPayloadMap()
                            .get(DOCUMENT_ID_KEY)
                            .getStringValue();
                    UUID docId = UUID.fromString(docIdStr);

                    groupedByDocId.computeIfAbsent(docId, k -> new ArrayList<>())
                            .add(point);
                }
            }

            return groupedByDocId.entrySet().stream()
                    .map(entry -> {
                        UUID docId = entry.getKey();
                        List<RetrievedPoint> chunks = entry.getValue();

                        String fileName = "unknown";
                        if (!chunks.isEmpty() && chunks.get(0).getPayloadMap().containsKey(FILE_NAME_KEY)) {
                            fileName = chunks.get(0)
                                    .getPayloadMap()
                                    .get(FILE_NAME_KEY)
                                    .getStringValue();
                        }

                        return DocumentInfo.builder()
                                .id(docId)
                                .fileName(fileName)
                                .status(DocumentStatus.COMPLETED)
                                .chunkCount(chunks.size())
                                .build();
                    })
                    .sorted(Comparator.comparing(DocumentInfo::getFileName))
                    .collect(Collectors.toList());

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to get all documents", e);
            return Collections.emptyList();
        }
    }

    private long parseTimeout(String timeoutStr) {
        String value = timeoutStr.replace("s", "").replace("ms", "");
        return Long.parseLong(value);
    }
}