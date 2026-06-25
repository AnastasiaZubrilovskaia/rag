package ru.neoflex.rag.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.neoflex.rag.properties.RagProperties;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChunkingService {
    private final RagProperties ragProperties;

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        int chunkSize = ragProperties.getChunkSize();
        int chunkOverlap = ragProperties.getChunkOverlap();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(
                    start + chunkSize,
                    text.length()
            );
            chunks.add(text.substring(start, end));

            if (end == text.length()) {
                break;
            }

            start = end - chunkOverlap;
        }

        return chunks;
    }
}
