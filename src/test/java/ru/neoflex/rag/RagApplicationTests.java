package ru.neoflex.rag;

import io.qdrant.client.QdrantClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration",
        "spring.ai.ollama.base-url=http://localhost:11434",
        "spring.ai.ollama.embedding.base-url=http://localhost:11434",
        "spring.ai.ollama.embedding.options.model=nomic-embed-text",
        "spring.ai.ollama.chat.base-url=https://ollama.com",
        "spring.ai.ollama.chat.api-key=test-key",
        "spring.ai.ollama.chat.options.model=glm-4.7:cloud",
        "rag.web-search.enabled=false"
})
class RagApplicationTests {

    @TestConfiguration
    static class TestConfig {

        @Bean
        VectorStore mockVectorStore() {
            return Mockito.mock(VectorStore.class);
        }

        @Bean
        QdrantClient mockQdrantClient() {
            return Mockito.mock(QdrantClient.class);
        }
    }

    @Test
    void contextLoads() {
    }

}