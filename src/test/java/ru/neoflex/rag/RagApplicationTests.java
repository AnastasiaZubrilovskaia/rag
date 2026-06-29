package ru.neoflex.rag;

import io.qdrant.client.QdrantClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration")
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
