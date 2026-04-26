package com.dong.ddrag.document.service;

import com.dong.ddrag.common.enums.DocumentStatus;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.document.mapper.DocumentMapper;
import com.dong.ddrag.document.model.entity.DocumentEntity;
import com.dong.ddrag.ingestion.mapper.DocumentChunkMapper;
import com.dong.ddrag.ingestion.model.entity.DocumentChunkEntity;
import com.dong.ddrag.ingestion.service.DocumentIngestionProcessor;
import com.dong.ddrag.ingestion.vector.VectorIngestionService;
import com.dong.ddrag.retrieval.elasticsearch.ElasticsearchChunkIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DocumentIngestionAsyncService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionAsyncService.class);
    private static final int FAILURE_REASON_MAX_LENGTH = 512;

    private final DocumentMapper documentMapper;
    private final DocumentIngestionProcessor documentIngestionProcessor;
    private final DocumentChunkMapper documentChunkMapper;
    private final VectorIngestionService vectorIngestionService;
    private final ElasticsearchChunkIndexService elasticsearchChunkIndexService;

    public DocumentIngestionAsyncService(
            DocumentMapper documentMapper,
            DocumentIngestionProcessor documentIngestionProcessor,
            DocumentChunkMapper documentChunkMapper,
            VectorIngestionService vectorIngestionService,
            ElasticsearchChunkIndexService elasticsearchChunkIndexService
    ) {
        this.documentMapper = documentMapper;
        this.documentIngestionProcessor = documentIngestionProcessor;
        this.documentChunkMapper = documentChunkMapper;
        this.vectorIngestionService = vectorIngestionService;
        this.elasticsearchChunkIndexService = elasticsearchChunkIndexService;
    }

    @Retryable(
            retryFor = RuntimeException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2.0)
    )
    @Transactional
    public void ingestDocument(Long documentId, Long groupId) {
        DocumentEntity document = requireDocument(documentId, groupId);
        log.info("开始异步执行文档ETL: documentId={}, groupId={}", documentId, groupId);
        cleanupProcessingArtifacts(documentId);
        documentIngestionProcessor.process(documentId, groupId);
        syncSearchIndex(document);
        markDocumentStatus(documentId, groupId, DocumentStatus.READY.name(), null, LocalDateTime.now());
        log.info("异步文档ETL完成: documentId={}, groupId={}, status={}", documentId, groupId, DocumentStatus.READY.name());
    }

    @Recover
    @Transactional
    public void recover(RuntimeException exception, Long documentId, Long groupId) {
        log.error("异步文档ETL最终失败: documentId={}, groupId={}, reason={}", documentId, groupId, exception.getMessage(), exception);
        cleanupProcessingArtifacts(documentId);
        markDocumentStatus(
                documentId,
                groupId,
                DocumentStatus.FAILED.name(),
                truncateFailureReason(exception.getMessage()),
                LocalDateTime.now()
        );
    }

    private DocumentEntity requireDocument(Long documentId, Long groupId) {
        DocumentEntity document = documentMapper.selectByIdAndGroupId(documentId, groupId);
        if (document == null) {
            throw new BusinessException("待处理文档不存在");
        }
        return document;
    }

    private void cleanupProcessingArtifacts(Long documentId) {
        try {
            documentChunkMapper.deleteByDocumentId(documentId);
        } catch (RuntimeException exception) {
            log.warn("清理旧 chunk 失败: documentId={}, reason={}", documentId, exception.getMessage());
        }
        try {
            vectorIngestionService.deleteDocumentVectors(documentId);
        } catch (RuntimeException exception) {
            log.warn("清理旧向量失败: documentId={}, reason={}", documentId, exception.getMessage());
        }
        try {
            elasticsearchChunkIndexService.deleteDocumentChunks(documentId);
        } catch (RuntimeException exception) {
            log.warn("清理旧 ES 索引失败: documentId={}, reason={}", documentId, exception.getMessage());
        }
    }

    private void syncSearchIndex(DocumentEntity document) {
        List<DocumentChunkEntity> chunks = documentChunkMapper.selectByDocumentId(document.getId());
        elasticsearchChunkIndexService.indexReadyChunks(document.getFileName(), chunks);
    }

    private void markDocumentStatus(
            Long documentId,
            Long groupId,
            String status,
            String failureReason,
            LocalDateTime processedAt
    ) {
        int updated = documentMapper.updateStatus(documentId, groupId, status, failureReason, processedAt);
        if (updated == 0) {
            throw new BusinessException("文档状态更新失败");
        }
    }

    private String truncateFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return "文档处理失败";
        }
        return failureReason.length() <= FAILURE_REASON_MAX_LENGTH
                ? failureReason
                : failureReason.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
