package ru.neoflex.rag.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TechnicalTokenExtractor {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("(/[\\w\\-.]+)+"),
            Pattern.compile("[\\w\\-.]+\\.(md|java|pdf|txt|docx|yml|yaml|json|xml|properties)"),
            Pattern.compile("\\b[A-Z][A-Z_0-9]+\\b"),
            Pattern.compile("\\b[A-Z][a-z]+([A-Z][a-z]+)+\\b"),
            Pattern.compile("@(Get|Post|Put|Delete|Patch|RequestMapping)Mapping"),
            Pattern.compile("@[A-Za-z]+"),
            Pattern.compile("\\b(class|interface|enum)\\s+([A-Z][a-zA-Z0-9]+)"),
            Pattern.compile("\\b(public|private|protected)\\s+([A-Za-z]+)\\s+([a-z][a-zA-Z0-9]+)\\s*\\(")
    );

    public List<String> extractTokens(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        for (Pattern pattern : PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String token = matcher.group();
                if (token != null && token.length() > 1) {
                    tokens.add(token);
                }
            }
        }
        return tokens.stream().distinct().toList();
    }

    public boolean containsToken(String text, String token) {
        return text.contains(token);
    }
}