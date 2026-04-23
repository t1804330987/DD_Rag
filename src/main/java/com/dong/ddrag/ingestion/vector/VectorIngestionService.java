package com.dong.ddrag.ingestion.vector;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.ingestion.model.entity.DocumentChunkEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class VectorIngestionService {

    private static final Logger log = LoggerFactory.getLogger(VectorIngestionService.class);
    private static final int DEFAULT_ADD_BATCH_SIZE = 9;
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() { };

    private final VectorStore vectorStore;
    private final int addBatchSize;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public VectorIngestionService(
            VectorStore vectorStore,
            @Value("${ingestion.vector.add-batch-size:${spring.ai.vectorstore.pgvector.max-document-batch-size:9}}")
            int addBatchSize
    ) {
        this.vectorStore = vectorStore;
        this.addBatchSize = normalizeBatchSize(addBatchSize);
    }

    // 保留单测和手工构造入口，生产环境走带配置的构造器。
    public VectorIngestionService(VectorStore vectorStore) {
        this(vectorStore, DEFAULT_ADD_BATCH_SIZE);
    }

    public void ingestChunks(List<DocumentChunkEntity> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        log.info("开始写入向量: chunkCount={}, addBatchSize={}", chunks.size(), addBatchSize);
        List<Document> documents = chunks.stream().map(this::toVectorDocument).toList();
        deleteExistingVectors(extractDocumentIds(chunks));
        embedAndStore(documents);
        log.info("向量写入结束: chunkCount={}", documents.size());
    }

    public void deleteDocumentVectors(Long documentId) {
        if (documentId == null || documentId <= 0) {
            return;
        }
        Filter.Expression filter = new FilterExpressionBuilder().eq("documentId", documentId).build();
        vectorStore.delete(filter);
        log.info("文档向量删除完成: documentId={}", documentId);
    }

    public void embedAndStore(List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return;
        }
        for (int i = 0; i < documents.size(); i += addBatchSize) {
            List<Document> subList = documents.subList(i, Math.min(i + addBatchSize, documents.size()));
            log.info("执行向量批次写入: batchStart={}, batchSize={}", i, subList.size());
            vectorStore.add(subList);
        }
    }

    private int normalizeBatchSize(int configuredBatchSize) {
        return configuredBatchSize > 0 ? configuredBatchSize : DEFAULT_ADD_BATCH_SIZE;
    }

    private Document toVectorDocument(DocumentChunkEntity chunk) {
        validateChunk(chunk);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("groupId", chunk.getGroupId());
        metadata.put("documentId", chunk.getDocumentId());
        metadata.put("chunkId", chunk.getId());
        metadata.put("chunkIndex", chunk.getChunkIndex());
        metadata.putAll(extractOptionalMetadata(chunk.getMetadataJson()));
        return Document.builder()
                .id(buildStableDocumentId(chunk))
                .text(chunk.getChunkText())
                .metadata(metadata)
                .build();
    }

    private void deleteExistingVectors(Set<Long> documentIds) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        for (Long documentId : documentIds) {
            Filter.Expression filter = builder.eq("documentId", documentId).build();
            vectorStore.delete(filter);
        }
    }

    private Set<Long> extractDocumentIds(List<DocumentChunkEntity> chunks) {
        Set<Long> documentIds = new LinkedHashSet<>();
        for (DocumentChunkEntity chunk : chunks) {
            validateChunk(chunk);
            documentIds.add(chunk.getDocumentId());
        }
        return documentIds;
    }

    private String buildStableDocumentId(DocumentChunkEntity chunk) {
        String rawId = chunk.getDocumentId() + ":" + chunk.getChunkIndex();
        return UUID.nameUUIDFromBytes(rawId.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void validateChunk(DocumentChunkEntity chunk) {
        if (chunk == null || chunk.getId() == null) {
            throw new BusinessException("向量写入前必须先完成 chunk 落库");
        }
        if (chunk.getDocumentId() == null || chunk.getChunkIndex() == null) {
            throw new BusinessException("向量写入前必须提供 documentId 和 chunkIndex");
        }
        if (chunk.getChunkText() == null || chunk.getChunkText().isBlank()) {
            throw new BusinessException("空切片不能写入向量库");
        }
    }

    private Map<String, Object> extractOptionalMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> sourceMetadata = objectMapper.readValue(metadataJson, MAP_TYPE_REFERENCE);
            Map<String, Object> optionalMetadata = new LinkedHashMap<>();
            String fileName = readLegacyCompatibleFileName(sourceMetadata);
            if (fileName != null) {
                optionalMetadata.put("fileName", fileName);
            }
            return optionalMetadata;
        } catch (Exception exception) {
            log.warn("解析 chunk metadataJson 失败，忽略扩展元数据。", exception);
            return Map.of();
        }
    }

    private String readLegacyCompatibleFileName(Map<String, Object> sourceMetadata) {
        Object fileName = sourceMetadata.get("fileName");
        if (fileName instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        Object documentName = sourceMetadata.get("documentName");
        if (documentName instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return null;
    }
}
