package ru.neoflex.rag.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "rag")
public class RagProperties {
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Search search;

    @Getter
    @Setter
    public static class Search {
        private Integer topK;
        private Double similarityThreshold;
    }
}
