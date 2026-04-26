package com.dong.ddrag.document.mapper;

import com.dong.ddrag.document.model.entity.DocumentUploadChunkEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentUploadChunkMapper {

    int insert(DocumentUploadChunkEntity chunk);

    int upsert(DocumentUploadChunkEntity chunk);

    List<DocumentUploadChunkEntity> selectByUploadId(@Param("uploadId") String uploadId);
}
