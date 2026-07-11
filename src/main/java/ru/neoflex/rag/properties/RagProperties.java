package ru.neoflex.rag.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Search search;
    private WebSearch webSearch;
    private Filter filter;

    @Getter
    @Setter
    public static class Search {
        private Integer topK;
        private Double similarityThreshold;
    }

    @Getter
    @Setter
    public static class WebSearch {
        private boolean enabled;
        private int minDocuments;
        private int resultsLimit;
    }

    @Getter
    @Setter
    public static class Filter {
        private Double strongThreshold;
        private Double borderlineThreshold;
        private Integer minContextSources;
        private boolean exactTermGuardEnabled;
    }
}
