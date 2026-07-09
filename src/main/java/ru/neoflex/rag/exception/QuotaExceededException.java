package ru.neoflex.rag.exception;

import lombok.Getter;

@Getter
public class QuotaExceededException extends RuntimeException {
    private final Long retryAfterSeconds;

    public QuotaExceededException(String message) {
        super(message);
        this.retryAfterSeconds = null;
    }

    public QuotaExceededException(String message, Long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
