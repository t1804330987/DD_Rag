package com.dong.ddrag.ingestion.transformer;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ingestion.chunking")
public class ChunkingProperties {

    private int targetTokens = 500;
    private int maxTokens = 800;
    private int overlapTokens = 80;

    public ChunkingProperties() {
    }

    public ChunkingProperties(int targetTokens, int maxTokens, int overlapTokens) {
        this.targetTokens = targetTokens;
        this.maxTokens = maxTokens;
        this.overlapTokens = overlapTokens;
    }

    public int getTargetTokens() {
        return targetTokens;
    }

    public void setTargetTokens(int targetTokens) {
        this.targetTokens = targetTokens;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getOverlapTokens() {
        return overlapTokens;
    }

    public void setOverlapTokens(int overlapTokens) {
        this.overlapTokens = overlapTokens;
    }
}
