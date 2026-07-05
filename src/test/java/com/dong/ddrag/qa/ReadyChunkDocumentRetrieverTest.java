package com.dong.ddrag.qa;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.qa.model.EvidenceLevel;
import com.dong.ddrag.qa.rag.EvidenceRetriever;
import com.dong.ddrag.qa.rag.ReadyChunkDocumentRetriever;
import com.dong.ddrag.qa.rag.RetrievedEvidenceBundle;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReadyChunkDocumentRetrieverTest {

    @Test
    void shouldDelegateToEvidenceRetriever() {
        EvidenceRetriever evidenceRetriever = mock(EvidenceRetriever.class);
        ReadyChunkDocumentRetriever retriever = new ReadyChunkDocumentRetriever(evidenceRetriever, 5);
        RetrievedEvidenceBundle evidenceBundle = new RetrievedEvidenceBundle(
                List.of(Document.builder().id("E1").text("chunk body").metadata(Map.of("evidenceId", "E1")).build()),
                EvidenceLevel.SUFFICIENT,
                "当前证据较充分，可以正常回答，但仍然不得超出证据进行臆测。"
        );
        when(evidenceRetriever.retrieve(2001L, "上传流程", 5)).thenReturn(evidenceBundle);

        List<Document> documents = retriever.retrieve(2001L, "上传流程");

        assertThat(documents).containsExactlyElementsOf(evidenceBundle.documents());
    }

    @Test
    void shouldReadGroupIdFromQueryContext() {
        EvidenceRetriever evidenceRetriever = mock(EvidenceRetriever.class);
        ReadyChunkDocumentRetriever retriever = new ReadyChunkDocumentRetriever(evidenceRetriever, 5);
        Query query = Query.builder()
                .text("上传流程")
                .context(Map.of("groupId", 2001L))
                .build();
        when(evidenceRetriever.retrieve(2001L, "上传流程", 5))
                .thenReturn(RetrievedEvidenceBundle.empty());

        List<Document> documents = retriever.retrieve(query);

        assertThat(documents).isEmpty();
    }

    @Test
    void shouldReusePrefetchedDocumentsFromQueryContext() {
        EvidenceRetriever evidenceRetriever = mock(EvidenceRetriever.class);
        ReadyChunkDocumentRetriever retriever = new ReadyChunkDocumentRetriever(evidenceRetriever, 5);
        List<Document> prefetchedDocuments = List.of(
                Document.builder()
                        .id("E1")
                        .text("chunk body")
                        .metadata(Map.of("evidenceId", "E1"))
                        .build()
        );
        Query query = Query.builder()
                .text("上传流程")
                .context(Map.of(
                        "groupId", 2001L,
                        ReadyChunkDocumentRetriever.PREFETCHED_DOCUMENTS_CONTEXT_KEY, prefetchedDocuments
                ))
                .build();

        List<Document> documents = retriever.retrieve(query);

        assertThat(documents).containsExactlyElementsOf(prefetchedDocuments);
        verifyNoInteractions(evidenceRetriever);
    }

    @Test
    void shouldRejectInvalidGroupIdInContext() {
        EvidenceRetriever evidenceRetriever = mock(EvidenceRetriever.class);
        ReadyChunkDocumentRetriever retriever = new ReadyChunkDocumentRetriever(evidenceRetriever, 5);
        Query query = Query.builder()
                .text("上传流程")
                .context(Map.of("groupId", "abc"))
                .build();

        assertThatThrownBy(() -> retriever.retrieve(query))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("groupId 非法");
    }
}
