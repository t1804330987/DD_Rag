package com.dong.ddrag.document.model.vo;

import java.time.LocalDateTime;

public class DocumentListItemVO {

    private Long documentId;
    private Long groupId;
    private String fileName;
    private String fileExt;
    private String contentType;
    private Long fileSize;
    private String status;
    private String failureReason;
    private LocalDateTime uploadedAt;
    private Long uploaderUserId;
    private String uploaderUserCode;
    private String uploaderDisplayName;
    private String previewText;

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileExt() {
        return fileExt;
    }

    public void setFileExt(String fileExt) {
        this.fileExt = fileExt;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Long getUploaderUserId() {
        return uploaderUserId;
    }

    public void setUploaderUserId(Long uploaderUserId) {
        this.uploaderUserId = uploaderUserId;
    }

    public String getUploaderUserCode() {
        return uploaderUserCode;
    }

    public void setUploaderUserCode(String uploaderUserCode) {
        this.uploaderUserCode = uploaderUserCode;
    }

    public String getUploaderDisplayName() {
        return uploaderDisplayName;
    }

    public void setUploaderDisplayName(String uploaderDisplayName) {
        this.uploaderDisplayName = uploaderDisplayName;
    }

    public String getPreviewText() {
        return previewText;
    }

    public void setPreviewText(String previewText) {
        this.previewText = previewText;
    }
}
