package com.dong.ddrag.document.model.dto;

import org.springframework.web.multipart.MultipartFile;

public record UploadChunkRequest(
        String uploadId,
        Integer chunkIndex,
        String chunkHash,
        MultipartFile chunk
) {
}
