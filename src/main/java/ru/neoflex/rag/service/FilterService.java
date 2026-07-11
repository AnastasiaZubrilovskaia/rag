package ru.neoflex.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import ru.neoflex.rag.properties.RagProperties;
import ru.neoflex.rag.util.TechnicalTokenExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilterService {

    private final RagProperties ragProperties;
    private final TechnicalTokenExtractor tokenExtractor;

    /**
     * Проверяет, является ли документ strong
     */
    private boolean isStrong(Document doc) {
        if (ragProperties.getFilter() == null) {
            return false;
        }
        double threshold = ragProperties.getFilter().getStrongThreshold();
        Double score = (Double) doc.getMetadata().getOrDefault("score", 0.0);
        return score >= threshold;
    }

    /**
     * Проверяет, есть ли хотя бы один strong документ
     */
    private boolean hasStrongSource(List<Document> documents) {
        return documents.stream().anyMatch(this::isStrong);
    }

    /**
     * Проверяет, достаточно ли контекста для ответа
     */
    private boolean hasEnoughContext(List<Document> documents) {
        if (documents.isEmpty() || ragProperties.getFilter() == null) {
            return false;
        }

        int minSources = ragProperties.getFilter().getMinContextSources();

        boolean hasVeryStrong = documents.stream()
                .anyMatch(doc -> {
                    Double score = (Double) doc.getMetadata().getOrDefault("score", 0.0);
                    return score >= 0.9;
                });

        if (hasVeryStrong) {
            log.info("Very strong document found (score >= 0.9), enough context");
            return true;
        }

        return documents.size() >= minSources;
    }


    public List<Document> applyExactTermGuard(String question, List<Document> documents) {
        if (ragProperties.getFilter() == null || !ragProperties.getFilter().isExactTermGuardEnabled()) {
            return documents;
        }

        if (question == null || question.isEmpty() || documents.isEmpty()) {
            return documents;
        }

        List<String> tokens = tokenExtractor.extractTokens(question);
        if (tokens.isEmpty()) {
            log.info("No technical tokens found in question");
            return documents;
        }

        log.info("Found technical tokens: {}", tokens);

        List<Document> boostedDocuments = new ArrayList<>();
        boolean anyTokenFound = false;

        for (Document doc : documents) {
            String text = doc.getText();
            boolean hasToken = false;

            for (String token : tokens) {
                if (tokenExtractor.containsToken(text, token)) {
                    hasToken = true;
                    anyTokenFound = true;
                    break;
                }
            }

            if (hasToken) {
                Document boosted = new Document(doc.getText(), doc.getMetadata());
                boosted.getMetadata().put("score", 1.0);
                boosted.getMetadata().put("boosted", true);
                boostedDocuments.add(boosted);
                log.info("Boosted document for token match");
            } else {
                boostedDocuments.add(doc);
            }
        }

        if (!anyTokenFound) {
            log.warn("No technical tokens found in any document, refusing generation");
            return List.of();
        }

        return boostedDocuments.stream()
                .sorted((a, b) -> {
                    Boolean aBoosted = (Boolean) a.getMetadata().getOrDefault("boosted", false);
                    Boolean bBoosted = (Boolean) b.getMetadata().getOrDefault("boosted", false);
                    return bBoosted.compareTo(aBoosted);
                })
                .collect(Collectors.toList());
    }

    /**
     * Двухуровневый фильтр: проверяет, можно ли генерировать ответ
     */
    public FilterResult applyFilter(List<Document> documents) {
        if (ragProperties.getFilter() == null) {
            log.info("Filter not configured, allowing generation");
            return FilterResult.of(true, "NONE", "Filter not configured");
        }

        boolean hasStrong = hasStrongSource(documents);
        boolean hasEnough = hasEnoughContext(documents);

        if (hasStrong && hasEnough) {
            log.info("Filter passed: STRONG level");
            return FilterResult.of(true, "STRONG", "Has strong sources and enough context");
        }

        if (hasEnough) {
            log.info("Filter passed: BORDERLINE level");
            return FilterResult.of(true, "BORDERLINE", "Has enough sources but no strong sources");
        }

        if (hasStrong) {
            log.info("Filter passed: BORDERLINE level (has strong but not enough)");
            return FilterResult.of(true, "BORDERLINE", "Has strong sources but not enough context");
        }

        log.warn("Filter failed: no strong sources and not enough context");
        return FilterResult.of(false, "NONE", "No strong sources and insufficient context");
    }


    public static class FilterResult {
        private final boolean allowed;
        private final String level;
        private final String reason;

        private FilterResult(boolean allowed, String level, String reason) {
            this.allowed = allowed;
            this.level = level;
            this.reason = reason;
        }

        public static FilterResult of(boolean allowed, String level, String reason) {
            return new FilterResult(allowed, level, reason);
        }

        public boolean isAllowed() { return allowed; }
        public String getLevel() { return level; }
        public String getReason() { return reason; }
    }
}