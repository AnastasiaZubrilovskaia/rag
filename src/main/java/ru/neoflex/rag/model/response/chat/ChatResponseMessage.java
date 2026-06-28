package ru.neoflex.rag.model.response.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ChatResponseMessage {
    private String role;
    private String content;

}