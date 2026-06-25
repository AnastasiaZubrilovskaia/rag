package ru.neoflex.rag.parser;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class TxtDocumentParser implements DocumentParser{
    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".txt");
    }

    @Override
    public String parse(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        }catch (IOException e) {
            throw new RuntimeException("Cannot parse txt file", e);
        }
    }
}
