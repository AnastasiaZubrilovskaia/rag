package ru.neoflex.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class EmbeddingCacheService {
    private final Map<String, float[]> cache = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> documentTexts = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 10_000;

    public float[] get(String text) {
        return cache.get(text);
    }

    public void put(String text, float[] embedding) {
        if (cache.size() >= MAX_CACHE_SIZE) {
            log.warn("Cache size limit reached ({}), clearing...", MAX_CACHE_SIZE);
            cache.clear();
        }
        cache.put(text, embedding);
        log.debug("Cached embedding for text length: {}", text.length());
    }

    public boolean contains(String text) {
        return cache.containsKey(text);
    }

    public int size() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
        documentTexts.clear();
        log.info("Embedding cache cleared");
    }

    public void registerDocumentTexts(UUID documentId, List<String> texts) {
        documentTexts.put(documentId, texts);
        log.debug("Registered {} texts for document: {}", texts.size(), documentId);
    }
    public void evictDocument(UUID documentId) {
        List<String> texts = documentTexts.remove(documentId);
        if (texts != null) {
            for (String text : texts) {
                cache.remove(text);
            }
            log.info("Evicted {} texts for document: {}", texts.size(), documentId);
        }
    }
}