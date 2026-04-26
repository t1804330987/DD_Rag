package com.dong.ddrag.document.mapper;

import com.dong.ddrag.document.model.entity.DocumentUploadSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentUploadSessionMapper {

    int insert(DocumentUploadSessionEntity session);

    DocumentUploadSessionEntity selectByUploadId(@Param("uploadId") String uploadId);

    DocumentUploadSessionEntity selectLatestReusableSession(
            @Param("groupId") Long groupId,
            @Param("uploaderUserId") Long uploaderUserId,
            @Param("fileHash") String fileHash
    );

    int updateStatusAndMergedObjectKey(
            @Param("uploadId") String uploadId,
            @Param("status") String status,
            @Param("mergedObjectKey") String mergedObjectKey,
            @Param("updatedAt") java.time.LocalDateTime updatedAt
    );
}
