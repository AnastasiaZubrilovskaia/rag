package ru.neoflex.rag.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.neoflex.rag.model.response.DocumentResponse;
import ru.neoflex.rag.service.DocumentService;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentResponse upload(@RequestParam("file") MultipartFile file
    ) {
        return documentService.upload(file);
    }

    @GetMapping
    public List<DocumentResponse> getDocuments() {
        return documentService.getDocuments();
    }

    @DeleteMapping("/{id}")
    public void deleteDocument(@PathVariable UUID id) {
        documentService.deleteDocument(id);
    }
}
