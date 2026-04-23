package com.dong.ddrag.document.model.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public class DocumentQuery {

    private Long currentUserId;
    private Long groupId;
    private String groupRelation;
    private String fileName;
    private Long uploaderUserId;
    private String status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime uploadedFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime uploadedTo;

    public Long getCurrentUserId() {
        return currentUserId;
    }

    public void setCurrentUserId(Long currentUserId) {
        this.currentUserId = currentUserId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getGroupRelation() {
        return groupRelation;
    }

    public void setGroupRelation(String groupRelation) {
        this.groupRelation = groupRelation;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getUploaderUserId() {
        return uploaderUserId;
    }

    public void setUploaderUserId(Long uploaderUserId) {
        this.uploaderUserId = uploaderUserId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getUploadedFrom() {
        return uploadedFrom;
    }

    public void setUploadedFrom(LocalDateTime uploadedFrom) {
        this.uploadedFrom = uploadedFrom;
    }

    public LocalDateTime getUploadedTo() {
        return uploadedTo;
    }

    public void setUploadedTo(LocalDateTime uploadedTo) {
        this.uploadedTo = uploadedTo;
    }
}
