package com.dong.ddrag.document.model.vo;

import java.util.List;

public record UploadInitResponse(
        boolean instantUpload,
        Long documentId,
        String uploadId,
        List<Integer> uploadedChunks,
        Long chunkSize,
        Integer chunkCount
) {

    public static UploadInitResponse instant(Long documentId) {
        return new UploadInitResponse(true, documentId, null, List.of(), null, null);
    }

    public static UploadInitResponse uploadSession(String uploadId, Long chunkSize, Integer chunkCount) {
        return new UploadInitResponse(false, null, uploadId, List.of(), chunkSize, chunkCount);
    }

    public static UploadInitResponse uploadSession(
            String uploadId,
            List<Integer> uploadedChunks,
            Long chunkSize,
            Integer chunkCount
    ) {
        return new UploadInitResponse(false, null, uploadId, uploadedChunks, chunkSize, chunkCount);
    }
}
