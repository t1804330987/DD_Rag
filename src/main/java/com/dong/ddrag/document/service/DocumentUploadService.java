package com.dong.ddrag.document.service;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.document.mapper.DocumentUploadChunkMapper;
import com.dong.ddrag.document.mapper.DocumentMapper;
import com.dong.ddrag.document.mapper.DocumentUploadSessionMapper;
import com.dong.ddrag.document.model.dto.UploadInitRequest;
import com.dong.ddrag.document.model.dto.UploadChunkRequest;
import com.dong.ddrag.document.model.entity.DocumentUploadChunkEntity;
import com.dong.ddrag.document.model.entity.DocumentEntity;
import com.dong.ddrag.document.model.entity.DocumentUploadSessionEntity;
import com.dong.ddrag.document.model.vo.UploadInitResponse;
import com.dong.ddrag.document.model.vo.UploadStatusResponse;
import com.dong.ddrag.groupmembership.service.GroupMembershipService;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.storage.service.ObjectStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentUploadService {

    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final int MAX_CONTENT_TYPE_LENGTH = 128;
    private static final int MAX_FILE_HASH_LENGTH = 128;
    private static final int MAX_FILE_EXT_LENGTH = 16;
    private static final long MAX_FILE_SIZE = 256L * 1024 * 1024;
    private static final long MAX_CHUNK_SIZE = 10L * 1024 * 1024;
    private static final long SESSION_EXPIRE_HOURS = 24L;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "md", "pdf", "docx");
    private static final String UPLOAD_STATUS_INIT = "INIT";
    private static final String UPLOAD_STATUS_UPLOADING = "UPLOADING";
    private static final String UPLOAD_STATUS_COMPLETING = "COMPLETING";
    private static final String UPLOAD_STATUS_COMPLETED = "COMPLETED";
    private static final String OCTET_STREAM = "application/octet-stream";

    private final DocumentMapper documentMapper;
    private final DocumentUploadSessionMapper documentUploadSessionMapper;
    private final DocumentUploadChunkMapper documentUploadChunkMapper;
    private final GroupMembershipService groupMembershipService;
    private final DocumentService documentService;
    private final ObjectStorageService objectStorageService;

    public DocumentUploadService(
            DocumentMapper documentMapper,
            DocumentUploadSessionMapper documentUploadSessionMapper,
            DocumentUploadChunkMapper documentUploadChunkMapper,
            GroupMembershipService groupMembershipService,
            DocumentService documentService,
            ObjectStorageService objectStorageService
    ) {
        this.documentMapper = documentMapper;
        this.documentUploadSessionMapper = documentUploadSessionMapper;
        this.documentUploadChunkMapper = documentUploadChunkMapper;
        this.groupMembershipService = groupMembershipService;
        this.documentService = documentService;
        this.objectStorageService = objectStorageService;
    }

    @Transactional
    public UploadInitResponse initUpload(HttpServletRequest request, UploadInitRequest uploadRequest) {
        NormalizedInitRequest normalizedRequest = validateInitRequest(uploadRequest);
        Long groupId = normalizedRequest.groupId();
        CurrentUserService.CurrentUser currentUser = groupMembershipService.requireGroupOwner(request, groupId);
        DocumentEntity existingDocument = documentMapper.selectByGroupIdAndFileHash(groupId, normalizedRequest.fileHash());
        if (existingDocument != null && "READY".equals(existingDocument.getStatus())) {
            Long documentId = documentService.createInstantUploadedDocument(
                    groupId,
                    currentUser.userId(),
                    existingDocument,
                    normalizedRequest.fileName()
            );
            return UploadInitResponse.instant(documentId);
        }
        DocumentUploadSessionEntity existingSession = documentUploadSessionMapper.selectLatestReusableSession(
                groupId,
                currentUser.userId(),
                normalizedRequest.fileHash()
        );
        if (existingSession != null) {
            List<Integer> uploadedChunks = documentUploadChunkMapper.selectByUploadId(existingSession.getUploadId()).stream()
                    .map(DocumentUploadChunkEntity::getChunkIndex)
                    .toList();
            return UploadInitResponse.uploadSession(
                    existingSession.getUploadId(),
                    uploadedChunks,
                    existingSession.getChunkSize(),
                    existingSession.getChunkCount()
            );
        }
        DocumentUploadSessionEntity session = buildUploadSession(groupId, currentUser.userId(), normalizedRequest);
        documentUploadSessionMapper.insert(session);
        return UploadInitResponse.uploadSession(session.getUploadId(), session.getChunkSize(), session.getChunkCount());
    }

    @Transactional
    public List<Integer> uploadChunk(HttpServletRequest request, UploadChunkRequest uploadRequest) {
        DocumentUploadSessionEntity session = requireOwnedActiveSession(request, uploadRequest.uploadId());
        MultipartFile chunk = requireChunk(uploadRequest, session);
        String chunkHash = normalizeFileHash(uploadRequest.chunkHash());
        String objectKey = buildChunkObjectKey(session.getGroupId(), session.getUploadId(), uploadRequest.chunkIndex());
        LocalDateTime now = LocalDateTime.now();
        try {
            objectStorageService.putObject(
                    session.getStorageBucket(),
                    objectKey,
                    chunk.getInputStream(),
                    chunk.getSize(),
                    OCTET_STREAM
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("分片上传失败");
        }

        DocumentUploadChunkEntity uploadChunk = new DocumentUploadChunkEntity();
        uploadChunk.setUploadId(session.getUploadId());
        uploadChunk.setChunkIndex(uploadRequest.chunkIndex());
        uploadChunk.setChunkSize(chunk.getSize());
        uploadChunk.setChunkHash(chunkHash);
        uploadChunk.setStorageBucket(session.getStorageBucket());
        uploadChunk.setStorageObjectKey(objectKey);
        uploadChunk.setUploadedAt(now);
        uploadChunk.setCreatedAt(now);
        uploadChunk.setUpdatedAt(now);
        documentUploadChunkMapper.upsert(uploadChunk);
        documentUploadSessionMapper.updateStatusAndMergedObjectKey(
                session.getUploadId(),
                UPLOAD_STATUS_UPLOADING,
                null,
                now
        );
        return documentUploadChunkMapper.selectByUploadId(session.getUploadId()).stream()
                .map(DocumentUploadChunkEntity::getChunkIndex)
                .toList();
    }

    @Transactional
    public Long completeUpload(HttpServletRequest request, String uploadId) {
        DocumentUploadSessionEntity session = requireOwnedActiveSession(request, uploadId);
        List<DocumentUploadChunkEntity> chunks = documentUploadChunkMapper.selectByUploadId(uploadId).stream()
                .sorted(Comparator.comparing(DocumentUploadChunkEntity::getChunkIndex))
                .toList();
        ensureAllChunksPresent(session, chunks);
        String objectKey = buildFinalObjectKey(session);
        LocalDateTime now = LocalDateTime.now();
        documentUploadSessionMapper.updateStatusAndMergedObjectKey(
                uploadId,
                UPLOAD_STATUS_COMPLETING,
                null,
                now
        );
        try {
            objectStorageService.composeObject(
                    session.getStorageBucket(),
                    objectKey,
                    chunks.stream().map(DocumentUploadChunkEntity::getStorageObjectKey).toList(),
                    session.getContentType()
            );
            Long documentId = documentService.finalizeUploadedDocument(
                    session.getGroupId(),
                    session.getUploaderUserId(),
                    session.getFileName(),
                    session.getFileExt(),
                    session.getContentType(),
                    session.getFileSize(),
                    session.getFileHash(),
                    session.getStorageBucket(),
                    objectKey
            );
            documentUploadSessionMapper.updateStatusAndMergedObjectKey(
                    uploadId,
                    UPLOAD_STATUS_COMPLETED,
                    objectKey,
                    LocalDateTime.now()
            );
            return documentId;
        } catch (RuntimeException exception) {
            try {
                objectStorageService.deleteObject(session.getStorageBucket(), objectKey);
            } catch (RuntimeException ignored) {
            }
            throw exception;
        }
    }

    public UploadStatusResponse getUploadStatus(HttpServletRequest request, String uploadId) {
        DocumentUploadSessionEntity session = requireOwnedActiveSession(request, uploadId);
        List<Integer> uploadedChunks = documentUploadChunkMapper.selectByUploadId(uploadId).stream()
                .map(DocumentUploadChunkEntity::getChunkIndex)
                .toList();
        return new UploadStatusResponse(
                session.getStatus(),
                uploadedChunks,
                uploadedChunks.size(),
                session.getChunkCount()
        );
    }

    private NormalizedInitRequest validateInitRequest(UploadInitRequest uploadRequest) {
        if (uploadRequest == null) {
            throw new BusinessException("上传初始化请求不能为空");
        }
        Long groupId = requireGroupId(uploadRequest.groupId());
        String fileName = sanitizeFileName(uploadRequest.fileName());
        String fileExt = extractFileExt(fileName);
        long fileSize = requirePositive(uploadRequest.fileSize(), "fileSize 非法");
        if (fileSize > MAX_FILE_SIZE) {
            throw new BusinessException("上传文件超过大小限制");
        }
        String contentType = normalizeContentType(uploadRequest.contentType());
        String fileHash = normalizeFileHash(uploadRequest.fileHash());
        long chunkSize = requirePositive(uploadRequest.chunkSize(), "chunkSize 非法");
        if (chunkSize > MAX_CHUNK_SIZE) {
            throw new BusinessException("chunkSize 超过限制");
        }
        int chunkCount = requirePositive(uploadRequest.chunkCount(), "chunkCount 非法");
        long expectedChunkCount = (fileSize + chunkSize - 1) / chunkSize;
        if (chunkCount != expectedChunkCount) {
            throw new BusinessException("chunkCount 与文件大小不匹配");
        }
        return new NormalizedInitRequest(groupId, fileName, fileExt, fileSize, contentType, fileHash, chunkSize, chunkCount);
    }

    private Long requireGroupId(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new BusinessException("groupId 非法");
        }
        return groupId;
    }

    private String sanitizeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException("文件名非法");
        }
        String normalizedFileName = StringUtils.cleanPath(fileName.trim());
        String sanitizedFileName = normalizedFileName.substring(normalizedFileName.lastIndexOf('/') + 1);
        if (!StringUtils.hasText(sanitizedFileName) || sanitizedFileName.length() > MAX_FILE_NAME_LENGTH) {
            throw new BusinessException("文件名非法");
        }
        return sanitizedFileName;
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

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "application/octet-stream";
        }
        String normalizedContentType = contentType.trim();
        if (normalizedContentType.length() > MAX_CONTENT_TYPE_LENGTH) {
            throw new BusinessException("文件类型描述过长");
        }
        return normalizedContentType;
    }

    private String normalizeFileHash(String fileHash) {
        if (!StringUtils.hasText(fileHash)) {
            throw new BusinessException("fileHash 非法");
        }
        String normalizedFileHash = fileHash.trim();
        if (normalizedFileHash.length() > MAX_FILE_HASH_LENGTH) {
            throw new BusinessException("fileHash 非法");
        }
        return normalizedFileHash;
    }

    private long requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new BusinessException(message);
        }
        return value;
    }

    private int requirePositive(Integer value, String message) {
        if (value == null || value <= 0) {
            throw new BusinessException(message);
        }
        return value;
    }

    private DocumentUploadSessionEntity requireOwnedActiveSession(HttpServletRequest request, String uploadId) {
        if (!StringUtils.hasText(uploadId)) {
            throw new BusinessException("uploadId 非法");
        }
        DocumentUploadSessionEntity session = documentUploadSessionMapper.selectByUploadId(uploadId.trim());
        if (session == null) {
            throw new BusinessException("上传会话不存在");
        }
        CurrentUserService.CurrentUser currentUser = groupMembershipService.requireGroupOwner(request, session.getGroupId());
        if (!currentUser.userId().equals(session.getUploaderUserId())) {
            throw new BusinessException("上传会话不属于当前用户");
        }
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("上传会话已过期");
        }
        if (UPLOAD_STATUS_COMPLETED.equals(session.getStatus())) {
            throw new BusinessException("上传会话已完成");
        }
        return session;
    }

    private MultipartFile requireChunk(UploadChunkRequest uploadRequest, DocumentUploadSessionEntity session) {
        if (uploadRequest == null) {
            throw new BusinessException("分片上传请求不能为空");
        }
        if (uploadRequest.chunkIndex() == null
                || uploadRequest.chunkIndex() < 0
                || uploadRequest.chunkIndex() >= session.getChunkCount()) {
            throw new BusinessException("chunkIndex 非法");
        }
        MultipartFile chunk = uploadRequest.chunk();
        if (chunk == null || chunk.isEmpty()) {
            throw new BusinessException("上传分片不能为空");
        }
        if (chunk.getSize() > session.getChunkSize()) {
            throw new BusinessException("上传分片超过大小限制");
        }
        return chunk;
    }

    private void ensureAllChunksPresent(DocumentUploadSessionEntity session, List<DocumentUploadChunkEntity> chunks) {
        if (chunks.size() != session.getChunkCount()) {
            throw new BusinessException("缺少分片，无法完成上传");
        }
        for (int index = 0; index < session.getChunkCount(); index++) {
            if (!Integer.valueOf(index).equals(chunks.get(index).getChunkIndex())) {
                throw new BusinessException("缺少分片，无法完成上传");
            }
        }
    }

    private String buildChunkObjectKey(Long groupId, String uploadId, Integer chunkIndex) {
        return "uploads/%d/%s/chunks/%d".formatted(groupId, uploadId, chunkIndex);
    }

    private String buildFinalObjectKey(DocumentUploadSessionEntity session) {
        String fileId = UUID.randomUUID().toString().replace("-", "");
        return "groups/%d/users/%d/%s.%s".formatted(
                session.getGroupId(),
                session.getUploaderUserId(),
                fileId,
                session.getFileExt()
        );
    }

    private DocumentUploadSessionEntity buildUploadSession(Long groupId, Long userId, NormalizedInitRequest uploadRequest) {
        LocalDateTime now = LocalDateTime.now();
        DocumentUploadSessionEntity session = new DocumentUploadSessionEntity();
        session.setUploadId(UUID.randomUUID().toString().replace("-", ""));
        session.setGroupId(groupId);
        session.setUploaderUserId(userId);
        session.setFileName(uploadRequest.fileName());
        session.setFileExt(uploadRequest.fileExt());
        session.setContentType(uploadRequest.contentType());
        session.setFileSize(uploadRequest.fileSize());
        session.setFileHash(uploadRequest.fileHash());
        session.setChunkSize(uploadRequest.chunkSize());
        session.setChunkCount(uploadRequest.chunkCount());
        session.setStatus(UPLOAD_STATUS_INIT);
        session.setStorageBucket(objectStorageService.getDefaultBucket());
        session.setMergedObjectKey(null);
        session.setExpiresAt(now.plusHours(SESSION_EXPIRE_HOURS));
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        return session;
    }

    private record NormalizedInitRequest(
            Long groupId,
            String fileName,
            String fileExt,
            Long fileSize,
            String contentType,
            String fileHash,
            Long chunkSize,
            Integer chunkCount
    ) {
    }
}
