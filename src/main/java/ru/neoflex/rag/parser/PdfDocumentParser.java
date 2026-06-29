package ru.neoflex.rag.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".pdf");
    }

    @Override
    public String parse(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(1);
                stripper.setEndPage(document.getNumberOfPages());
                stripper.setSortByPosition(true);

                return stripper.getText(document);
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse PDF file: " + e.getMessage(), e);
        }
    }
}