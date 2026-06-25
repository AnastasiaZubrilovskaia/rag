package ru.neoflex.rag.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.neoflex.rag.model.response.DocumentResponse;

import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {
    public DocumentResponse upload(MultipartFile file){
        return null;
    }

    public List<DocumentResponse> getDocuments(){
        return  List.of();
    }

    public void deleteDocument(UUID id) {
    }
}
