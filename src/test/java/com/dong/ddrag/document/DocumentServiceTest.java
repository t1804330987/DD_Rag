package com.dong.ddrag.document;

import com.dong.ddrag.document.mapper.DocumentMapper;
import com.dong.ddrag.document.model.dto.UploadDocumentRequest;
import com.dong.ddrag.document.model.entity.DocumentEntity;
import com.dong.ddrag.document.service.DocumentIngestionRequestedEvent;
import com.dong.ddrag.document.service.DocumentService;
import com.dong.ddrag.groupmembership.service.GroupMembershipService;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.ingestion.mapper.DocumentChunkMapper;
import com.dong.ddrag.ingestion.service.DocumentIngestionProcessor;
import com.dong.ddrag.ingestion.vector.VectorIngestionService;
import com.dong.ddrag.retrieval.elasticsearch.ElasticsearchChunkIndexService;
import com.dong.ddrag.storage.service.ObjectStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private GroupMembershipService groupMembershipService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private DocumentIngestionProcessor documentIngestionProcessor;

    @Mock
    private DocumentChunkMapper documentChunkMapper;

    @Mock
    private VectorIngestionService vectorIngestionService;

    @Mock
    private ElasticsearchChunkIndexService elasticsearchChunkIndexService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Test
    void shouldDeleteUploadedObjectWhenMetadataInsertFails() {
        DocumentService documentService = createDocumentService();
        UploadDocumentRequest uploadRequest = buildUploadRequest();
        HttpServletRequest request = new MockHttpServletRequest();
        CurrentUserService.CurrentUser currentUser =
                new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户甲");

        given(groupMembershipService.requireGroupReadable(any(HttpServletRequest.class), eq(2001L)))
                .willReturn(currentUser);
        given(groupMembershipService.requireGroupOwner(any(HttpServletRequest.class), eq(2001L)))
                .willReturn(currentUser);
        given(objectStorageService.getDefaultBucket()).willReturn("test-bucket");
        willThrow(new DuplicateKeyException("元数据入库失败"))
                .given(documentMapper).insert(any(DocumentEntity.class));

        assertThatThrownBy(() -> documentService.uploadDocument(request, uploadRequest))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("元数据入库失败");

        ArgumentCaptor<String> objectKeyCaptor = ArgumentCaptor.forClass(String.class);
        then(objectStorageService).should().putObject(
                eq("test-bucket"),
                objectKeyCaptor.capture(),
                any(InputStream.class),
                anyLong(),
                eq("text/plain")
        );
        then(objectStorageService).should().deleteObject("test-bucket", objectKeyCaptor.getValue());
    }

    @Test
    void shouldKeepOriginalInsertFailureWhenCompensationDeleteFails() {
        DocumentService documentService = createDocumentService();
        UploadDocumentRequest uploadRequest = buildUploadRequest();
        HttpServletRequest request = new MockHttpServletRequest();
        CurrentUserService.CurrentUser currentUser =
                new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户甲");

        given(groupMembershipService.requireGroupReadable(any(HttpServletRequest.class), eq(2001L)))
                .willReturn(currentUser);
        given(groupMembershipService.requireGroupOwner(any(HttpServletRequest.class), eq(2001L)))
                .willReturn(currentUser);
        given(objectStorageService.getDefaultBucket()).willReturn("test-bucket");
        willThrow(new DuplicateKeyException("元数据入库失败"))
                .given(documentMapper).insert(any(DocumentEntity.class));
        willThrow(new IllegalStateException("删除补偿失败"))
                .given(objectStorageService).deleteObject(anyString(), anyString());

        Throwable thrown = catchThrowable(() -> documentService.uploadDocument(request, uploadRequest));

        assertThat(thrown)
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("元数据入库失败");
        assertThat(thrown.getSuppressed()).hasSize(1);
        assertThat(thrown.getSuppressed()[0]).hasMessageContaining("删除补偿失败");
    }

    @Test
    void shouldPersistUploadedDocumentAndPublishAsyncIngestionEvent() {
        DocumentService documentService = createDocumentService();
        UploadDocumentRequest uploadRequest = buildUploadRequest();
        HttpServletRequest request = new MockHttpServletRequest();
        CurrentUserService.CurrentUser currentUser =
                new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户甲");

        given(groupMembershipService.requireGroupReadable(any(HttpServletRequest.class), eq(2001L)))
                .willReturn(currentUser);
        given(groupMembershipService.requireGroupOwner(any(HttpServletRequest.class), eq(2001L)))
                .willReturn(currentUser);
        given(objectStorageService.getDefaultBucket()).willReturn("test-bucket");
        doAnswer(invocation -> {
            DocumentEntity document = invocation.getArgument(0);
            document.setId(3001L);
            return 1;
        }).when(documentMapper).insert(any(DocumentEntity.class));

        Long documentId = documentService.uploadDocument(request, uploadRequest);

        assertThat(documentId).isEqualTo(3001L);
        ArgumentCaptor<DocumentEntity> documentCaptor = ArgumentCaptor.forClass(DocumentEntity.class);
        then(documentMapper).should().insert(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getStatus()).isEqualTo("PROCESSING");
        ArgumentCaptor<DocumentIngestionRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(DocumentIngestionRequestedEvent.class);
        then(applicationEventPublisher).should().publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().documentId()).isEqualTo(3001L);
        assertThat(eventCaptor.getValue().groupId()).isEqualTo(2001L);
        then(documentIngestionProcessor).should(never()).process(anyLong(), anyLong());
        then(documentMapper).should(never()).updateStatus(anyLong(), anyLong(), eq("READY"), any(), any());
        then(elasticsearchChunkIndexService).should(never()).indexReadyChunks(anyString(), any());
    }

    @Test
    void shouldDeleteUploadedObjectWhenAsyncIngestionEventPublishFails() {
        DocumentService documentService = createDocumentService();
        UploadDocumentRequest uploadRequest = buildUploadRequest();
        HttpServletRequest request = new MockHttpServletRequest();
        CurrentUserService.CurrentUser currentUser =
                new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户甲");

        given(groupMembershipService.requireGroupReadable(any(HttpServletRequest.class), eq(2001L)))
                .willReturn(currentUser);
        given(groupMembershipService.requireGroupOwner(any(HttpServletRequest.class), eq(2001L)))
                .willReturn(currentUser);
        given(objectStorageService.getDefaultBucket()).willReturn("test-bucket");
        doAnswer(invocation -> {
            DocumentEntity document = invocation.getArgument(0);
            document.setId(3001L);
            return 1;
        }).when(documentMapper).insert(any(DocumentEntity.class));
        willThrow(new IllegalStateException("事件发布失败"))
                .given(applicationEventPublisher).publishEvent(any(DocumentIngestionRequestedEvent.class));

        assertThatThrownBy(() -> documentService.uploadDocument(request, uploadRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("事件发布失败");

        ArgumentCaptor<String> objectKeyCaptor = ArgumentCaptor.forClass(String.class);
        then(objectStorageService).should().putObject(
                eq("test-bucket"),
                objectKeyCaptor.capture(),
                any(InputStream.class),
                anyLong(),
                eq("text/plain")
        );
        then(objectStorageService).should().deleteObject("test-bucket", objectKeyCaptor.getValue());
    }

    @Test
    void shouldRetryFailedDocumentByResettingStatusAndPublishingAsyncEvent() {
        DocumentService documentService = createDocumentService();
        HttpServletRequest request = new MockHttpServletRequest();
        CurrentUserService.CurrentUser currentUser =
                new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户甲");
        DocumentEntity document = new DocumentEntity();
        document.setId(3001L);
        document.setGroupId(2001L);
        document.setStatus("FAILED");

        given(groupMembershipService.requireGroupReadable(any(HttpServletRequest.class), eq(2001L)))
                .willReturn(currentUser);
        given(groupMembershipService.requireGroupOwner(any(HttpServletRequest.class), eq(2001L)))
                .willReturn(currentUser);
        given(documentMapper.selectByIdAndGroupId(3001L, 2001L)).willReturn(document);
        given(documentMapper.updateStatus(eq(3001L), eq(2001L), eq("PROCESSING"), eq(null), eq(null)))
                .willReturn(1);

        documentService.retryFailedDocumentIngestion(request, 2001L, 3001L);

        then(documentMapper).should().updateStatus(3001L, 2001L, "PROCESSING", null, null);
        ArgumentCaptor<DocumentIngestionRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(DocumentIngestionRequestedEvent.class);
        then(applicationEventPublisher).should().publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().documentId()).isEqualTo(3001L);
        assertThat(eventCaptor.getValue().groupId()).isEqualTo(2001L);
    }

    @Test
    void shouldCreateInstantUploadedDocumentByReusingExistingObjectMetadata() {
        DocumentService documentService = createDocumentService();
        DocumentEntity existingDocument = new DocumentEntity();
        existingDocument.setId(3001L);
        existingDocument.setGroupId(2001L);
        existingDocument.setUploaderUserId(1002L);
        existingDocument.setFileName("历史需求.txt");
        existingDocument.setFileExt("txt");
        existingDocument.setContentType("text/plain");
        existingDocument.setFileSize(2048L);
        existingDocument.setFileHash("hash-abc");
        existingDocument.setStorageBucket("test-bucket");
        existingDocument.setStorageObjectKey("groups/2001/users/1002/existing.txt");
        existingDocument.setStatus("READY");
        doAnswer(invocation -> {
            DocumentEntity document = invocation.getArgument(0);
            document.setId(4001L);
            return 1;
        }).when(documentMapper).insert(any(DocumentEntity.class));

        Long documentId = documentService.createInstantUploadedDocument(2001L, 1001L, existingDocument, "本次上传文件.txt");

        assertThat(documentId).isEqualTo(4001L);
        ArgumentCaptor<DocumentEntity> documentCaptor = ArgumentCaptor.forClass(DocumentEntity.class);
        then(documentMapper).should().insert(documentCaptor.capture());
        DocumentEntity insertedDocument = documentCaptor.getValue();
        assertThat(insertedDocument.getGroupId()).isEqualTo(2001L);
        assertThat(insertedDocument.getUploaderUserId()).isEqualTo(1001L);
        assertThat(insertedDocument.getFileName()).isEqualTo("本次上传文件.txt");
        assertThat(insertedDocument.getFileExt()).isEqualTo("txt");
        assertThat(insertedDocument.getContentType()).isEqualTo("text/plain");
        assertThat(insertedDocument.getFileSize()).isEqualTo(2048L);
        assertThat(insertedDocument.getFileHash()).isEqualTo("hash-abc");
        assertThat(insertedDocument.getStorageBucket()).isEqualTo("test-bucket");
        assertThat(insertedDocument.getStorageObjectKey()).isEqualTo("groups/2001/users/1002/existing.txt");
        assertThat(insertedDocument.getStatus()).isEqualTo("PROCESSING");
        then(objectStorageService).should(never()).putObject(anyString(), anyString(), any(InputStream.class), anyLong(), anyString());
        then(applicationEventPublisher).should().publishEvent(any(DocumentIngestionRequestedEvent.class));
        then(documentIngestionProcessor).should(never()).process(anyLong(), anyLong());
    }

    @Test
    void shouldSanitizePathStyleFileNameWhenCreatingInstantUploadedDocument() {
        DocumentService documentService = createDocumentService();
        DocumentEntity existingDocument = new DocumentEntity();
        existingDocument.setId(3001L);
        existingDocument.setFileExt("txt");
        existingDocument.setContentType("text/plain");
        existingDocument.setFileSize(2048L);
        existingDocument.setFileHash("hash-abc");
        existingDocument.setStorageBucket("test-bucket");
        existingDocument.setStorageObjectKey("groups/2001/users/1002/existing.txt");
        doAnswer(invocation -> {
            DocumentEntity document = invocation.getArgument(0);
            document.setId(4001L);
            return 1;
        }).when(documentMapper).insert(any(DocumentEntity.class));

        documentService.createInstantUploadedDocument(2001L, 1001L, existingDocument, "dir/subdir/本次上传文件.txt");

        ArgumentCaptor<DocumentEntity> documentCaptor = ArgumentCaptor.forClass(DocumentEntity.class);
        then(documentMapper).should().insert(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getFileName()).isEqualTo("本次上传文件.txt");
    }

    private DocumentService createDocumentService() {
        return new DocumentService(
                documentMapper,
                groupMembershipService,
                currentUserService,
                objectStorageService,
                vectorIngestionService,
                elasticsearchChunkIndexService,
                applicationEventPublisher
        );
    }

    private UploadDocumentRequest buildUploadRequest() {
        UploadDocumentRequest uploadRequest = new UploadDocumentRequest();
        uploadRequest.setGroupId(2001L);
        uploadRequest.setFile(new MockMultipartFile(
                "file",
                "需求说明.txt",
                "text/plain",
                "hello document".getBytes()
        ));
        return uploadRequest;
    }
}
