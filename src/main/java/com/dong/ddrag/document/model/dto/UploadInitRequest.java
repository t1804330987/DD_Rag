package com.dong.ddrag.document.model.dto;

public record UploadInitRequest(
        Long groupId,
        String fileName,
        Long fileSize,
        String contentType,
        String fileHash,
        Long chunkSize,
        Integer chunkCount
) {
}
