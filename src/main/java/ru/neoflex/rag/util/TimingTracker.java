package ru.neoflex.rag.util;

import lombok.Getter;

@Getter
public class TimingTracker {
    private final long startTime;
    private long retrievalStart;
    private long retrievalEnd;
    private long promptStart;
    private long promptEnd;
    private long generationStart;
    private long generationEnd;

    public TimingTracker() {
        this.startTime = System.currentTimeMillis();
    }

    public void startRetrieval() {
        this.retrievalStart = System.currentTimeMillis();
    }

    public void endRetrieval() {
        this.retrievalEnd = System.currentTimeMillis();
    }

    public void startPrompt() {
        this.promptStart = System.currentTimeMillis();
    }

    public void endPrompt() {
        this.promptEnd = System.currentTimeMillis();
    }

    public void startGeneration() {
        this.generationStart = System.currentTimeMillis();
    }

    public void endGeneration() {
        this.generationEnd = System.currentTimeMillis();
    }

    public long getRetrievalMs() {
        return retrievalEnd > 0 ? retrievalEnd - retrievalStart : 0;
    }

    public long getPromptMs() {
        return promptEnd > 0 ? promptEnd - promptStart : 0;
    }

    public long getGenerationMs() {
        return generationEnd > 0 ? generationEnd - generationStart : 0;
    }

    public long getTotalMs() {
        return System.currentTimeMillis() - startTime;
    }
}