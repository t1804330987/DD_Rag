package com.dong.ddrag.assistant.model.entity;

import java.time.LocalDateTime;

public class AssistantMessageEntity {

    private Long id;
    private Long sessionId;
    private String role;
    private String toolMode;
    private Long groupId;
    private String content;
    private String structuredPayload;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getToolMode() {
        return toolMode;
    }

    public void setToolMode(String toolMode) {
        this.toolMode = toolMode;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStructuredPayload() {
        return structuredPayload;
    }

    public void setStructuredPayload(String structuredPayload) {
        this.structuredPayload = structuredPayload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
