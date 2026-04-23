package com.dong.ddrag.document;

import com.dong.ddrag.document.mapper.DocumentMapper;
import com.dong.ddrag.document.model.dto.UploadDocumentRequest;
import com.dong.ddrag.document.model.entity.DocumentEntity;
import com.dong.ddrag.document.service.DocumentService;
import com.dong.ddrag.groupmembership.service.GroupMembershipService;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.ingestion.mapper.DocumentChunkMapper;
import com.dong.ddrag.ingestion.service.DocumentIngestionProcessor;
import com.dong.ddrag.ingestion.vector.VectorIngestionService;
import com.dong.ddrag.retrieval.elasticsearch.ElasticsearchChunkIndexService;
import com.dong.ddrag.storage.service.ObjectStorageService;
import jakarta.servlet.http.HttpServletRequest;
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
    void shouldProcessUploadedDocumentSynchronouslyAndMarkReady() {
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
        given(documentChunkMapper.selectByDocumentId(3001L)).willReturn(java.util.List.of());
        given(documentMapper.updateStatus(eq(3001L), eq(2001L), eq("READY"), eq(null), any(LocalDateTime.class)))
                .willReturn(1);
        doAnswer(invocation -> {
            DocumentEntity document = invocation.getArgument(0);
            document.setId(3001L);
            return 1;
        }).when(documentMapper).insert(any(DocumentEntity.class));

        Long documentId = documentService.uploadDocument(request, uploadRequest);

        assertThat(documentId).isEqualTo(3001L);
        then(documentIngestionProcessor).should().process(3001L, 2001L);
        InOrder inOrder = inOrder(documentIngestionProcessor, documentChunkMapper, elasticsearchChunkIndexService, documentMapper);
        inOrder.verify(documentIngestionProcessor).process(3001L, 2001L);
        inOrder.verify(documentChunkMapper).selectByDocumentId(3001L);
        inOrder.verify(elasticsearchChunkIndexService).indexReadyChunks(eq("需求说明.txt"), any());
        inOrder.verify(documentMapper).updateStatus(
                eq(3001L),
                eq(2001L),
                eq("READY"),
                eq(null),
                any(LocalDateTime.class)
        );
        then(documentMapper).should().updateStatus(
                eq(3001L),
                eq(2001L),
                eq("READY"),
                eq(null),
                any(LocalDateTime.class)
        );
    }

    @Test
    void shouldDeleteUploadedObjectWhenSynchronousIngestionFails() {
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
        willThrow(new IllegalStateException("同步入库失败"))
                .given(documentIngestionProcessor).process(3001L, 2001L);

        assertThatThrownBy(() -> documentService.uploadDocument(request, uploadRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("同步入库失败");

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
    void shouldNotMarkDocumentReadyWhenElasticsearchSyncFails() {
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
        given(documentChunkMapper.selectByDocumentId(3001L)).willReturn(java.util.List.of());
        doAnswer(invocation -> {
            DocumentEntity document = invocation.getArgument(0);
            document.setId(3001L);
            return 1;
        }).when(documentMapper).insert(any(DocumentEntity.class));
        willThrow(new IllegalStateException("ES 同步失败"))
                .given(elasticsearchChunkIndexService).indexReadyChunks(eq("需求说明.txt"), any());

        assertThatThrownBy(() -> documentService.uploadDocument(request, uploadRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ES 同步失败");

        then(documentMapper).should(never()).updateStatus(
                eq(3001L),
                eq(2001L),
                eq("READY"),
                eq(null),
                any(LocalDateTime.class)
        );
        then(vectorIngestionService).should().deleteDocumentVectors(3001L);
        then(elasticsearchChunkIndexService).should().deleteDocumentChunks(3001L);
    }

    private DocumentService createDocumentService() {
        return new DocumentService(
                documentMapper,
                groupMembershipService,
                currentUserService,
                objectStorageService,
                documentIngestionProcessor,
                documentChunkMapper,
                vectorIngestionService,
                elasticsearchChunkIndexService
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
