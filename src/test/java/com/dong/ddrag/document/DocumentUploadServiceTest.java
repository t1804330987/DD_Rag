package com.dong.ddrag.document;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.document.mapper.DocumentUploadChunkMapper;
import com.dong.ddrag.document.mapper.DocumentMapper;
import com.dong.ddrag.document.mapper.DocumentUploadSessionMapper;
import com.dong.ddrag.document.model.dto.UploadInitRequest;
import com.dong.ddrag.document.model.dto.UploadChunkRequest;
import com.dong.ddrag.document.model.entity.DocumentEntity;
import com.dong.ddrag.document.model.entity.DocumentUploadChunkEntity;
import com.dong.ddrag.document.model.entity.DocumentUploadSessionEntity;
import com.dong.ddrag.document.model.vo.UploadInitResponse;
import com.dong.ddrag.document.service.DocumentService;
import com.dong.ddrag.document.service.DocumentUploadService;
import com.dong.ddrag.groupmembership.service.GroupMembershipService;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.storage.service.ObjectStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DocumentUploadServiceTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private DocumentUploadSessionMapper documentUploadSessionMapper;

    @Mock
    private DocumentUploadChunkMapper documentUploadChunkMapper;

    @Mock
    private GroupMembershipService groupMembershipService;

    @Mock
    private DocumentService documentService;

    @Mock
    private ObjectStorageService objectStorageService;

    @Test
    void shouldReturnInstantUploadWhenSameGroupHashExists() {
        DocumentUploadService uploadService = createUploadService();
        HttpServletRequest request = new MockHttpServletRequest();
        CurrentUserService.CurrentUser currentUser =
                new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户甲");
        UploadInitRequest initRequest = new UploadInitRequest(
                2001L,
                "产品需求文档.txt",
                1024L,
                "text/plain",
                "hash-abc",
                512L,
                2
        );
        DocumentEntity existingDocument = new DocumentEntity();
        existingDocument.setId(3001L);
        existingDocument.setGroupId(2001L);
        existingDocument.setUploaderUserId(1002L);
        existingDocument.setFileName("历史文件.txt");
        existingDocument.setFileExt("txt");
        existingDocument.setContentType("text/plain");
        existingDocument.setFileSize(1024L);
        existingDocument.setFileHash("hash-abc");
        existingDocument.setStorageBucket("test-bucket");
        existingDocument.setStorageObjectKey("groups/2001/existing.txt");
        existingDocument.setStatus("READY");

        given(groupMembershipService.requireGroupOwner(any(HttpServletRequest.class), eq(2001L)))
                .willReturn(currentUser);
        given(documentMapper.selectByGroupIdAndFileHash(2001L, "hash-abc"))
                .willReturn(existingDocument);
        given(documentService.createInstantUploadedDocument(2001L, 1001L, existingDocument, "产品需求文档.txt"))
                .willReturn(4001L);

        UploadInitResponse response = uploadService.initUpload(request, initRequest);

        assertThat(response.instantUpload()).isTrue();
        assertThat(response.documentId()).isEqualTo(4001L);
        assertThat(response.uploadId()).isNull();
        assertThat(response.uploadedChunks()).isEmpty();
        then(documentService).should().createInstantUploadedDocument(2001L, 1001L, existingDocument, "产品需求文档.txt");
        then(documentUploadSessionMapper).should(never()).insert(any(DocumentUploadSessionEntity.class));
    }

    @Test
    void shouldNotInstantUploadWhenMatchedDocumentIsNotReady() {
        DocumentUploadService uploadService = createUploadService();
        HttpServletRequest request = new MockHttpServletRequest();
        CurrentUserService.CurrentUser currentUser =
                new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户甲");
        UploadInitRequest initRequest = new UploadInitRequest(
                2001L,
                "产品需求文档.txt",
                1024L,
                "text/plain",
                "hash-abc",
                512L,
                2
        );
        DocumentEntity existingDocument = new DocumentEntity();
        existingDocument.setId(3001L);
        existingDocument.setStatus("PROCESSING");

        given(groupMembershipService.requireGroupOwner(any(HttpServletRequest.class), eq(2001L)))
                .willReturn(currentUser);
        given(documentMapper.selectByGroupIdAndFileHash(2001L, "hash-abc"))
                .willReturn(existingDocument);

        UploadInitResponse response = uploadService.initUpload(request, initRequest);

        assertThat(response.instantUpload()).isFalse();
        assertThat(response.uploadId()).isNotBlank();
        then(documentService).should(never())
                .createInstantUploadedDocument(any(), any(), any(DocumentEntity.class), any());
    }

    @Test
    void shouldNormalizeFileHashAndFileNameForInstantUpload() {
        DocumentUploadService uploadService = createUploadService();
        HttpServletRequest request = new MockHttpServletRequest();
        CurrentUserService.CurrentUser currentUser =
                new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户甲");
        UploadInitRequest initRequest = new UploadInitRequest(
                2001L,
                "nested/path/产品需求文档.txt",
                1024L,
                " text/plain ",
                "  hash-abc  ",
                512L,
                2
        );
        DocumentEntity existingDocument = new DocumentEntity();
        existingDocument.setId(3001L);
        existingDocument.setStatus("READY");

        given(groupMembershipService.requireGroupOwner(any(HttpServletRequest.class), eq(2001L)))
                .willReturn(currentUser);
        given(documentMapper.selectByGroupIdAndFileHash(2001L, "hash-abc"))
                .willReturn(existingDocument);
        given(documentService.createInstantUploadedDocument(2001L, 1001L, existingDocument, "产品需求文档.txt"))
                .willReturn(4001L);

        UploadInitResponse response = uploadService.initUpload(request, initRequest);

        assertThat(response.instantUpload()).isTrue();
        then(documentMapper).should().selectByGroupIdAndFileHash(2001L, "hash-abc");
        then(documentService).should().createInstantUploadedDocument(2001L, 1001L, existingDocument, "产品需求文档.txt");
    }

    @Test
    void shouldCreateUploadSessionWhenNoInstantUploadMatch() {
        DocumentUploadService uploadService = createUploadService();
        HttpServletRequest request = new MockHttpServletRequest();
        CurrentUserService.CurrentUser currentUser =
                new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户甲");
        UploadInitRequest initRequest = new UploadInitRequest(
                2001L,
                "dir/产品需求文档.txt",
                2048L,
                " text/plain ",
                "  hash-def  ",
                1024L,
                2
        );

        given(groupMembershipService.requireGroupOwner(any(HttpServletRequest.class), eq(2001L)))
                .willReturn(currentUser);
        given(objectStorageService.getDefaultBucket()).willReturn("test-bucket");
        given(documentMapper.selectByGroupIdAndFileHash(2001L, "hash-def"))
                .willReturn(null);

        UploadInitResponse response = uploadService.initUpload(request, initRequest);

        assertThat(response.instantUpload()).isFalse();
        assertThat(response.documentId()).isNull();
        assertThat(response.uploadId()).isNotBlank();
        assertThat(response.chunkCount()).isEqualTo(2);
        assertThat(response.uploadedChunks()).isEqualTo(List.of());

        ArgumentCaptor<DocumentUploadSessionEntity> sessionCaptor =
                ArgumentCaptor.forClass(DocumentUploadSessionEntity.class);
        then(documentUploadSessionMapper).should().insert(sessionCaptor.capture());
        DocumentUploadSessionEntity savedSession = sessionCaptor.getValue();
        assertThat(savedSession.getUploadId()).isEqualTo(response.uploadId());
        assertThat(savedSession.getGroupId()).isEqualTo(2001L);
        assertThat(savedSession.getUploaderUserId()).isEqualTo(1001L);
        assertThat(savedSession.getFileName()).isEqualTo("产品需求文档.txt");
        assertThat(savedSession.getFileExt()).isEqualTo("txt");
        assertThat(savedSession.getContentType()).isEqualTo("text/plain");
        assertThat(savedSession.getFileSize()).isEqualTo(2048L);
        assertThat(savedSession.getFileHash()).isEqualTo("hash-def");
        assertThat(savedSession.getChunkSize()).isEqualTo(1024L);
        assertThat(savedSession.getChunkCount()).isEqualTo(2);
        assertThat(savedSession.getStatus()).isEqualTo("INIT");
        assertThat(savedSession.getStorageBucket()).isEqualTo("test-bucket");
        then(documentMapper).should().selectByGroupIdAndFileHash(2001L, "hash-def");
        then(documentService).should(never())
                .createInstantUploadedDocument(any(), any(), any(DocumentEntity.class), any());
    }

    @Test
    void shouldUploadChunkAndRecordProgress() {
        DocumentUploadService uploadService = createUploadService();
        HttpServletRequest request = new MockHttpServletRequest();
        MockMultipartFile chunkFile = new MockMultipartFile(
                "chunk",
                "chunk-0.part",
                "application/octet-stream",
                "chunk-body".getBytes()
        );
        UploadChunkRequest uploadChunkRequest = new UploadChunkRequest("upload-001", 0, "chunk-hash-0", chunkFile);
        DocumentUploadSessionEntity session = buildSession("upload-001", "INIT", 2);

        given(groupMembershipService.requireGroupOwner(any(HttpServletRequest.class), eq(2001L)))
                .willReturn(new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户甲"));
        given(documentUploadSessionMapper.selectByUploadId("upload-001")).willReturn(session);
        given(documentUploadChunkMapper.selectByUploadId("upload-001")).willReturn(List.of(buildChunk("upload-001", 0)));

        List<Integer> uploadedChunks = uploadService.uploadChunk(request, uploadChunkRequest);

        assertThat(uploadedChunks).containsExactly(0);
        ArgumentCaptor<String> objectKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<DocumentUploadChunkEntity> chunkCaptor = ArgumentCaptor.forClass(DocumentUploadChunkEntity.class);
        then(objectStorageService).should().putObject(
                eq("test-bucket"),
                objectKeyCaptor.capture(),
                any(InputStream.class),
                eq(chunkFile.getSize()),
                eq("application/octet-stream")
        );
        then(documentUploadChunkMapper).should().upsert(chunkCaptor.capture());
        then(documentUploadSessionMapper).should().updateStatusAndMergedObjectKey(
                eq("upload-001"),
                eq("UPLOADING"),
                isNull(),
                any(LocalDateTime.class)
        );
        assertThat(objectKeyCaptor.getValue()).isEqualTo("uploads/2001/upload-001/chunks/0");
        DocumentUploadChunkEntity savedChunk = chunkCaptor.getValue();
        assertThat(savedChunk.getUploadId()).isEqualTo("upload-001");
        assertThat(savedChunk.getChunkIndex()).isEqualTo(0);
        assertThat(savedChunk.getChunkHash()).isEqualTo("chunk-hash-0");
        assertThat(savedChunk.getChunkSize()).isEqualTo(chunkFile.getSize());
        assertThat(savedChunk.getStorageBucket()).isEqualTo("test-bucket");
        assertThat(savedChunk.getStorageObjectKey()).isEqualTo("uploads/2001/upload-001/chunks/0");
    }

    @Test
    void shouldRejectCompleteWhenChunksMissing() {
        DocumentUploadService uploadService = createUploadService();
        HttpServletRequest request = new MockHttpServletRequest();
        DocumentUploadSessionEntity session = buildSession("upload-001", "UPLOADING", 2);

        given(groupMembershipService.requireGroupOwner(any(HttpServletRequest.class), eq(2001L)))
                .willReturn(new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户甲"));
        given(documentUploadSessionMapper.selectByUploadId("upload-001")).willReturn(session);
        given(documentUploadChunkMapper.selectByUploadId("upload-001")).willReturn(List.of(buildChunk("upload-001", 0)));

        assertThatThrownBy(() -> uploadService.completeUpload(request, "upload-001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少分片");

        then(objectStorageService).should(never()).composeObject(anyString(), anyString(), any(), anyString());
        then(documentService).should(never()).finalizeUploadedDocument(any(), any(), any(), any(), any(), anyLong(), any(), any(), any());
    }

    @Test
    void shouldComposeChunksAndFinalizeDocumentWhenCompleteUpload() {
        DocumentUploadService uploadService = createUploadService();
        HttpServletRequest request = new MockHttpServletRequest();
        DocumentUploadSessionEntity session = buildSession("upload-001", "UPLOADING", 2);
        DocumentUploadChunkEntity firstChunk = buildChunk("upload-001", 0);
        DocumentUploadChunkEntity secondChunk = buildChunk("upload-001", 1);

        given(groupMembershipService.requireGroupOwner(any(HttpServletRequest.class), eq(2001L)))
                .willReturn(new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户甲"));
        given(documentUploadSessionMapper.selectByUploadId("upload-001")).willReturn(session);
        given(documentUploadChunkMapper.selectByUploadId("upload-001")).willReturn(List.of(firstChunk, secondChunk));
        given(documentService.finalizeUploadedDocument(
                eq(2001L),
                eq(1001L),
                eq("产品需求文档.txt"),
                eq("txt"),
                eq("text/plain"),
                eq(18L),
                eq("file-hash-001"),
                eq("test-bucket"),
                anyString()
        )).willReturn(4001L);

        Long documentId = uploadService.completeUpload(request, "upload-001");

        assertThat(documentId).isEqualTo(4001L);
        InOrder inOrder = org.mockito.Mockito.inOrder(objectStorageService, documentService, documentUploadSessionMapper);
        inOrder.verify(documentUploadSessionMapper).updateStatusAndMergedObjectKey(
                eq("upload-001"),
                eq("COMPLETING"),
                isNull(),
                any(LocalDateTime.class)
        );
        ArgumentCaptor<String> finalObjectKeyCaptor = ArgumentCaptor.forClass(String.class);
        inOrder.verify(objectStorageService).composeObject(
                eq("test-bucket"),
                finalObjectKeyCaptor.capture(),
                eq(List.of(
                        "uploads/2001/upload-001/chunks/0",
                        "uploads/2001/upload-001/chunks/1"
                )),
                eq("text/plain")
        );
        String finalObjectKey = finalObjectKeyCaptor.getValue();
        assertThat(finalObjectKey).startsWith("groups/2001/users/1001/").endsWith(".txt");
        inOrder.verify(documentService).finalizeUploadedDocument(
                2001L,
                1001L,
                "产品需求文档.txt",
                "txt",
                "text/plain",
                18L,
                "file-hash-001",
                "test-bucket",
                finalObjectKey
        );
        inOrder.verify(documentUploadSessionMapper).updateStatusAndMergedObjectKey(
                eq("upload-001"),
                eq("COMPLETED"),
                eq(finalObjectKey),
                any(LocalDateTime.class)
        );
    }

    private DocumentUploadSessionEntity buildSession(String uploadId, String status, int chunkCount) {
        DocumentUploadSessionEntity session = new DocumentUploadSessionEntity();
        session.setUploadId(uploadId);
        session.setGroupId(2001L);
        session.setUploaderUserId(1001L);
        session.setFileName("产品需求文档.txt");
        session.setFileExt("txt");
        session.setContentType("text/plain");
        session.setFileSize(18L);
        session.setFileHash("file-hash-001");
        session.setChunkSize(10L);
        session.setChunkCount(chunkCount);
        session.setStatus(status);
        session.setStorageBucket("test-bucket");
        session.setExpiresAt(LocalDateTime.now().plusHours(1));
        return session;
    }

    private DocumentUploadChunkEntity buildChunk(String uploadId, int chunkIndex) {
        DocumentUploadChunkEntity chunk = new DocumentUploadChunkEntity();
        chunk.setUploadId(uploadId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setChunkSize(chunkIndex == 0 ? 10L : 8L);
        chunk.setChunkHash("chunk-hash-" + chunkIndex);
        chunk.setStorageBucket("test-bucket");
        chunk.setStorageObjectKey("uploads/2001/%s/chunks/%d".formatted(uploadId, chunkIndex));
        return chunk;
    }

    private DocumentUploadService createUploadService() {
        return new DocumentUploadService(
                documentMapper,
                documentUploadSessionMapper,
                documentUploadChunkMapper,
                groupMembershipService,
                documentService,
                objectStorageService
        );
    }
}
