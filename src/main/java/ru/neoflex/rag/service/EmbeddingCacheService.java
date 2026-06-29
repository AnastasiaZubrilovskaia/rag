package ru.neoflex.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class EmbeddingCacheService {
    private final Map<String, float[]> cache = new ConcurrentHashMap<>();

    public float[] get(String text) {
        return cache.get(text);
    }

    public void put(String text, float[] embedding) {
        cache.put(text, embedding);
        log.info("Cached embedding for text length: {}", text.length());
    }

    public boolean contains(String text) {
        return cache.containsKey(text);
    }

    public int size() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
        log.info("Embedding cache cleared");
    }
}