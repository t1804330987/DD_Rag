package com.dong.ddrag.harness.document;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.document.mapper.DocumentMapper;
import com.dong.ddrag.document.mapper.DocumentUploadChunkMapper;
import com.dong.ddrag.document.mapper.DocumentUploadSessionMapper;
import com.dong.ddrag.document.model.dto.UploadChunkRequest;
import com.dong.ddrag.document.model.entity.DocumentEntity;
import com.dong.ddrag.document.model.entity.DocumentUploadChunkEntity;
import com.dong.ddrag.document.model.entity.DocumentUploadSessionEntity;
import com.dong.ddrag.document.service.DocumentIngestionAsyncService;
import com.dong.ddrag.document.service.DocumentService;
import com.dong.ddrag.document.service.DocumentUploadService;
import com.dong.ddrag.groupmembership.service.GroupMembershipService;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.ingestion.mapper.DocumentChunkMapper;
import com.dong.ddrag.ingestion.model.entity.DocumentChunkEntity;
import com.dong.ddrag.ingestion.service.DocumentIngestionProcessor;
import com.dong.ddrag.ingestion.vector.VectorIngestionService;
import com.dong.ddrag.retrieval.elasticsearch.ElasticsearchChunkIndexService;
import com.dong.ddrag.storage.service.ObjectStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class DocumentLifecycleHarnessTest {

    private static final String UPLOAD_ID = "upload-001";
    private static final Long GROUP_ID = 2001L;
    private static final Long USER_ID = 1001L;

    @Test
    void completeUploadComposesChunksFinalizesDocumentAndMarksSessionCompleted() {
        HarnessRuntime runtime = createRuntime();
        MockHttpServletRequest request = new MockHttpServletRequest();
        DocumentUploadSessionEntity session = uploadSession("UPLOADING", 2);
        DocumentUploadChunkEntity firstChunk = chunk(0);
        DocumentUploadChunkEntity secondChunk = chunk(1);
        given(runtime.groupMembershipService().requireGroupOwner(any(HttpServletRequest.class), eq(GROUP_ID)))
                .willReturn(new CurrentUserService.CurrentUser(USER_ID, "u1001", "测试用户"));
        given(runtime.sessionMapper().selectByUploadId(UPLOAD_ID)).willReturn(session);
        given(runtime.chunkMapper().selectByUploadId(UPLOAD_ID)).willReturn(List.of(firstChunk, secondChunk));
        given(runtime.documentService().finalizeUploadedDocument(
                eq(GROUP_ID),
                eq(USER_ID),
                eq("需求说明.txt"),
                eq("txt"),
                eq("text/plain"),
                eq(18L),
                eq("file-hash-001"),
                eq("test-bucket"),
                anyString()
        )).willReturn(3001L);

        Long documentId = runtime.uploadService().completeUpload(request, UPLOAD_ID);

        assertThat(documentId).isEqualTo(3001L);
        InOrder inOrder = inOrder(runtime.sessionMapper(), runtime.objectStorageService(), runtime.documentService());
        inOrder.verify(runtime.sessionMapper()).updateStatusAndMergedObjectKey(
                eq(UPLOAD_ID),
                eq("COMPLETING"),
                isNull(),
                any(LocalDateTime.class)
        );
        ArgumentCaptor<String> finalObjectKeyCaptor = ArgumentCaptor.forClass(String.class);
        inOrder.verify(runtime.objectStorageService()).composeObject(
                eq("test-bucket"),
                finalObjectKeyCaptor.capture(),
                eq(List.of("uploads/2001/upload-001/chunks/0", "uploads/2001/upload-001/chunks/1")),
                eq("text/plain")
        );
        String finalObjectKey = finalObjectKeyCaptor.getValue();
        assertThat(finalObjectKey).startsWith("groups/2001/users/1001/").endsWith(".txt");
        inOrder.verify(runtime.documentService()).finalizeUploadedDocument(
                GROUP_ID,
                USER_ID,
                "需求说明.txt",
                "txt",
                "text/plain",
                18L,
                "file-hash-001",
                "test-bucket",
                finalObjectKey
        );
        inOrder.verify(runtime.sessionMapper()).updateStatusAndMergedObjectKey(
                eq(UPLOAD_ID),
                eq("COMPLETED"),
                eq(finalObjectKey),
                any(LocalDateTime.class)
        );
    }

    @Test
    void missingChunksRejectCompletionAndProduceNoStorageOrDocumentSideEffects() {
        HarnessRuntime runtime = createRuntime();
        MockHttpServletRequest request = new MockHttpServletRequest();
        given(runtime.groupMembershipService().requireGroupOwner(any(HttpServletRequest.class), eq(GROUP_ID)))
                .willReturn(new CurrentUserService.CurrentUser(USER_ID, "u1001", "测试用户"));
        given(runtime.sessionMapper().selectByUploadId(UPLOAD_ID)).willReturn(uploadSession("UPLOADING", 2));
        given(runtime.chunkMapper().selectByUploadId(UPLOAD_ID)).willReturn(List.of(chunk(0)));

        assertThatThrownBy(() -> runtime.uploadService().completeUpload(request, UPLOAD_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少分片");

        then(runtime.objectStorageService()).should(never())
                .composeObject(anyString(), anyString(), any(), anyString());
        then(runtime.documentService()).should(never())
                .finalizeUploadedDocument(any(), any(), any(), any(), any(), anyLong(), any(), any(), any());
        then(runtime.sessionMapper()).should(never())
                .updateStatusAndMergedObjectKey(anyString(), eq("COMPLETING"), any(), any());
        then(runtime.sessionMapper()).should(never())
                .updateStatusAndMergedObjectKey(anyString(), eq("COMPLETED"), any(), any());
    }

    @Test
    void chunkUploadStoresObjectRecordsChunkAndMarksSessionUploading() throws Exception {
        HarnessRuntime runtime = createRuntime();
        MockMultipartFile chunkFile = new MockMultipartFile(
                "chunk",
                "chunk-0.part",
                "application/octet-stream",
                "chunk-body".getBytes()
        );
        UploadChunkRequest uploadChunkRequest = new UploadChunkRequest(UPLOAD_ID, 0, "chunk-hash-0", chunkFile);
        given(runtime.groupMembershipService().requireGroupOwner(any(HttpServletRequest.class), eq(GROUP_ID)))
                .willReturn(new CurrentUserService.CurrentUser(USER_ID, "u1001", "测试用户"));
        given(runtime.sessionMapper().selectByUploadId(UPLOAD_ID)).willReturn(uploadSession("INIT", 2));
        given(runtime.chunkMapper().selectByUploadId(UPLOAD_ID)).willReturn(List.of(chunk(0)));

        List<Integer> uploadedChunks = runtime.uploadService().uploadChunk(new MockHttpServletRequest(), uploadChunkRequest);

        assertThat(uploadedChunks).containsExactly(0);
        then(runtime.objectStorageService()).should().putObject(
                eq("test-bucket"),
                eq("uploads/2001/upload-001/chunks/0"),
                any(InputStream.class),
                eq(chunkFile.getSize()),
                eq("application/octet-stream")
        );
        ArgumentCaptor<DocumentUploadChunkEntity> chunkCaptor = ArgumentCaptor.forClass(DocumentUploadChunkEntity.class);
        then(runtime.chunkMapper()).should().upsert(chunkCaptor.capture());
        assertThat(chunkCaptor.getValue().getChunkHash()).isEqualTo("chunk-hash-0");
        assertThat(chunkCaptor.getValue().getStorageObjectKey()).isEqualTo("uploads/2001/upload-001/chunks/0");
        then(runtime.sessionMapper()).should().updateStatusAndMergedObjectKey(
                eq(UPLOAD_ID),
                eq("UPLOADING"),
                isNull(),
                any(LocalDateTime.class)
        );
    }

    @Test
    void ingestionDeletesStaleArtifactsProcessesDocumentIndexesChunksAndMarksReady() {
        HarnessRuntime runtime = createRuntime();
        DocumentEntity document = new DocumentEntity();
        document.setId(3001L);
        document.setGroupId(GROUP_ID);
        document.setFileName("需求说明.txt");
        DocumentChunkEntity readyChunk = new DocumentChunkEntity();
        readyChunk.setId(9001L);
        given(runtime.documentMapper().selectByIdAndGroupId(3001L, GROUP_ID)).willReturn(document);
        given(runtime.documentChunkMapper().selectByDocumentId(3001L)).willReturn(List.of(readyChunk));
        given(runtime.documentMapper().updateStatus(eq(3001L), eq(GROUP_ID), eq("READY"), eq(null), any(LocalDateTime.class)))
                .willReturn(1);

        runtime.ingestionAsyncService().ingestDocument(3001L, GROUP_ID);

        InOrder inOrder = inOrder(
                runtime.documentChunkMapper(),
                runtime.vectorIngestionService(),
                runtime.elasticsearchChunkIndexService(),
                runtime.documentIngestionProcessor(),
                runtime.documentMapper()
        );
        inOrder.verify(runtime.documentChunkMapper()).deleteByDocumentId(3001L);
        inOrder.verify(runtime.vectorIngestionService()).deleteDocumentVectors(3001L);
        inOrder.verify(runtime.elasticsearchChunkIndexService()).deleteDocumentChunks(3001L);
        inOrder.verify(runtime.documentIngestionProcessor()).process(3001L, GROUP_ID);
        inOrder.verify(runtime.documentChunkMapper()).selectByDocumentId(3001L);
        inOrder.verify(runtime.elasticsearchChunkIndexService()).indexReadyChunks("需求说明.txt", List.of(readyChunk));
        inOrder.verify(runtime.documentMapper()).updateStatus(eq(3001L), eq(GROUP_ID), eq("READY"), eq(null), any(LocalDateTime.class));
    }

    @Test
    void ingestionRecoveryDeletesPartialArtifactsAndMarksDocumentFailed() {
        HarnessRuntime runtime = createRuntime();
        given(runtime.documentMapper().updateStatus(eq(3001L), eq(GROUP_ID), eq("FAILED"), eq("向量写入失败"), any(LocalDateTime.class)))
                .willReturn(1);

        runWithIngestionLogsMuted(() ->
                runtime.ingestionAsyncService().recover(new IllegalStateException("向量写入失败"), 3001L, GROUP_ID)
        );

        then(runtime.documentChunkMapper()).should().deleteByDocumentId(3001L);
        then(runtime.vectorIngestionService()).should().deleteDocumentVectors(3001L);
        then(runtime.elasticsearchChunkIndexService()).should().deleteDocumentChunks(3001L);
        then(runtime.documentMapper()).should().updateStatus(
                eq(3001L),
                eq(GROUP_ID),
                eq("FAILED"),
                eq("向量写入失败"),
                any(LocalDateTime.class)
        );
    }

    private void runWithIngestionLogsMuted(Runnable runnable) {
        Logger logger = (Logger) LoggerFactory.getLogger(DocumentIngestionAsyncService.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.OFF);
        try {
            runnable.run();
        } finally {
            logger.setLevel(previousLevel);
        }
    }

    private HarnessRuntime createRuntime() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentUploadSessionMapper sessionMapper = mock(DocumentUploadSessionMapper.class);
        DocumentUploadChunkMapper chunkMapper = mock(DocumentUploadChunkMapper.class);
        GroupMembershipService groupMembershipService = mock(GroupMembershipService.class);
        DocumentService documentService = mock(DocumentService.class);
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        DocumentIngestionProcessor documentIngestionProcessor = mock(DocumentIngestionProcessor.class);
        DocumentChunkMapper documentChunkMapper = mock(DocumentChunkMapper.class);
        VectorIngestionService vectorIngestionService = mock(VectorIngestionService.class);
        ElasticsearchChunkIndexService elasticsearchChunkIndexService = mock(ElasticsearchChunkIndexService.class);
        DocumentUploadService uploadService = new DocumentUploadService(
                documentMapper,
                sessionMapper,
                chunkMapper,
                groupMembershipService,
                documentService,
                objectStorageService
        );
        DocumentIngestionAsyncService ingestionAsyncService = new DocumentIngestionAsyncService(
                documentMapper,
                documentIngestionProcessor,
                documentChunkMapper,
                vectorIngestionService,
                elasticsearchChunkIndexService
        );
        return new HarnessRuntime(
                uploadService,
                ingestionAsyncService,
                documentMapper,
                sessionMapper,
                chunkMapper,
                documentChunkMapper,
                groupMembershipService,
                documentService,
                objectStorageService,
                documentIngestionProcessor,
                vectorIngestionService,
                elasticsearchChunkIndexService
        );
    }

    private DocumentUploadSessionEntity uploadSession(String status, int chunkCount) {
        DocumentUploadSessionEntity session = new DocumentUploadSessionEntity();
        session.setUploadId(UPLOAD_ID);
        session.setGroupId(GROUP_ID);
        session.setUploaderUserId(USER_ID);
        session.setFileName("需求说明.txt");
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

    private DocumentUploadChunkEntity chunk(int index) {
        DocumentUploadChunkEntity chunk = new DocumentUploadChunkEntity();
        chunk.setUploadId(UPLOAD_ID);
        chunk.setChunkIndex(index);
        chunk.setChunkSize(index == 0 ? 10L : 8L);
        chunk.setChunkHash("chunk-hash-" + index);
        chunk.setStorageBucket("test-bucket");
        chunk.setStorageObjectKey("uploads/2001/upload-001/chunks/" + index);
        return chunk;
    }

    private record HarnessRuntime(
            DocumentUploadService uploadService,
            DocumentIngestionAsyncService ingestionAsyncService,
            DocumentMapper documentMapper,
            DocumentUploadSessionMapper sessionMapper,
            DocumentUploadChunkMapper chunkMapper,
            DocumentChunkMapper documentChunkMapper,
            GroupMembershipService groupMembershipService,
            DocumentService documentService,
            ObjectStorageService objectStorageService,
            DocumentIngestionProcessor documentIngestionProcessor,
            VectorIngestionService vectorIngestionService,
            ElasticsearchChunkIndexService elasticsearchChunkIndexService
    ) {
    }
}
