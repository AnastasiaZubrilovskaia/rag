package ru.neoflex.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.neoflex.rag.properties.RagProperties;
import ru.neoflex.rag.service.ChunkingService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingServiceTest {
    private ChunkingService chunkingService;

    @BeforeEach
    void setUp() {
        RagProperties properties = new RagProperties();
        properties.setChunkSize(10);
        properties.setChunkOverlap(3);
        chunkingService = new ChunkingService(properties);
    }

    @Test
    void shouldChunkTextWithOverlap() {
        String text = "12345678901234567890";

        List<String> chunks = chunkingService.chunk(text);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).isEqualTo("1234567890");
        assertThat(chunks.get(1)).isEqualTo("8901234567");
        assertThat(chunks.get(2)).isEqualTo("567890");
    }

    @Test
    void shouldReturnSingleChunkForShortText() {
        String text = "123";

        List<String> chunks = chunkingService.chunk(text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo("123");
    }

    @Test
    void shouldReturnEmptyListForEmptyText() {
        String text = "";
        List<String> chunks = chunkingService.chunk(text);

        assertThat(chunks).isEmpty();
    }

    @Test
    void shouldHandleExactChunkSize() {
        String text = "1234567890";

        List<String> chunks = chunkingService.chunk(text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo("1234567890");
    }
}