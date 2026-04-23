package com.dong.ddrag.retrieval.vectorstore;

import com.dong.ddrag.common.exception.BusinessException;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Component
public class PgVectorRetrievalAdapter {

    private final VectorStore vectorStore;

    public PgVectorRetrievalAdapter(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<VectorHit> search(Long groupId, String question, int topK) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .filterExpression(new FilterExpressionBuilder().eq("groupId", groupId).build())
                .build();
        return vectorStore.similaritySearch(searchRequest).stream()
                .map(document -> toVectorHit(groupId, document))
                .toList();
    }

    private VectorHit toVectorHit(Long expectedGroupId, Document document) {
        Map<String, Object> metadata = document.getMetadata();
        Long groupId = requireLong(metadata, "groupId");
        if (!expectedGroupId.equals(groupId)) {
            throw new BusinessException("向量检索返回了跨群组数据");
        }
        return new VectorHit(
                requireLong(metadata, "documentId"),
                requireLong(metadata, "chunkId"),
                requireInteger(metadata, "chunkIndex"),
                requireText(document.getText()),
                document.getScore() == null ? 0D : document.getScore()
        );
    }

    private Long requireLong(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException exception) {
                throw new BusinessException("向量检索元数据格式非法: " + key, exception);
            }
        }
        throw new BusinessException("向量检索缺少必要元数据: " + key);
    }

    private Integer requireInteger(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException exception) {
                throw new BusinessException("向量检索元数据格式非法: " + key, exception);
            }
        }
        throw new BusinessException("向量检索缺少必要元数据: " + key);
    }

    private String requireText(String text) {
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("向量检索返回空切片");
        }
        return text.trim();
    }

    public record VectorHit(
            Long documentId,
            Long chunkId,
            Integer chunkIndex,
            String chunkText,
            double score
    ) {
    }
}
