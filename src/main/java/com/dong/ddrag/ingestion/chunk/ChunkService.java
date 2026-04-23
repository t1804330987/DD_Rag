package com.dong.ddrag.ingestion.chunk;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.ingestion.mapper.DocumentChunkMapper;
import com.dong.ddrag.ingestion.model.entity.DocumentChunkEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChunkService {

    private static final Logger log = LoggerFactory.getLogger(ChunkService.class);
    private static final int CHUNK_SUMMARY_LENGTH = 120;
    private static final String DEFAULT_CHUNK_STRATEGY = "spring-ai-document";
    private static final String EMPTY_CHUNK_DOCUMENTS_MESSAGE = "文档切片结果为空，无法持久化";

    private final DocumentChunkMapper documentChunkMapper;
    private final ObjectMapper objectMapper;

    public ChunkService(DocumentChunkMapper documentChunkMapper, ObjectMapper objectMapper) {
        this.documentChunkMapper = documentChunkMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<DocumentChunkEntity> saveChunkDocuments(Long documentId, Long groupId, List<Document> documents) {
        validateIdentifiers(documentId, groupId);
        List<DocumentChunkEntity> chunks = buildChunkDocuments(documentId, groupId, documents);
        if (chunks.isEmpty()) {
            throw new BusinessException(EMPTY_CHUNK_DOCUMENTS_MESSAGE);
        }
        log.info("开始保存切片: documentId={}, groupId={}, chunkCount={}", documentId, groupId, chunks.size());
        documentChunkMapper.deleteByDocumentId(documentId);
        documentChunkMapper.insertBatch(chunks);
        backfillChunkIds(documentId, chunks);
        log.info("切片保存完成: documentId={}, groupId={}, chunkCount={}", documentId, groupId, chunks.size());
        return chunks;
    }

    private void backfillChunkIds(Long documentId, List<DocumentChunkEntity> chunks) {
        if (chunks.stream().allMatch(chunk -> chunk.getId() != null)) {
            return;
        }
        List<DocumentChunkEntity> persistedChunks = documentChunkMapper.selectByDocumentId(documentId);
        if (persistedChunks.size() != chunks.size()) {
            throw new BusinessException("批量保存切片后回填主键失败");
        }
        Map<Integer, Long> chunkIdByIndex = new HashMap<>();
        for (DocumentChunkEntity persistedChunk : persistedChunks) {
            if (persistedChunk.getChunkIndex() == null || persistedChunk.getId() == null) {
                throw new BusinessException("批量保存切片后返回的主键数据不完整");
            }
            chunkIdByIndex.put(persistedChunk.getChunkIndex(), persistedChunk.getId());
        }
        for (DocumentChunkEntity chunk : chunks) {
            Long chunkId = chunkIdByIndex.get(chunk.getChunkIndex());
            if (chunkId == null) {
                throw new BusinessException("批量保存切片后无法匹配 chunk 主键");
            }
            chunk.setId(chunkId);
        }
    }

    private void validateIdentifiers(Long documentId, Long groupId) {
        if (documentId == null || groupId == null) {
            throw new BusinessException("切片前必须提供 documentId 和 groupId");
        }
    }

    private List<DocumentChunkEntity> buildChunkDocuments(Long documentId, Long groupId, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new BusinessException(EMPTY_CHUNK_DOCUMENTS_MESSAGE);
        }
        List<DocumentChunkEntity> chunks = new ArrayList<>();
        int fallbackStart = 0;
        for (Document document : documents) {
            String chunkText = normalizeChunkDocumentText(document);
            if (chunkText.isBlank()) {
                continue;
            }
            ChunkRange range = resolveChunkRange(document.getMetadata(), chunkText, fallbackStart);
            chunks.add(buildChunk(documentId, groupId, chunks.size(), chunkText, range.charStart(),
                    range.charEnd(), document.getMetadata()));
            fallbackStart = range.charEnd();
        }
        return chunks;
    }

    private String normalizeChunkDocumentText(Document document) {
        if (document == null || document.getText() == null) {
            return "";
        }
        return document.getText()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private ChunkRange resolveChunkRange(Map<String, Object> metadata, String chunkText, int fallbackStart) {
        ChunkRange fallbackRange = fallbackRange(fallbackStart, chunkText.length());
        Integer charStart = readMetadataInt(metadata, "charStart");
        Integer charEnd = readMetadataInt(metadata, "charEnd");
        int chunkLength = chunkText.length();
        if (charStart != null && charEnd != null) {
            return isTrustedRange(charStart, charEnd, chunkLength)
                    ? new ChunkRange(charStart, charEnd)
                    : fallbackRange;
        }
        if (charStart != null) {
            return isTrustedStart(charStart, chunkLength)
                    ? new ChunkRange(charStart, safeAdd(charStart, chunkLength))
                    : fallbackRange;
        }
        if (charEnd != null) {
            return isTrustedEnd(charEnd, chunkLength)
                    ? new ChunkRange(charEnd - chunkLength, charEnd)
                    : fallbackRange;
        }
        return fallbackRange;
    }

    private ChunkRange fallbackRange(int fallbackStart, int chunkLength) {
        int safeStart = Math.max(0, fallbackStart);
        return new ChunkRange(safeStart, safeAdd(safeStart, chunkLength));
    }

    private boolean isTrustedRange(int charStart, int charEnd, int chunkLength) {
        return isTrustedStart(charStart, chunkLength) && charEnd >= charStart + chunkLength;
    }

    private boolean isTrustedStart(int charStart, int chunkLength) {
        return (long) charStart + chunkLength <= Integer.MAX_VALUE;
    }

    private boolean isTrustedEnd(int charEnd, int chunkLength) {
        return charEnd >= chunkLength;
    }

    private int safeAdd(int value, int delta) {
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, value) + delta);
    }

    private DocumentChunkEntity buildChunk(Long documentId, Long groupId, int chunkIndex, String chunkText,
                                           int charStart, int charEnd, Map<String, Object> metadata) {
        LocalDateTime now = LocalDateTime.now();
        DocumentChunkEntity chunk = new DocumentChunkEntity();
        chunk.setDocumentId(documentId);
        chunk.setGroupId(groupId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setChunkText(chunkText);
        chunk.setChunkSummary(buildSummary(chunkText));
        chunk.setCharStart(charStart);
        chunk.setCharEnd(charEnd);
        chunk.setMetadataJson(buildMetadataJson(documentId, groupId, chunkIndex, charStart, charEnd, metadata));
        chunk.setCreatedAt(now);
        chunk.setUpdatedAt(now);
        return chunk;
    }

    private String buildSummary(String chunkText) {
        if (chunkText.length() <= CHUNK_SUMMARY_LENGTH) {
            return chunkText;
        }
        return chunkText.substring(0, CHUNK_SUMMARY_LENGTH) + "...";
    }

    private String buildMetadataJson(Long documentId, Long groupId, int chunkIndex, int charStart, int charEnd) {
        return buildMetadataJson(documentId, groupId, chunkIndex, charStart, charEnd, Map.of());
    }

    private String buildMetadataJson(Long documentId, Long groupId, int chunkIndex, int charStart, int charEnd,
                                     Map<String, Object> sourceMetadata) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (sourceMetadata != null && !sourceMetadata.isEmpty()) {
            metadata.putAll(sourceMetadata);
        }
        metadata.put("documentId", documentId);
        metadata.put("groupId", groupId);
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("charStart", charStart);
        metadata.put("charEnd", charEnd);
        metadata.put("sectionPath", readMetadataString(sourceMetadata, "sectionPath"));
        metadata.put("chunkStrategy", readMetadataString(sourceMetadata, "chunkStrategy", DEFAULT_CHUNK_STRATEGY));
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("文档切片元数据序列化失败", exception);
        }
    }

    private Integer readMetadataInt(Map<String, Object> metadata, String key) {
        if (metadata == null || !metadata.containsKey(key) || metadata.get(key) == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof Number || value instanceof String text && !text.isBlank()) {
            try {
                BigDecimal decimal = new BigDecimal(String.valueOf(value).trim());
                if (decimal.signum() < 0 || decimal.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
                    return null;
                }
                return decimal.stripTrailingZeros().scale() <= 0 ? decimal.intValueExact() : null;
            } catch (NumberFormatException | ArithmeticException ignored) {
                return null;
            }
        }
        return null;
    }

    private String readMetadataString(Map<String, Object> metadata, String key) {
        return readMetadataString(metadata, key, null);
    }

    private String readMetadataString(Map<String, Object> metadata, String key, String defaultValue) {
        if (metadata == null || !metadata.containsKey(key)) {
            return defaultValue;
        }
        Object value = metadata.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private record ChunkRange(int charStart, int charEnd) {}
}
