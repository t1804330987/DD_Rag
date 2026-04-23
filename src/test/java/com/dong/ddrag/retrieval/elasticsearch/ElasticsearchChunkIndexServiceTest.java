package com.dong.ddrag.retrieval.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ElasticsearchChunkIndexServiceTest {

    @Test
    void shouldBuildCreateIndexBodyWithExpectedMappingsAndAnalyzers() {
        ElasticsearchChunkIndexService service = new ElasticsearchChunkIndexService(
                new ObjectMapper(),
                mock(HttpClient.class),
                "localhost",
                9200,
                "http",
                "dd_rag_document_chunks"
        );

        Map<String, Object> requestBody = service.buildCreateIndexRequestBody();

        Map<String, Object> settings = castMap(requestBody.get("settings"));
        Map<String, Object> analysis = castMap(settings.get("analysis"));
        Map<String, Object> analyzer = castMap(analysis.get("analyzer"));
        Map<String, Object> mappings = castMap(requestBody.get("mappings"));
        Map<String, Object> properties = castMap(mappings.get("properties"));
        Map<String, Object> status = castMap(properties.get("status"));
        Map<String, Object> fileName = castMap(properties.get("fileName"));
        Map<String, Object> chunkText = castMap(properties.get("chunkText"));
        Map<String, Object> fileNameFields = castMap(fileName.get("fields"));
        Map<String, Object> fileNameKeyword = castMap(fileNameFields.get("keyword"));

        assertThat(analyzer).containsKeys("ddrag_ik_index", "ddrag_ik_search");
        assertThat(castMap(analyzer.get("ddrag_ik_index"))).containsEntry("tokenizer", "ik_max_word");
        assertThat(castMap(analyzer.get("ddrag_ik_search"))).containsEntry("tokenizer", "ik_smart");
        assertThat(status).containsEntry("type", "keyword");
        assertThat(fileName)
                .containsEntry("type", "text")
                .containsEntry("analyzer", "ddrag_ik_index")
                .containsEntry("search_analyzer", "ddrag_ik_search");
        assertThat(fileNameKeyword).containsEntry("type", "keyword");
        assertThat(chunkText)
                .containsEntry("type", "text")
                .containsEntry("analyzer", "ddrag_ik_index")
                .containsEntry("search_analyzer", "ddrag_ik_search");
        assertThat(properties).containsKeys("groupId", "documentId", "chunkId", "chunkIndex", "deleted");
    }

    @Test
    void shouldBuildKeywordSearchBodyWithSoftFilterAndRescore() {
        ElasticsearchChunkIndexService service = new ElasticsearchChunkIndexService(
                new ObjectMapper(),
                mock(HttpClient.class),
                "localhost",
                9200,
                "http",
                "dd_rag_document_chunks"
        );

        Map<String, Object> requestBody = service.buildKeywordSearchRequestBody(2001L, "训练 效率", 50);

        assertThat(requestBody).containsEntry("size", 50);
        assertThat(requestBody).containsKey("rescore");

        Map<String, Object> query = castMap(requestBody.get("query"));
        Map<String, Object> boolQuery = castMap(query.get("bool"));
        List<Map<String, Object>> filter = castList(boolQuery.get("filter"));
        List<Map<String, Object>> should = castList(boolQuery.get("should"));
        Map<String, Object> rescore = castMap(requestBody.get("rescore"));
        Map<String, Object> rescoreQuery = castMap(rescore.get("query"));
        Map<String, Object> rescoreBool = castMap(castMap(rescoreQuery.get("rescore_query")).get("bool"));

        assertThat(filter).containsExactly(
                Map.of("term", Map.of("groupId", 2001L)),
                Map.of("term", Map.of("status", "READY")),
                Map.of("term", Map.of("deleted", false))
        );
        assertThat(boolQuery).containsEntry("minimum_should_match", 1);
        assertThat(should).hasSize(4);
        assertThat(rescore).containsEntry("window_size", 50);
        assertThat(rescoreQuery)
                .containsEntry("query_weight", 0.2D)
                .containsEntry("rescore_query_weight", 1.0D)
                .containsEntry("score_mode", "total");
        assertThat(rescoreBool).containsEntry("minimum_should_match", 1);
        assertThat(castList(rescoreBool.get("should"))).hasSize(4);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
