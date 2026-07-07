package ru.neoflex.rag.parser;

import de.l3s.boilerpipe.BoilerpipeProcessingException;
import de.l3s.boilerpipe.extractors.ArticleExtractor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class HtmlDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String fileName) {
        return fileName != null &&
                (fileName.toLowerCase().endsWith(".html") ||
                        fileName.toLowerCase().endsWith(".htm"));
    }

    @Override
    public String parse(MultipartFile file) {
        try {
            String html = new String(file.getBytes(), StandardCharsets.UTF_8);
            String cleanHtml = Jsoup.clean(html, Safelist.basic());


            Document doc = Jsoup.parse(cleanHtml);
            doc.select("script, style, nav, footer, header, aside, .ad, .advertisement, .menu, .sidebar, .banner").remove();
            String text = ArticleExtractor.INSTANCE.getText(html);

            if (text == null || text.isEmpty() || text.length() < 50) {
                text = doc.body().text();
            }

            text = text.replaceAll("\\s+", " ")
                    .replaceAll("(?m)^\\s*$\\r?\\n", "")
                    .trim();

            if (text.isEmpty()) {
                throw new RuntimeException("No text content extracted from HTML");
            }

            return text;

        } catch (IOException e) {
            throw new RuntimeException("Cannot read HTML file", e);
        } catch (BoilerpipeProcessingException e) {
            throw new RuntimeException("Cannot extract text from HTML", e);
        }
    }
}