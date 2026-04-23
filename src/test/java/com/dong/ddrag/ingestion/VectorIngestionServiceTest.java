package com.dong.ddrag.ingestion;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.ingestion.model.entity.DocumentChunkEntity;
import com.dong.ddrag.ingestion.vector.VectorIngestionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class VectorIngestionServiceTest {

    @Test
    void shouldDeleteExistingVectorsBeforeAddingStableDocuments() {
        VectorStore vectorStore = mock(VectorStore.class);
        VectorIngestionService vectorIngestionService = new VectorIngestionService(vectorStore);
        DocumentChunkEntity chunk = new DocumentChunkEntity();
        chunk.setId(9001L);
        chunk.setDocumentId(1001L);
        chunk.setGroupId(2001L);
        chunk.setChunkIndex(0);
        chunk.setChunkText("chunk body");

        vectorIngestionService.ingestChunks(List.of(chunk));

        ArgumentCaptor<Filter.Expression> deleteCaptor = ArgumentCaptor.forClass(Filter.Expression.class);
        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        inOrder(vectorStore).verify(vectorStore).delete(deleteCaptor.capture());
        verify(vectorStore).add(documentsCaptor.capture());
        assertThat(deleteCaptor.getValue()).isEqualTo(new FilterExpressionBuilder().eq("documentId", 1001L).build());
        Document vectorDocument = documentsCaptor.getValue().getFirst();
        assertThat(vectorDocument.getId()).isEqualTo(stableVectorId(1001L, 0));
        assertThat(vectorDocument.getMetadata())
                .containsEntry("groupId", 2001L)
                .containsEntry("documentId", 1001L)
                .containsEntry("chunkId", 9001L)
                .containsEntry("chunkIndex", 0);
    }

    @Test
    void shouldIgnoreEmptyChunkList() {
        VectorStore vectorStore = mock(VectorStore.class);
        VectorIngestionService vectorIngestionService = new VectorIngestionService(vectorStore);

        vectorIngestionService.ingestChunks(List.of());

        verifyNoInteractions(vectorStore);
    }

    @Test
    void shouldRejectChunkWithoutPersistedId() {
        VectorStore vectorStore = mock(VectorStore.class);
        VectorIngestionService vectorIngestionService = new VectorIngestionService(vectorStore);
        DocumentChunkEntity chunk = new DocumentChunkEntity();
        chunk.setDocumentId(1001L);
        chunk.setGroupId(2001L);
        chunk.setChunkIndex(0);
        chunk.setChunkText("chunk body");

        assertThatThrownBy(() -> vectorIngestionService.ingestChunks(List.of(chunk)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("向量写入前必须先完成 chunk 落库");
    }

    @Test
    void shouldUseStableIdsAcrossRepeatedIngestion() {
        VectorStore vectorStore = mock(VectorStore.class);
        VectorIngestionService vectorIngestionService = new VectorIngestionService(vectorStore);
        DocumentChunkEntity firstChunk = createChunk(11L, 1001L, 2001L, 0, "first body");
        DocumentChunkEntity secondChunk = createChunk(22L, 1001L, 2001L, 0, "second body");

        vectorIngestionService.ingestChunks(List.of(firstChunk));
        vectorIngestionService.ingestChunks(List.of(secondChunk));

        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(2)).add(documentsCaptor.capture());
        assertThat(documentsCaptor.getAllValues())
                .extracting(documents -> documents.getFirst().getId())
                .containsExactly(stableVectorId(1001L, 0), stableVectorId(1001L, 0));
    }

    @Test
    void shouldAddDocumentsInConfiguredBatches() {
        VectorStore vectorStore = mock(VectorStore.class);
        VectorIngestionService vectorIngestionService = new VectorIngestionService(vectorStore, 2);
        List<DocumentChunkEntity> chunks = List.of(
                createChunk(11L, 1001L, 2001L, 0, "first body"),
                createChunk(12L, 1001L, 2001L, 1, "second body"),
                createChunk(13L, 1001L, 2001L, 2, "third body")
        );

        vectorIngestionService.ingestChunks(chunks);

        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(2)).add(documentsCaptor.capture());
        assertThat(documentsCaptor.getAllValues()).extracting(List::size).containsExactly(2, 1);
    }

    @Test
    void shouldPreserveFileNameInVectorMetadata() {
        VectorStore vectorStore = mock(VectorStore.class);
        VectorIngestionService vectorIngestionService = new VectorIngestionService(vectorStore);
        DocumentChunkEntity chunk = createChunk(9001L, 1001L, 2001L, 0, "chunk body");
        chunk.setMetadataJson("""
                {"fileName":"产品手册.md"}
                """);

        vectorIngestionService.ingestChunks(List.of(chunk));

        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documentsCaptor.capture());
        Document vectorDocument = documentsCaptor.getValue().getFirst();
        assertThat(vectorDocument.getMetadata())
                .containsEntry("fileName", "产品手册.md");
    }

    @Test
    void shouldFallbackToLegacyFileNameFieldForVectorMetadata() {
        VectorStore vectorStore = mock(VectorStore.class);
        VectorIngestionService vectorIngestionService = new VectorIngestionService(vectorStore);
        DocumentChunkEntity chunk = createChunk(9001L, 1001L, 2001L, 0, "chunk body");
        chunk.setMetadataJson("""
                {"documentName":"旧产品手册.md"}
                """);

        vectorIngestionService.ingestChunks(List.of(chunk));

        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documentsCaptor.capture());
        Document vectorDocument = documentsCaptor.getValue().getFirst();
        assertThat(vectorDocument.getMetadata())
                .containsEntry("fileName", "旧产品手册.md");
    }

    private DocumentChunkEntity createChunk(
            Long chunkId,
            Long documentId,
            Long groupId,
            Integer chunkIndex,
            String chunkText
    ) {
        DocumentChunkEntity chunk = new DocumentChunkEntity();
        chunk.setId(chunkId);
        chunk.setDocumentId(documentId);
        chunk.setGroupId(groupId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setChunkText(chunkText);
        return chunk;
    }

    private String stableVectorId(Long documentId, Integer chunkIndex) {
        return UUID.nameUUIDFromBytes((documentId + ":" + chunkIndex).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
