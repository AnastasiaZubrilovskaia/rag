package ru.neoflex.rag.parser;

import org.springframework.web.multipart.MultipartFile;

public interface DocumentParser {
    boolean supports(String fileName);

    String parse(MultipartFile file);
}