package com.dong.ddrag.document.model.dto;

import org.springframework.web.multipart.MultipartFile;

public class UploadDocumentRequest {

    private Long groupId;
    private MultipartFile file;

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }
}
