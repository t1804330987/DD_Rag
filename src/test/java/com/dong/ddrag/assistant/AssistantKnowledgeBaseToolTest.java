package com.dong.ddrag.assistant;

import com.dong.ddrag.assistant.agent.AssistantKnowledgeBaseTool;
import com.dong.ddrag.assistant.agent.AssistantKnowledgeBaseToolResultHolder;
import com.dong.ddrag.assistant.model.vo.tool.KnowledgeBaseSearchToolResponse;
import com.dong.ddrag.qa.model.EvidenceLevel;
import com.dong.ddrag.qa.model.vo.AskQuestionResponse;
import com.dong.ddrag.qa.rag.ReadyChunkDocumentRetriever;
import com.dong.ddrag.qa.rag.RetrievedEvidenceBundle;
import com.dong.ddrag.qa.support.CitationAssembler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AssistantKnowledgeBaseToolTest {

    @Mock
    private ReadyChunkDocumentRetriever readyChunkDocumentRetriever;

    @Test
    void shouldReturnEvidenceWithoutGeneratingAnswer() {
        AssistantKnowledgeBaseTool tool = new AssistantKnowledgeBaseTool(
                readyChunkDocumentRetriever,
                new CitationAssembler()
        );
        AssistantKnowledgeBaseToolResultHolder resultHolder = new AssistantKnowledgeBaseToolResultHolder();
        given(readyChunkDocumentRetriever.retrieveEvidence(2001L, "上传流程是什么"))
                .willReturn(new RetrievedEvidenceBundle(
                        List.of(Document.builder()
                                .id("E1")
                                .text("上传前需要完成文档解析。")
                                .metadata(Map.of(
                                        "documentId", 1L,
                                        "chunkId", 2L,
                                        "chunkIndex", 0,
                                        "fileName", "文档.md",
                                        "score", 0.9D
                                ))
                                .build()),
                        EvidenceLevel.SUFFICIENT,
                        "当前证据较充分，可以正常回答。"
                ));

        KnowledgeBaseSearchToolResponse response = tool.search(
                "上传流程是什么",
                new ToolContext(Map.of(
                        "groupId", 2001L,
                        "knowledgeBaseToolResultHolder", resultHolder
                ))
        );

        assertThat(response.found()).isTrue();
        assertThat(response.evidenceGuidance()).contains("证据较充分");
        assertThat(response.evidences()).hasSize(1);
        assertThat(response.evidences().getFirst().snippet()).isEqualTo("上传前需要完成文档解析。");
        assertThat(response.citations())
                .extracting(AskQuestionResponse.Citation::fileName)
                .containsExactly("文档.md");
        assertThat(resultHolder.currentCitations())
                .extracting(AskQuestionResponse.Citation::fileName)
                .containsExactly("文档.md");
    }

    @Test
    void shouldBlockDuplicateSearchInSameAgentTurn() {
        AssistantKnowledgeBaseTool tool = new AssistantKnowledgeBaseTool(
                readyChunkDocumentRetriever,
                new CitationAssembler()
        );
        AssistantKnowledgeBaseToolResultHolder resultHolder = new AssistantKnowledgeBaseToolResultHolder();
        resultHolder.recordCitations(List.of(new AskQuestionResponse.Citation(
                1L,
                2L,
                0,
                "文档.md",
                0.9D,
                "片段"
        )));

        KnowledgeBaseSearchToolResponse response = tool.search(
                "再次检索",
                new ToolContext(Map.of(
                        "groupId", 2001L,
                        "knowledgeBaseToolResultHolder", resultHolder
                ))
        );

        assertThat(response.found()).isFalse();
        assertThat(response.reasonCode()).isEqualTo("DUPLICATE_TOOL_CALL");
        assertThat(response.reasonMessage()).contains("不要再次调用工具");
        assertThat(response.citations())
                .extracting(AskQuestionResponse.Citation::fileName)
                .containsExactly("文档.md");
        then(readyChunkDocumentRetriever).shouldHaveNoInteractions();
    }
}
