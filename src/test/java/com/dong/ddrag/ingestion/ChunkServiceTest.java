package com.dong.ddrag.ingestion;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.ingestion.chunk.ChunkService;
import com.dong.ddrag.ingestion.mapper.DocumentChunkMapper;
import com.dong.ddrag.ingestion.model.entity.DocumentChunkEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChunkServiceTest {

    @Test
    void shouldPersistSpringAiDocumentsAsChunksWithMetadata() {
        DocumentChunkMapper mapper = mock(DocumentChunkMapper.class);
        ChunkService chunkService = new ChunkService(mapper, new ObjectMapper());
        Document first = Document.builder()
                .text("产品团队每两周发布一次。")
                .metadata(Map.of(
                        "charStart", 0,
                        "charEnd", 12,
                        "sectionPath", "迭代",
                        "fileName", "产品手册.md"
                ))
                .build();
        DocumentChunkEntity persisted = new DocumentChunkEntity();
        persisted.setId(101L);
        persisted.setChunkIndex(0);
        org.mockito.Mockito.when(mapper.selectByDocumentId(eq(3001L))).thenReturn(List.of(persisted));

        List<DocumentChunkEntity> chunks = chunkService.saveChunkDocuments(3001L, 2001L, List.of(first));

        ArgumentCaptor<List<DocumentChunkEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).deleteByDocumentId(3001L);
        verify(mapper).insertBatch(captor.capture());
        assertThat(chunks).hasSize(1);
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().getChunkIndex()).isEqualTo(0);
        assertThat(captor.getValue().getFirst().getMetadataJson()).contains("\"sectionPath\":\"迭代\"");
        assertThat(captor.getValue().getFirst().getMetadataJson()).contains("\"fileName\":\"产品手册.md\"");
    }

    @Test
    void shouldFallbackToSafeRangeWhenMetadataOffsetsAreInvalid() throws Exception {
        DocumentChunkMapper mapper = mock(DocumentChunkMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ChunkService chunkService = new ChunkService(mapper, objectMapper);
        Document overflowStart = Document.builder()
                .text("产品团队")
                .metadata(Map.of("charStart", Integer.MAX_VALUE))
                .build();
        Document invalidStringStart = Document.builder()
                .text("继续切片")
                .metadata(Map.of("charStart", "2147483648", "charEnd", "bad-value"))
                .build();
        DocumentChunkEntity persistedFirst = new DocumentChunkEntity();
        persistedFirst.setId(201L);
        persistedFirst.setChunkIndex(0);
        DocumentChunkEntity persistedSecond = new DocumentChunkEntity();
        persistedSecond.setId(202L);
        persistedSecond.setChunkIndex(1);
        org.mockito.Mockito.when(mapper.selectByDocumentId(eq(3001L)))
                .thenReturn(List.of(persistedFirst, persistedSecond));

        List<DocumentChunkEntity> chunks = chunkService.saveChunkDocuments(3001L, 2001L,
                List.of(overflowStart, invalidStringStart));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getCharStart()).isZero();
        assertThat(chunks.get(0).getCharEnd()).isEqualTo(chunks.get(0).getChunkText().length());
        assertThat(chunks.get(0).getCharEnd()).isGreaterThanOrEqualTo(chunks.get(0).getCharStart());
        assertThat(chunks.get(1).getCharStart()).isEqualTo(chunks.get(0).getCharEnd());
        assertThat(chunks.get(1).getCharEnd()).isGreaterThanOrEqualTo(chunks.get(1).getCharStart());

        Map<?, ?> firstMetadata = objectMapper.readValue(chunks.get(0).getMetadataJson(), Map.class);
        assertThat(((Number) firstMetadata.get("charStart")).intValue()).isGreaterThanOrEqualTo(0);
        assertThat(((Number) firstMetadata.get("charEnd")).intValue())
                .isGreaterThanOrEqualTo(((Number) firstMetadata.get("charStart")).intValue());
    }

    @Test
    void shouldPreserveFileNameInChunkMetadata() throws Exception {
        DocumentChunkMapper mapper = mock(DocumentChunkMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ChunkService chunkService = new ChunkService(mapper, objectMapper);
        Document first = Document.builder()
                .text("产品团队每两周发布一次。")
                .metadata(Map.of(
                        "charStart", 0,
                        "charEnd", 12,
                        "fileName", "产品手册.md"
                ))
                .build();
        DocumentChunkEntity persisted = new DocumentChunkEntity();
        persisted.setId(101L);
        persisted.setChunkIndex(0);
        org.mockito.Mockito.when(mapper.selectByDocumentId(eq(3001L))).thenReturn(List.of(persisted));

        List<DocumentChunkEntity> chunks = chunkService.saveChunkDocuments(3001L, 2001L, List.of(first));

        Map<?, ?> metadata = objectMapper.readValue(chunks.getFirst().getMetadataJson(), Map.class);
        assertThat(metadata.get("fileName")).isEqualTo("产品手册.md");
        assertThat(metadata.containsKey("fileName")).isTrue();
    }

    @Test
    void shouldRejectDocumentListWithoutValidText() {
        DocumentChunkMapper mapper = mock(DocumentChunkMapper.class);
        ChunkService chunkService = new ChunkService(mapper, new ObjectMapper());

        assertThatThrownBy(() -> chunkService.saveChunkDocuments(3001L, 2001L, Arrays.asList(
                null,
                Document.builder().text("   ").build()
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文档切片结果为空，无法持久化");
    }

    @Test
    void shouldBatchInsertTransformedChunkDocuments() {
        DocumentChunkMapper mapper = mock(DocumentChunkMapper.class);
        ChunkService chunkService = new ChunkService(mapper, new ObjectMapper());
        Document first = Document.builder().text("第一段").metadata(Map.of("charStart", 0, "charEnd", 3)).build();
        Document second = Document.builder().text("第二段").metadata(Map.of("charStart", 3, "charEnd", 6)).build();

        doAnswer(invocation -> {
            List<DocumentChunkEntity> persisted = invocation.getArgument(0);
            persisted.get(0).setId(11L);
            persisted.get(1).setId(12L);
            return 2;
        }).when(mapper).insertBatch(any());

        List<DocumentChunkEntity> chunks = chunkService.saveChunkDocuments(1001L, 2001L, List.of(first, second));

        ArgumentCaptor<List<DocumentChunkEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).deleteByDocumentId(1001L);
        verify(mapper).insertBatch(captor.capture());
        assertThat(chunks).hasSize(2);
        assertThat(chunks).extracting(DocumentChunkEntity::getId).containsExactly(11L, 12L);
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().getFirst())
                .extracting(DocumentChunkEntity::getDocumentId, DocumentChunkEntity::getGroupId, DocumentChunkEntity::getChunkIndex)
                .containsExactly(1001L, 2001L, 0);
        assertThat(captor.getValue().getFirst().getChunkSummary()).isNotBlank();
    }

    @Test
    void shouldReloadChunkIdsWhenBatchInsertDoesNotPopulateGeneratedKeys() {
        DocumentChunkMapper mapper = mock(DocumentChunkMapper.class);
        ChunkService chunkService = new ChunkService(mapper, new ObjectMapper());
        Document first = Document.builder().text("第一段").metadata(Map.of("charStart", 0, "charEnd", 3)).build();
        Document second = Document.builder().text("第二段").metadata(Map.of("charStart", 3, "charEnd", 6)).build();

        DocumentChunkEntity persistedFirst = new DocumentChunkEntity();
        persistedFirst.setId(101L);
        persistedFirst.setChunkIndex(0);
        DocumentChunkEntity persistedSecond = new DocumentChunkEntity();
        persistedSecond.setId(102L);
        persistedSecond.setChunkIndex(1);

        org.mockito.Mockito.when(mapper.selectByDocumentId(eq(1001L)))
                .thenReturn(List.of(persistedFirst, persistedSecond));

        List<DocumentChunkEntity> chunks = chunkService.saveChunkDocuments(1001L, 2001L, List.of(first, second));

        verify(mapper).selectByDocumentId(1001L);
        assertThat(chunks).extracting(DocumentChunkEntity::getId).containsExactly(101L, 102L);
    }

    @Test
    void shouldPreserveCauseWhenChunkMetadataSerializationFails() throws Exception {
        DocumentChunkMapper mapper = mock(DocumentChunkMapper.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ChunkService chunkService = new ChunkService(mapper, objectMapper);
        JsonProcessingException cause = new JsonProcessingException("broken json") { };
        doThrow(cause).when(objectMapper).writeValueAsString(any());

        Document document = Document.builder().text("正常文本").build();

        assertThatThrownBy(() -> chunkService.saveChunkDocuments(1001L, 2001L, List.of(document)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文档切片元数据序列化失败")
                .hasCause(cause);
    }
}
