package com.dong.ddrag.assistant.model.entity;

import java.time.LocalDateTime;

public class AssistantSessionContextEntity {

    private Long sessionId;
    private String sessionMemory;
    private String compactSummary;
    private String runtimeMemoryState;
    private Long sessionMemoryBaseMessageId;
    private Long sessionMemoryRangeEndMessageId;
    private Long compactSummaryBaseMessageId;
    private Long compactSummaryRangeEndMessageId;
    private Long contextVersion;
    private LocalDateTime updatedAt;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionMemory() {
        return sessionMemory;
    }

    public void setSessionMemory(String sessionMemory) {
        this.sessionMemory = sessionMemory;
    }

    public String getCompactSummary() {
        return compactSummary;
    }

    public void setCompactSummary(String compactSummary) {
        this.compactSummary = compactSummary;
    }

    public String getRuntimeMemoryState() {
        return runtimeMemoryState;
    }

    public void setRuntimeMemoryState(String runtimeMemoryState) {
        this.runtimeMemoryState = runtimeMemoryState;
    }

    public Long getSessionMemoryBaseMessageId() {
        return sessionMemoryBaseMessageId;
    }

    public void setSessionMemoryBaseMessageId(Long sessionMemoryBaseMessageId) {
        this.sessionMemoryBaseMessageId = sessionMemoryBaseMessageId;
    }

    public Long getSessionMemoryRangeEndMessageId() {
        return sessionMemoryRangeEndMessageId;
    }

    public void setSessionMemoryRangeEndMessageId(Long sessionMemoryRangeEndMessageId) {
        this.sessionMemoryRangeEndMessageId = sessionMemoryRangeEndMessageId;
    }

    public Long getCompactSummaryBaseMessageId() {
        return compactSummaryBaseMessageId;
    }

    public void setCompactSummaryBaseMessageId(Long compactSummaryBaseMessageId) {
        this.compactSummaryBaseMessageId = compactSummaryBaseMessageId;
    }

    public Long getCompactSummaryRangeEndMessageId() {
        return compactSummaryRangeEndMessageId;
    }

    public void setCompactSummaryRangeEndMessageId(Long compactSummaryRangeEndMessageId) {
        this.compactSummaryRangeEndMessageId = compactSummaryRangeEndMessageId;
    }

    public Long getContextVersion() {
        return contextVersion;
    }

    public void setContextVersion(Long contextVersion) {
        this.contextVersion = contextVersion;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
