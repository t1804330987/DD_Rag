package com.dong.ddrag.retrieval;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.retrieval.vectorstore.PgVectorRetrievalAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PgVectorRetrievalAdapterTest {

    @Test
    void shouldSearchWithTopKAndGroupFilter() {
        VectorStore vectorStore = mock(VectorStore.class);
        given(vectorStore.similaritySearch(any(SearchRequest.class))).willReturn(List.of(
                vectorDocument(1001L, 9001L, 2001L, 0, 0.82D, "检索结果")
        ));
        PgVectorRetrievalAdapter adapter = new PgVectorRetrievalAdapter(vectorStore);

        List<PgVectorRetrievalAdapter.VectorHit> hits = adapter.search(2001L, "如何安排迭代", 5);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest searchRequest = captor.getValue();
        assertThat(searchRequest.getTopK()).isEqualTo(5);
        assertThat(searchRequest.getQuery()).isEqualTo("如何安排迭代");
        assertThat(searchRequest.getFilterExpression().toString())
                .isEqualTo(new FilterExpressionBuilder().eq("groupId", 2001L).build().toString());
        assertThat(hits).singleElement().satisfies(hit -> {
            assertThat(hit.documentId()).isEqualTo(1001L);
            assertThat(hit.chunkId()).isEqualTo(9001L);
            assertThat(hit.score()).isEqualTo(0.82D);
        });
    }

    @Test
    void shouldRejectCrossGroupVectorResult() {
        VectorStore vectorStore = mock(VectorStore.class);
        given(vectorStore.similaritySearch(any(SearchRequest.class))).willReturn(List.of(
                vectorDocument(1001L, 9001L, 2002L, 0, 0.82D, "跨组结果")
        ));
        PgVectorRetrievalAdapter adapter = new PgVectorRetrievalAdapter(vectorStore);

        assertThatThrownBy(() -> adapter.search(2001L, "如何安排迭代", 5))
                .isInstanceOf(BusinessException.class)
                .hasMessage("向量检索返回了跨群组数据");
    }

    @Test
    void shouldWrapInvalidStringMetadataAsBusinessException() {
        VectorStore vectorStore = mock(VectorStore.class);
        given(vectorStore.similaritySearch(any(SearchRequest.class))).willReturn(List.of(
                Document.builder()
                        .id("bad-metadata")
                        .text("非法 metadata")
                        .score(0.80D)
                        .metadata(Map.of(
                                "groupId", "bad-group-id",
                                "documentId", 1001L,
                                "chunkId", 9001L,
                                "chunkIndex", 0
                        ))
                        .build()
        ));
        PgVectorRetrievalAdapter adapter = new PgVectorRetrievalAdapter(vectorStore);

        assertThatThrownBy(() -> adapter.search(2001L, "如何安排迭代", 5))
                .isInstanceOf(BusinessException.class)
                .hasMessage("向量检索元数据格式非法: groupId");
    }

    private Document vectorDocument(
            Long documentId,
            Long chunkId,
            Long groupId,
            int chunkIndex,
            double score,
            String text
    ) {
        return Document.builder()
                .id(documentId + ":" + chunkIndex)
                .text(text)
                .score(score)
                .metadata(Map.of(
                        "groupId", groupId,
                        "documentId", documentId,
                        "chunkId", chunkId,
                        "chunkIndex", chunkIndex
                ))
                .build();
    }
}
