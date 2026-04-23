package com.dong.ddrag.document.service;

import com.dong.ddrag.common.enums.DocumentStatus;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.document.mapper.DocumentMapper;
import com.dong.ddrag.document.model.dto.DocumentQuery;
import com.dong.ddrag.document.model.dto.UploadDocumentRequest;
import com.dong.ddrag.document.model.entity.DocumentEntity;
import com.dong.ddrag.document.model.vo.DocumentListItemVO;
import com.dong.ddrag.document.model.vo.DocumentPreviewVO;
import com.dong.ddrag.groupmembership.service.GroupMembershipService;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.ingestion.mapper.DocumentChunkMapper;
import com.dong.ddrag.ingestion.model.entity.DocumentChunkEntity;
import com.dong.ddrag.ingestion.service.DocumentIngestionProcessor;
import com.dong.ddrag.ingestion.vector.VectorIngestionService;
import com.dong.ddrag.retrieval.elasticsearch.ElasticsearchChunkIndexService;
import com.dong.ddrag.storage.service.ObjectStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final int PREVIEW_MAX_LENGTH = 200;
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final int MAX_CONTENT_TYPE_LENGTH = 128;
    private static final int MAX_FILE_EXT_LENGTH = 16;
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "md", "pdf", "docx");

    private final DocumentMapper documentMapper;
    private final GroupMembershipService groupMembershipService;
    private final CurrentUserService currentUserService;
    private final ObjectStorageService objectStorageService;
    private final DocumentIngestionProcessor documentIngestionProcessor;
    private final DocumentChunkMapper documentChunkMapper;
    private final VectorIngestionService vectorIngestionService;
    private final ElasticsearchChunkIndexService elasticsearchChunkIndexService;

    public DocumentService(
            DocumentMapper documentMapper,
            GroupMembershipService groupMembershipService,
            CurrentUserService currentUserService,
            ObjectStorageService objectStorageService,
            DocumentIngestionProcessor documentIngestionProcessor,
            DocumentChunkMapper documentChunkMapper,
            VectorIngestionService vectorIngestionService,
            ElasticsearchChunkIndexService elasticsearchChunkIndexService
    ) {
        this.documentMapper = documentMapper;
        this.groupMembershipService = groupMembershipService;
        this.currentUserService = currentUserService;
        this.objectStorageService = objectStorageService;
        this.documentIngestionProcessor = documentIngestionProcessor;
        this.documentChunkMapper = documentChunkMapper;
        this.vectorIngestionService = vectorIngestionService;
        this.elasticsearchChunkIndexService = elasticsearchChunkIndexService;
    }

    @Transactional
    public Long uploadDocument(HttpServletRequest request, UploadDocumentRequest uploadRequest) {
        Long groupId = requireGroupId(uploadRequest.getGroupId());
        CurrentUserService.CurrentUser currentUser = requireGroupOwner(request, groupId);
        MultipartFile file = requireValidFile(uploadRequest.getFile());
        String fileName = extractFileName(file);
        String fileExt = extractFileExt(fileName);
        String bucket = objectStorageService.getDefaultBucket();
        String objectKey = buildObjectKey(groupId, currentUser.userId(), fileExt);
        DocumentEntity document = null;
        log.info("开始上传文档: groupId={}, userId={}, fileName={}, size={}, objectKey={}",
                groupId, currentUser.userId(), fileName, file.getSize(), objectKey);
        uploadFile(bucket, objectKey, file);
        log.info("对象存储上传完成: groupId={}, objectKey={}", groupId, objectKey);
        try {
            document = buildDocument(groupId, currentUser.userId(), file, fileName, fileExt, bucket, objectKey);
            documentMapper.insert(document);
            log.info("文档元数据入库完成: documentId={}, groupId={}, status={}",
                    document.getId(), groupId, document.getStatus());
            documentIngestionProcessor.process(document.getId(), groupId);
            log.info("文档ETL处理完成: documentId={}, groupId={}", document.getId(), groupId);
            syncSearchIndex(document);
            log.info("文档 ES 索引同步完成: documentId={}, groupId={}", document.getId(), groupId);
            markDocumentReady(document.getId(), groupId);
            log.info("文档状态更新完成: documentId={}, groupId={}, status={}",
                    document.getId(), groupId, DocumentStatus.READY.name());
            return document.getId();
        } catch (RuntimeException exception) {
            log.error("文档上传链路失败: groupId={}, objectKey={}, reason={}",
                    groupId, objectKey, exception.getMessage(), exception);
            compensateExternalIndexes(document);
            compensateUploadedObject(bucket, objectKey, exception);
            throw exception;
        }
    }

    public List<DocumentListItemVO> listDocuments(HttpServletRequest request, DocumentQuery query) {
        DocumentQuery validatedQuery = normalizeQuery(request, query);
        return documentMapper.selectReadableDocuments(validatedQuery);
    }

    public void softDeleteDocument(HttpServletRequest request, Long groupId, Long documentId) {
        requireGroupOwner(request, requireGroupId(groupId));
        if (documentId == null || documentId <= 0) {
            throw new BusinessException("文档ID非法");
        }
        if (documentMapper.markDeleted(documentId, groupId) == 0) {
            throw new BusinessException("文档不存在或已删除");
        }
        vectorIngestionService.deleteDocumentVectors(documentId);
        elasticsearchChunkIndexService.deleteDocumentChunks(documentId);
    }

    public DocumentPreviewVO previewDocument(HttpServletRequest request, Long groupId, Long documentId) {
        Long requiredGroupId = requireGroupId(groupId);
        groupMembershipService.requireGroupReadable(request, requiredGroupId);
        if (documentId == null || documentId <= 0) {
            throw new BusinessException("文档ID非法");
        }
        DocumentEntity document = documentMapper.selectByIdAndGroupId(documentId, requiredGroupId);
        if (document == null) {
            throw new BusinessException("文档不存在或已删除");
        }
        if (!DocumentStatus.READY.name().equals(document.getStatus())) {
            throw new BusinessException("文档尚未就绪，暂不可预览");
        }
        if (!StringUtils.hasText(document.getPreviewText())) {
            throw new BusinessException("文档暂无可预览内容");
        }
        DocumentPreviewVO preview = new DocumentPreviewVO();
        preview.setDocumentId(document.getId());
        preview.setFileName(document.getFileName());
        preview.setPreviewText(trimPreviewText(document.getPreviewText()));
        return preview;
    }

    private Long requireGroupId(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new BusinessException("groupId 非法");
        }
        return groupId;
    }

    private CurrentUserService.CurrentUser requireGroupOwner(HttpServletRequest request, Long groupId) {
        CurrentUserService.CurrentUser currentUser = groupMembershipService.requireGroupReadable(request, groupId);
        groupMembershipService.requireGroupOwner(request, groupId);
        return currentUser;
    }

    private MultipartFile requireValidFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("上传文件超过大小限制");
        }
        return file;
    }

    private String extractFileName(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFileName)) {
            throw new BusinessException("文件名非法");
        }
        String normalizedFileName = StringUtils.cleanPath(originalFileName);
        String fileName = normalizedFileName.substring(normalizedFileName.lastIndexOf('/') + 1);
        if (!StringUtils.hasText(fileName) || fileName.length() > MAX_FILE_NAME_LENGTH) {
            throw new BusinessException("文件名非法");
        }
        return fileName;
    }

    private String extractFileExt(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            throw new BusinessException("文件扩展名非法");
        }
        String fileExt = fileName.substring(dotIndex + 1).toLowerCase();
        if (fileExt.length() > MAX_FILE_EXT_LENGTH || !SUPPORTED_EXTENSIONS.contains(fileExt)) {
            throw new BusinessException("文件类型不支持");
        }
        return fileExt;
    }

    private String buildObjectKey(Long groupId, Long userId, String fileExt) {
        String fileId = UUID.randomUUID().toString().replace("-", "");
        return "groups/%d/users/%d/%s.%s".formatted(groupId, userId, fileId, fileExt);
    }

    private void uploadFile(String bucket, String objectKey, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            objectStorageService.putObject(
                    bucket,
                    objectKey,
                    inputStream,
                    file.getSize(),
                    normalizeContentType(file.getContentType())
            );
        } catch (IOException exception) {
            throw new BusinessException("读取上传文件失败");
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException("文档上传失败");
        }
    }

    private void compensateUploadedObject(String bucket, String objectKey, RuntimeException originalException) {
        try {
            objectStorageService.deleteObject(bucket, objectKey);
        } catch (RuntimeException compensationException) {
            originalException.addSuppressed(compensationException);
            log.warn(
                    "Failed to compensate uploaded object after metadata persistence failure, bucket={}, objectKey={}, reason={}",
                    bucket,
                    objectKey,
                    compensationException.getMessage()
            );
        }
    }

    private void compensateExternalIndexes(DocumentEntity document) {
        if (document == null || document.getId() == null) {
            return;
        }
        try {
            vectorIngestionService.deleteDocumentVectors(document.getId());
        } catch (RuntimeException exception) {
            log.warn("文档失败补偿时删除向量失败: documentId={}, reason={}", document.getId(), exception.getMessage());
        }
        try {
            elasticsearchChunkIndexService.deleteDocumentChunks(document.getId());
        } catch (RuntimeException exception) {
            log.warn("文档失败补偿时删除 ES 索引失败: documentId={}, reason={}", document.getId(), exception.getMessage());
        }
    }

    private void syncSearchIndex(DocumentEntity document) {
        List<DocumentChunkEntity> chunks = documentChunkMapper.selectByDocumentId(document.getId());
        elasticsearchChunkIndexService.indexReadyChunks(document.getFileName(), chunks);
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "application/octet-stream";
        }
        if (contentType.length() > MAX_CONTENT_TYPE_LENGTH) {
            throw new BusinessException("文件类型描述过长");
        }
        return contentType;
    }

    private void markDocumentReady(Long documentId, Long groupId) {
        int updated = documentMapper.updateStatus(
                documentId,
                groupId,
                DocumentStatus.READY.name(),
                null,
                LocalDateTime.now()
        );
        if (updated == 0) {
            throw new BusinessException("文档入库成功但状态更新失败");
        }
    }

    private DocumentQuery normalizeQuery(HttpServletRequest request, DocumentQuery query) {
        DocumentQuery safeQuery = query == null ? new DocumentQuery() : query;
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        safeQuery.setCurrentUserId(currentUser.userId());
        if (safeQuery.getGroupId() != null) {
            groupMembershipService.requireGroupReadable(request, requireGroupId(safeQuery.getGroupId()));
        }
        if (safeQuery.getUploaderUserId() != null && safeQuery.getUploaderUserId() <= 0) {
            throw new BusinessException("uploaderUserId 非法");
        }
        if (safeQuery.getUploadedFrom() != null
                && safeQuery.getUploadedTo() != null
                && safeQuery.getUploadedFrom().isAfter(safeQuery.getUploadedTo())) {
            throw new BusinessException("uploadedFrom 不能晚于 uploadedTo");
        }
        if (StringUtils.hasText(safeQuery.getGroupRelation())) {
            safeQuery.setGroupRelation(normalizeGroupRelation(safeQuery.getGroupRelation()));
        }
        if (StringUtils.hasText(safeQuery.getStatus())) {
            safeQuery.setStatus(normalizeStatus(safeQuery.getStatus()));
        }
        if (StringUtils.hasText(safeQuery.getFileName())) {
            safeQuery.setFileName(safeQuery.getFileName().trim());
        }
        return safeQuery;
    }

    private String normalizeGroupRelation(String groupRelation) {
        String normalized = groupRelation.trim().toUpperCase();
        return switch (normalized) {
            case "OWNER", "OWNED" -> "OWNED";
            case "MEMBER", "JOINED" -> "JOINED";
            default -> throw new BusinessException("groupRelation 非法");
        };
    }

    private String normalizeStatus(String status) {
        try {
            return DocumentStatus.valueOf(status.trim().toUpperCase()).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("status 非法");
        }
    }

    private String trimPreviewText(String previewText) {
        if (!StringUtils.hasText(previewText) || previewText.length() <= PREVIEW_MAX_LENGTH) {
            return previewText;
        }
        return previewText.substring(0, PREVIEW_MAX_LENGTH);
    }

    private DocumentEntity buildDocument(
            Long groupId,
            Long userId,
            MultipartFile file,
            String fileName,
            String fileExt,
            String bucket,
            String objectKey
    ) {
        LocalDateTime now = LocalDateTime.now();
        DocumentEntity document = new DocumentEntity();
        document.setGroupId(groupId);
        document.setUploaderUserId(userId);
        document.setFileName(fileName);
        document.setFileExt(fileExt);
        document.setContentType(normalizeContentType(file.getContentType()));
        document.setFileSize(file.getSize());
        document.setStorageBucket(bucket);
        document.setStorageObjectKey(objectKey);
        document.setStatus(DocumentStatus.PROCESSING.name());
        document.setDeleted(false);
        document.setUploadedAt(now);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        return document;
    }
}
