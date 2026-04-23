package com.dong.ddrag.retrieval.elasticsearch;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.ingestion.model.entity.DocumentChunkEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ElasticsearchChunkIndexService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchChunkIndexService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final double KEYWORD_SCORE_REFERENCE = 100D;
    private static final String READY_STATUS = "READY";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String indexName;
    private volatile boolean indexInitialized;

    @Autowired
    public ElasticsearchChunkIndexService(
            ObjectMapper objectMapper,
            @Value("${elasticsearch.host:localhost}") String host,
            @Value("${elasticsearch.port:9200}") int port,
            @Value("${elasticsearch.scheme:http}") String scheme,
            @Value("${elasticsearch.index-name:dd_rag_document_chunks}") String indexName
    ) {
        this(
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(REQUEST_TIMEOUT)
                        .build(),
                host,
                port,
                scheme,
                indexName
        );
    }

    ElasticsearchChunkIndexService(
            ObjectMapper objectMapper,
            HttpClient httpClient,
            String host,
            int port,
            String scheme,
            String indexName
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.baseUrl = "%s://%s:%d".formatted(scheme, host, port);
        this.indexName = indexName;
    }

    public void indexReadyChunks(String fileName, List<DocumentChunkEntity> chunks) {
        if (!StringUtils.hasText(fileName) || chunks == null || chunks.isEmpty()) {
            return;
        }
        ensureIndexInitialized();
        for (DocumentChunkEntity chunk : chunks) {
            indexChunk(fileName, chunk);
        }
    }

    public void deleteDocumentChunks(Long documentId) {
        if (documentId == null || documentId <= 0) {
            return;
        }
        ensureIndexInitialized();
        String path = "/%s/_delete_by_query".formatted(indexName);
        Map<String, Object> requestBody = Map.of(
                "query", Map.of("term", Map.of("documentId", documentId))
        );
        try {
            sendJsonRequest("POST", path, requestBody, true);
            log.info("ES 文档切片索引删除完成: documentId={}", documentId);
        } catch (RuntimeException exception) {
            log.warn("ES 文档切片索引删除失败: documentId={}, reason={}", documentId, exception.getMessage());
        }
    }

    public List<KeywordHit> search(Long groupId, String question, int topK) {
        if (groupId == null || groupId <= 0 || !StringUtils.hasText(question) || topK <= 0) {
            return List.of();
        }
        ensureIndexInitialized();
        Map<String, Object> requestBody = buildKeywordSearchRequestBody(groupId, question, topK);
        try {
            JsonNode root = sendJsonRequest("POST", "/%s/_search".formatted(indexName), requestBody, true);
            JsonNode hitsNode = root.path("hits").path("hits");
            if (!hitsNode.isArray() || hitsNode.isEmpty()) {
                return List.of();
            }
            List<KeywordHit> hits = new ArrayList<>();
            for (JsonNode hitNode : hitsNode) {
                JsonNode sourceNode = hitNode.path("_source");
                double rawScore = hitNode.path("_score").asDouble(0D);
                hits.add(new KeywordHit(
                        sourceNode.path("documentId").asLong(),
                        sourceNode.path("chunkId").asLong(),
                        sourceNode.path("chunkIndex").asInt(),
                        sourceNode.path("fileName").asText(""),
                        sourceNode.path("chunkText").asText(""),
                        rawScore,
                        normalizeKeywordScore(rawScore)
                ));
            }
            return List.copyOf(hits);
        } catch (RuntimeException exception) {
            log.warn(
                    "ES 关键词检索失败，降级为空结果: groupId={}, question='{}', reason={}",
                    groupId,
                    abbreviate(question),
                    exception.getMessage()
            );
            return List.of();
        }
    }

    private void indexChunk(String fileName, DocumentChunkEntity chunk) {
        validateChunk(chunk);
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("chunkId", chunk.getId());
        requestBody.put("groupId", chunk.getGroupId());
        requestBody.put("documentId", chunk.getDocumentId());
        requestBody.put("chunkIndex", chunk.getChunkIndex());
        requestBody.put("fileName", fileName);
        requestBody.put("chunkText", chunk.getChunkText());
        requestBody.put("status", READY_STATUS);
        requestBody.put("deleted", false);
        sendJsonRequest(
                "PUT",
                "/%s/_doc/%s".formatted(indexName, URLEncoder.encode(String.valueOf(chunk.getId()), StandardCharsets.UTF_8)),
                requestBody,
                false
        );
    }

    private void validateChunk(DocumentChunkEntity chunk) {
        if (chunk == null
                || chunk.getId() == null
                || chunk.getGroupId() == null
                || chunk.getDocumentId() == null
                || chunk.getChunkIndex() == null
                || !StringUtils.hasText(chunk.getChunkText())) {
            throw new BusinessException("ES 索引写入缺少必要 chunk 字段");
        }
    }

    private void ensureIndexInitialized() {
        if (indexInitialized) {
            return;
        }
        synchronized (this) {
            if (indexInitialized) {
                return;
            }
            if (!indexExists()) {
                createIndex();
            }
            indexInitialized = true;
        }
    }

    private boolean indexExists() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/%s".formatted(indexName)))
                    .timeout(REQUEST_TIMEOUT)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                return true;
            }
            if (response.statusCode() == 404) {
                return false;
            }
            throw new BusinessException("ES 索引检查失败: " + response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("ES 索引检查失败", exception);
        } catch (IOException exception) {
            throw new BusinessException("ES 索引检查失败", exception);
        }
    }

    private void createIndex() {
        sendJsonRequest("PUT", "/%s".formatted(indexName), buildCreateIndexRequestBody(), false);
        log.info("ES 索引初始化完成: {}", indexName);
    }

    Map<String, Object> buildCreateIndexRequestBody() {
        return Map.of(
                "settings", Map.of(
                        "analysis", Map.of(
                                "analyzer", Map.of(
                                        "ddrag_ik_index", Map.of(
                                                "type", "custom",
                                                "tokenizer", "ik_max_word"
                                        ),
                                        "ddrag_ik_search", Map.of(
                                                "type", "custom",
                                                "tokenizer", "ik_smart"
                                        )
                                )
                        )
                ),
                "mappings", Map.of(
                        "properties", Map.of(
                                "groupId", Map.of("type", "long"),
                                "documentId", Map.of("type", "long"),
                                "chunkId", Map.of("type", "long"),
                                "chunkIndex", Map.of("type", "integer"),
                                "status", Map.of("type", "keyword"),
                                "deleted", Map.of("type", "boolean"),
                                "fileName", Map.of(
                                        "type", "text",
                                        "analyzer", "ddrag_ik_index",
                                        "search_analyzer", "ddrag_ik_search",
                                        "fields", Map.of(
                                                "keyword", Map.of(
                                                        "type", "keyword",
                                                        "ignore_above", 256
                                                )
                                        )
                                ),
                                "chunkText", Map.of(
                                        "type", "text",
                                        "analyzer", "ddrag_ik_index",
                                        "search_analyzer", "ddrag_ik_search"
                                )
                        )
                )
        );
    }

    Map<String, Object> buildKeywordSearchRequestBody(Long groupId, String question, int topK) {
        Map<String, Object> boolQuery = Map.of(
                "filter", List.of(
                        Map.of("term", Map.of("groupId", groupId)),
                        Map.of("term", Map.of("status", READY_STATUS)),
                        Map.of("term", Map.of("deleted", false))
                ),
                "should", buildKeywordShouldClauses(question),
                "minimum_should_match", 1
        );
        return Map.of(
                "size", topK,
                "_source", List.of("groupId", "documentId", "chunkId", "chunkIndex", "fileName", "chunkText"),
                "query", Map.of("bool", boolQuery),
                "rescore", Map.of(
                        "window_size", topK,
                        "query", Map.of(
                                "query_weight", 0.2D,
                                "rescore_query_weight", 1.0D,
                                "score_mode", "total",
                                "rescore_query", Map.of(
                                        "bool", Map.of(
                                                "should", buildKeywordRescoreShouldClauses(question),
                                                "minimum_should_match", 1
                                        )
                                )
                        )
                )
        );
    }

    private List<Map<String, Object>> buildKeywordShouldClauses(String question) {
        return List.of(
                Map.of("match_phrase", Map.of("fileName", Map.of("query", question, "boost", 8))),
                Map.of("match", Map.of("fileName", Map.of("query", question, "boost", 4))),
                Map.of("match_phrase", Map.of("chunkText", Map.of("query", question, "boost", 6))),
                Map.of("match", Map.of("chunkText", Map.of("query", question, "boost", 3)))
        );
    }

    private List<Map<String, Object>> buildKeywordRescoreShouldClauses(String question) {
        return List.of(
                Map.of("match_phrase", Map.of("fileName", Map.of("query", question, "boost", 8))),
                Map.of("match", Map.of("fileName", Map.of("query", question, "operator", "and", "boost", 5))),
                Map.of("match_phrase", Map.of("chunkText", Map.of("query", question, "boost", 7))),
                Map.of("match", Map.of("chunkText", Map.of("query", question, "operator", "and", "boost", 4)))
        );
    }

    private JsonNode sendJsonRequest(
            String method,
            String path,
            Map<String, Object> requestBody,
            boolean ignoreMissingIndex
    ) {
        try {
            String body = objectMapper.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (ignoreMissingIndex && response.statusCode() == 404) {
                return objectMapper.createObjectNode();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("ES 请求失败: " + response.statusCode() + ", body=" + response.body());
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("ES 请求失败", exception);
        } catch (IOException exception) {
            throw new BusinessException("ES 请求失败", exception);
        }
    }

    private String abbreviate(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }

    private double normalizeKeywordScore(double rawScore) {
        if (rawScore <= 0D) {
            return 0D;
        }
        return Math.min(1D, Math.log1p(rawScore) / Math.log1p(KEYWORD_SCORE_REFERENCE));
    }

    public record KeywordHit(
            Long documentId,
            Long chunkId,
            Integer chunkIndex,
            String fileName,
            String chunkText,
            double rawScore,
            double normalizedScore
    ) {
    }
}
