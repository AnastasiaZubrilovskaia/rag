package ru.neoflex.rag;

import org.junit.jupiter.api.Test;
import ru.neoflex.rag.service.EmbeddingCacheService;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingCacheServiceTest {
    private final EmbeddingCacheService cacheService = new EmbeddingCacheService();

    @Test
    void shouldCacheAndRetrieveEmbedding() {
        String text = "test text";
        float[] embedding = {0.1f, 0.2f, 0.3f};

        cacheService.put(text, embedding);
        float[] cached = cacheService.get(text);

        assertThat(cached).isEqualTo(embedding);
        assertThat(cacheService.contains(text)).isTrue();
    }

    @Test
    void shouldReturnNullForMissingKey() {
        float[] cached = cacheService.get("missing");
        assertThat(cached).isNull();
    }

    @Test
    void shouldReturnFalseForMissingKey() {
        boolean exists = cacheService.contains("missing");

        assertThat(exists).isFalse();
    }

    @Test
    void shouldClearCache() {
        cacheService.put("test", new float[]{0.1f});
        cacheService.clear();
        assertThat(cacheService.size()).isZero();
        assertThat(cacheService.contains("test")).isFalse();
    }

    @Test
    void shouldReturnCorrectSize() {
        cacheService.put("text1", new float[]{0.1f});
        cacheService.put("text2", new float[]{0.2f});

        int size = cacheService.size();

        assertThat(size).isEqualTo(2);
    }
}