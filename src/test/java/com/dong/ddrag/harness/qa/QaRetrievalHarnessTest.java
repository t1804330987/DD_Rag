package com.dong.ddrag.harness.qa;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.groupmembership.service.GroupMembershipService;
import com.dong.ddrag.qa.model.EvidenceLevel;
import com.dong.ddrag.qa.model.KnowledgeAnswerOutput;
import com.dong.ddrag.qa.model.dto.AskQuestionRequest;
import com.dong.ddrag.qa.model.vo.AskQuestionResponse;
import com.dong.ddrag.qa.rag.EvidenceRetriever;
import com.dong.ddrag.qa.rag.RetrievedEvidenceBundle;
import com.dong.ddrag.qa.service.QaChatService;
import com.dong.ddrag.qa.service.QaService;
import com.dong.ddrag.qa.support.CitationAssembler;
import com.dong.ddrag.qa.support.QaAnswerParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.InOrder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

@SuppressWarnings("unchecked")
class QaRetrievalHarnessTest {

    private static final Long GROUP_ID = 2001L;
    private static final String QUESTION = "产品团队如何安排迭代？";

    @Test
    void readableMemberRetrievesEvidenceAndReturnsGroundedCitations() {
        HarnessRuntime runtime = createRuntime();
        AskQuestionRequest request = askRequest();
        List<Document> evidence = List.of(Document.builder()
                .id("E1")
                .text("产品团队每两周发布一次。")
                .metadata(Map.of(
                        "evidenceId", "E1",
                        "documentId", 3001L,
                        "chunkId", 9001L,
                        "chunkIndex", 0,
                        "fileName", "产品手册.md",
                        "score", 0.91D
                ))
                .build());
        given(runtime.evidenceRetriever().retrieve(GROUP_ID, QUESTION))
                .willReturn(new RetrievedEvidenceBundle(
                        evidence,
                        EvidenceLevel.SUFFICIENT,
                        "当前证据较充分，可以正常回答，但仍然不得超出证据进行臆测。"
                ));
        given(runtime.chatClient().prompt(any(Prompt.class))
                .advisors(any(java.util.function.Consumer.class))
                .call()
                .entity(KnowledgeAnswerOutput.class))
                .willReturn(new KnowledgeAnswerOutput(true, "产品团队每两周发布一次。", null, null));

        AskQuestionResponse response = runtime.qaService().ask(new MockHttpServletRequest(), request);

        assertThat(response.answered()).isTrue();
        assertThat(response.answer()).isEqualTo("产品团队每两周发布一次。");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().getFirst().documentId()).isEqualTo(3001L);
        assertThat(response.citations().getFirst().chunkId()).isEqualTo(9001L);
        assertThat(response.citations().getFirst().fileName()).isEqualTo("产品手册.md");
        InOrder inOrder = inOrder(runtime.groupMembershipService(), runtime.evidenceRetriever(), runtime.chatClient());
        inOrder.verify(runtime.groupMembershipService()).requireGroupReadable(any(HttpServletRequest.class), any());
        inOrder.verify(runtime.evidenceRetriever()).retrieve(GROUP_ID, QUESTION);
        inOrder.verify(runtime.chatClient()).prompt(any(Prompt.class));
    }

    @Test
    void nonMemberIsRejectedBeforeRetrievalOrModelSideEffects() {
        HarnessRuntime runtime = createRuntime();
        given(runtime.groupMembershipService().requireGroupReadable(any(HttpServletRequest.class), any()))
                .willThrow(new BusinessException("当前用户不是目标群组成员"));

        assertThatThrownBy(() -> runtime.qaService().ask(new MockHttpServletRequest(), askRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前用户不是目标群组成员");

        then(runtime.evidenceRetriever()).shouldHaveNoInteractions();
        then(runtime.chatClient()).shouldHaveNoInteractions();
    }

    @Test
    void emptyEvidenceReturnsUnansweredAndDoesNotCallModel() {
        HarnessRuntime runtime = createRuntime();
        given(runtime.evidenceRetriever().retrieve(GROUP_ID, QUESTION))
                .willReturn(RetrievedEvidenceBundle.empty());

        AskQuestionResponse response = runtime.qaService().ask(new MockHttpServletRequest(), askRequest());

        assertThat(response.answered()).isFalse();
        assertThat(response.reasonCode()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(response.citations()).isEmpty();
        then(runtime.chatClient()).should(never()).prompt(any(Prompt.class));
    }

    @Test
    void missingStructuredModelOutputFailsClosedWithoutInventedCitations() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        EvidenceRetriever evidenceRetriever = mock(EvidenceRetriever.class);
        QaService qaService = new QaService(
                mock(GroupMembershipService.class),
                new QaChatService(
                        chatClient,
                        promptTemplate(),
                        evidenceRetriever,
                        new QaAnswerParser(new ObjectMapper()),
                        new CitationAssembler()
                )
        );
        given(evidenceRetriever.retrieve(GROUP_ID, QUESTION))
                .willReturn(new RetrievedEvidenceBundle(
                        List.of(Document.builder()
                                .id("E1")
                                .text("产品团队每两周发布一次。")
                                .metadata(Map.of("fileName", "产品手册.md"))
                                .build()),
                        EvidenceLevel.SUFFICIENT,
                        "只基于证据回答。"
                ));
        given(chatClient.prompt(any(Prompt.class))
                .advisors(any(java.util.function.Consumer.class))
                .call()
                .entity(KnowledgeAnswerOutput.class))
                .willReturn(null);

        AskQuestionResponse response = qaService.ask(new MockHttpServletRequest(), askRequest());

        assertThat(response.answered()).isFalse();
        assertThat(response.reasonCode()).isEqualTo("ANSWER_FORMAT_ERROR");
        assertThat(response.citations()).isEmpty();
    }

    private HarnessRuntime createRuntime() {
        GroupMembershipService groupMembershipService = mock(GroupMembershipService.class);
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        EvidenceRetriever evidenceRetriever = mock(EvidenceRetriever.class);
        QaChatService qaChatService = new QaChatService(
                chatClient,
                promptTemplate(),
                evidenceRetriever,
                new QaAnswerParser(new ObjectMapper()),
                new CitationAssembler()
        );
        return new HarnessRuntime(
                new QaService(groupMembershipService, qaChatService),
                groupMembershipService,
                chatClient,
                evidenceRetriever
        );
    }

    private AskQuestionRequest askRequest() {
        AskQuestionRequest request = new AskQuestionRequest();
        request.setGroupId(GROUP_ID);
        request.setQuestion(QUESTION);
        return request;
    }

    private PromptTemplate promptTemplate() {
        return PromptTemplate.builder()
                .template("""
                        问题：
                        {question}
                        证据等级：
                        {evidenceLevel}
                        回答策略：
                        {evidenceGuidance}
                        """)
                .build();
    }

    private record HarnessRuntime(
            QaService qaService,
            GroupMembershipService groupMembershipService,
            ChatClient chatClient,
            EvidenceRetriever evidenceRetriever
    ) {
    }
}
