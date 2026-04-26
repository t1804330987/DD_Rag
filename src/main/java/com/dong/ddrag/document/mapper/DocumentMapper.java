package com.dong.ddrag.document.mapper;

import com.dong.ddrag.document.model.dto.DocumentQuery;
import com.dong.ddrag.document.model.entity.DocumentEntity;
import com.dong.ddrag.document.model.vo.DocumentListItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DocumentMapper {

    int insert(DocumentEntity document);

    List<DocumentListItemVO> selectReadableDocuments(DocumentQuery query);

    DocumentEntity selectByIdAndGroupId(
            @Param("documentId") Long documentId,
            @Param("groupId") Long groupId
    );

    DocumentEntity selectByGroupIdAndFileHash(
            @Param("groupId") Long groupId,
            @Param("fileHash") String fileHash
    );

    int markDeleted(
            @Param("documentId") Long documentId,
            @Param("groupId") Long groupId
    );

    int updateStatus(
            @Param("documentId") Long documentId,
            @Param("groupId") Long groupId,
            @Param("status") String status,
            @Param("failureReason") String failureReason,
            @Param("processedAt") LocalDateTime processedAt
    );

    int markStaleProcessingDocumentsFailed(
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("failureReason") String failureReason,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("processedAt") LocalDateTime processedAt
    );

    int updatePreviewText(
            @Param("documentId") Long documentId,
            @Param("groupId") Long groupId,
            @Param("previewText") String previewText
    );
}
