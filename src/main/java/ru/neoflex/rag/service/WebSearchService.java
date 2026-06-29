package ru.neoflex.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchService {
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${searxng.base-url:http://localhost:8888}")
    private String searxngBaseUrl;

    public List<String> search(String query) {
        try {
            String url = searxngBaseUrl + "/search?q=" + query + "&format=json&categories=general";

            log.info("Searching web: {}", query);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.path("results");

            List<String> snippets = new ArrayList<>();
            for (JsonNode result : results) {
                String title = result.path("title").asText();
                String snippet = result.path("snippet").asText();
                String urlResult = result.path("url").asText();

                if (!snippet.isEmpty()) {
                    snippets.add(title + "\n" + snippet + "\nИсточник: " + urlResult);
                }

                if (snippets.size() >= 3) {
                    break;
                }
            }

            log.info("Found {} web results", snippets.size());
            return snippets;

        } catch (Exception e) {
            log.error("Web search failed: {}", e.getMessage());
            return List.of();
        }
    }

    public boolean isAvailable() {
        try {
            restTemplate.getForObject(searxngBaseUrl + "/health", String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}