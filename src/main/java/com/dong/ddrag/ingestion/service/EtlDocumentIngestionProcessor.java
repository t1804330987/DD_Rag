package com.dong.ddrag.ingestion.service;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.document.mapper.DocumentMapper;
import com.dong.ddrag.document.model.entity.DocumentEntity;
import com.dong.ddrag.ingestion.chunk.ChunkService;
import com.dong.ddrag.ingestion.model.entity.DocumentChunkEntity;
import com.dong.ddrag.ingestion.parser.factory.DocumentParserFactory;
import com.dong.ddrag.ingestion.reader.StoredObjectDocumentReader;
import com.dong.ddrag.ingestion.transformer.StructureAwareChunkTransformer;
import com.dong.ddrag.ingestion.transformer.TextCleanupTransformer;
import com.dong.ddrag.ingestion.vector.VectorIngestionService;
import com.dong.ddrag.storage.service.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;

import java.util.List;

public class EtlDocumentIngestionProcessor implements DocumentIngestionProcessor {

    private static final Logger log = LoggerFactory.getLogger(EtlDocumentIngestionProcessor.class);
    private static final String DOCUMENT_NOT_FOUND_MESSAGE = "待入库文档不存在";
    private static final int PREVIEW_MAX_LENGTH = 200;

    private final DocumentMapper documentMapper;
    private final ObjectStorageService storageService;
    private final DocumentParserFactory parserFactory;
    private final TextCleanupTransformer textCleanupTransformer;
    private final StructureAwareChunkTransformer chunkTransformer;
    private final ChunkService chunkService;
    private final VectorIngestionService vectorService;

    public EtlDocumentIngestionProcessor(
            DocumentMapper documentMapper,
            ObjectStorageService storageService,
            DocumentParserFactory parserFactory,
            TextCleanupTransformer textCleanupTransformer,
            StructureAwareChunkTransformer chunkTransformer,
            ChunkService chunkService,
            VectorIngestionService vectorService
    ) {
        this.documentMapper = documentMapper;
        this.storageService = storageService;
        this.parserFactory = parserFactory;
        this.textCleanupTransformer = textCleanupTransformer;
        this.chunkTransformer = chunkTransformer;
        this.chunkService = chunkService;
        this.vectorService = vectorService;
    }

    @Override
    public void process(Long documentId, Long groupId) {
        log.info("开始执行文档ETL: documentId={}, groupId={}", documentId, groupId);
        DocumentEntity documentEntity = findDocument(documentId, groupId);
        StoredObjectDocumentReader reader =
                new StoredObjectDocumentReader(storageService, parserFactory, documentEntity);
        List<Document> rawDocuments = reader.get();
        log.info("文档读取完成: documentId={}, groupId={}, rawDocuments={}",
                documentId, groupId, rawDocuments.size());
        List<Document> cleanedDocuments = textCleanupTransformer.apply(rawDocuments);
        log.info("文本清洗完成: documentId={}, groupId={}, cleanedDocuments={}",
                documentId, groupId, cleanedDocuments.size());
        persistPreviewText(documentId, groupId, cleanedDocuments);
        List<Document> chunkDocuments = chunkTransformer.apply(cleanedDocuments);
        log.info("文档切片完成: documentId={}, groupId={}, chunkDocuments={}",
                documentId, groupId, chunkDocuments.size());
        List<DocumentChunkEntity> chunks =
                chunkService.saveChunkDocuments(documentId, groupId, chunkDocuments);
        log.info("切片落库完成: documentId={}, groupId={}, persistedChunks={}",
                documentId, groupId, chunks.size());
        vectorService.ingestChunks(chunks);
        log.info("向量写入完成: documentId={}, groupId={}, vectorChunks={}",
                documentId, groupId, chunks.size());
    }

    private DocumentEntity findDocument(Long documentId, Long groupId) {
        DocumentEntity documentEntity = documentMapper.selectByIdAndGroupId(documentId, groupId);
        if (documentEntity == null) {
            throw new BusinessException(DOCUMENT_NOT_FOUND_MESSAGE);
        }
        return documentEntity;
    }

    private void persistPreviewText(Long documentId, Long groupId, List<Document> cleanedDocuments) {
        String previewText = cleanedDocuments.stream()
                .map(Document::getText)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .map(String::trim)
                .map(this::truncatePreviewText)
                .orElse(null);
        int updated = documentMapper.updatePreviewText(documentId, groupId, previewText);
        if (updated == 0) {
            throw new BusinessException("文档预览写入失败");
        }
    }

    private String truncatePreviewText(String previewText) {
        if (previewText.length() <= PREVIEW_MAX_LENGTH) {
            return previewText;
        }
        return previewText.substring(0, PREVIEW_MAX_LENGTH);
    }
}
