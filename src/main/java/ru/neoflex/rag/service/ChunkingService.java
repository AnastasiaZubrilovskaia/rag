package ru.neoflex.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.neoflex.rag.properties.RagProperties;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkingService {
    private final RagProperties ragProperties;

    public List<String> chunk(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        text = normalizeText(text);

        List<String> chunks = new ArrayList<>();
        int chunkSize = ragProperties.getChunkSize();
        int chunkOverlap = ragProperties.getChunkOverlap();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());

            if (end < text.length()) {
                int wordEnd = findWordBoundary(text, end);
                if (wordEnd > start) {
                    end = wordEnd;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end == text.length()) {
                break;
            }

            start = Math.max(start + 1, end - chunkOverlap);
        }
        log.info("Chunked text into {} chunks", chunks.size());
        return chunks;
    }

    private String normalizeText(String text) {
        text = text.replaceAll("\\s+", " ");
        text = text.replaceAll("(?m)^\\s*$\\r?\\n", "");
        return text;
    }

    private int findWordBoundary(String text, int position) {
        for (int i = position; i < Math.min(position + 50, text.length()); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                return i;
            }
        }
        return position;
    }
}
