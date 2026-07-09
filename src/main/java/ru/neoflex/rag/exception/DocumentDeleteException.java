package ru.neoflex.rag.exception;

public class DocumentDeleteException extends RuntimeException {
    public DocumentDeleteException(String message) {
        super(message);
    }
}
