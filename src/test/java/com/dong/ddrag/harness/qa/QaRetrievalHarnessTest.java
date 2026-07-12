package com.dong.ddrag.harness.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.groupmembership.service.GroupMembershipService;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.modelplatform.model.enums.ConnectionOwnerType;
import com.dong.ddrag.modelplatform.model.enums.ModelScenario;
import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationContext;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationDispatcher;
import com.dong.ddrag.modelplatform.runtime.ModelRuntimeService;
import com.dong.ddrag.qa.model.EvidenceLevel;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.mock.web.MockHttpServletRequest;

class QaRetrievalHarnessTest {
    private static final Long USER_ID = 7L;
    private static final Long GROUP_ID = 2001L;
    private static final String QUESTION = "产品团队如何安排迭代？";

    @Test
    void readableMemberRetrievesEvidenceAndReturnsGroundedCitationsThroughQaScenario() {
        HarnessRuntime runtime = createRuntime();
        when(runtime.evidenceRetriever().retrieve(USER_ID, GROUP_ID, QUESTION)).thenReturn(bundle());
        when(runtime.dispatcher().call(any(), any(Prompt.class))).thenReturn(response("""
                {"answered":true,"answer":"产品团队每两周发布一次。"}
                """));

        AskQuestionResponse result = runtime.qaService().ask(new MockHttpServletRequest(), askRequest());

        assertThat(result.answered()).isTrue();
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().getFirst().documentId()).isEqualTo(3001L);
        org.mockito.Mockito.verify(runtime.runtimeService())
                .resolveScenario(eq(USER_ID), eq(ModelScenario.QA_ANSWER), any());
    }

    @Test
    void nonMemberIsRejectedBeforeRetrievalOrModelSideEffects() {
        HarnessRuntime runtime = createRuntime();
        when(runtime.groupMembershipService().requireGroupReadable(any(HttpServletRequest.class), eq(GROUP_ID)))
                .thenThrow(new BusinessException("当前用户不是目标群组成员"));

        assertThatThrownBy(() -> runtime.qaService().ask(new MockHttpServletRequest(), askRequest()))
                .isInstanceOf(BusinessException.class).hasMessage("当前用户不是目标群组成员");

        verifyNoInteractions(runtime.evidenceRetriever(), runtime.runtimeService(), runtime.dispatcher());
    }

    @Test
    void emptyEvidenceReturnsUnansweredAndDoesNotCallModel() {
        HarnessRuntime runtime = createRuntime();
        when(runtime.evidenceRetriever().retrieve(USER_ID, GROUP_ID, QUESTION)).thenReturn(RetrievedEvidenceBundle.empty());

        AskQuestionResponse result = runtime.qaService().ask(new MockHttpServletRequest(), askRequest());

        assertThat(result.reasonCode()).isEqualTo("INSUFFICIENT_EVIDENCE");
        verifyNoInteractions(runtime.runtimeService(), runtime.dispatcher());
    }

    private HarnessRuntime createRuntime() {
        GroupMembershipService membership = mock(GroupMembershipService.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.requireBusinessUser(any(HttpServletRequest.class)))
                .thenReturn(new CurrentUserService.CurrentUser(USER_ID, "u7", "user", SystemRole.USER, false));
        EvidenceRetriever retriever = mock(EvidenceRetriever.class);
        ModelRuntimeService runtime = mock(ModelRuntimeService.class);
        ModelInvocationDispatcher dispatcher = mock(ModelInvocationDispatcher.class);
        when(runtime.resolveScenario(eq(USER_ID), eq(ModelScenario.QA_ANSWER), any())).thenReturn(context());
        DocumentRetriever advisorRetriever = mock(DocumentRetriever.class);
        when(advisorRetriever.retrieve(any())).thenReturn(List.of());
        RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(advisorRetriever).build();
        QaChatService chat = new QaChatService(template("系统规则"), advisor,
                template("问题：{question}\n证据等级：{evidenceLevel}\n回答策略：{evidenceGuidance}"), retriever,
                new QaAnswerParser(new ObjectMapper()), new CitationAssembler(), runtime, dispatcher);
        return new HarnessRuntime(new QaService(membership, chat, currentUser), membership, retriever, runtime, dispatcher);
    }

    private AskQuestionRequest askRequest() {
        AskQuestionRequest request = new AskQuestionRequest();
        request.setGroupId(GROUP_ID);
        request.setQuestion(QUESTION);
        return request;
    }

    private RetrievedEvidenceBundle bundle() {
        return new RetrievedEvidenceBundle(List.of(Document.builder().id("E1").text("产品团队每两周发布一次。")
                .metadata(Map.of("evidenceId", "E1", "documentId", 3001L, "chunkId", 9001L,
                        "chunkIndex", 0, "fileName", "产品手册.md", "score", 0.91D)).build()),
                EvidenceLevel.SUFFICIENT, "仅基于证据回答。");
    }

    private ModelInvocationContext context() {
        return new ModelInvocationContext(USER_ID, ModelScenario.QA_ANSWER, 11L, 21L, 1L,
                ProviderType.DASHSCOPE, "qwen-plus", "qa", ConnectionOwnerType.PLATFORM,
                null, null, null, null, null);
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private PromptTemplate template(String value) {
        return PromptTemplate.builder().template(value).build();
    }

    private record HarnessRuntime(QaService qaService, GroupMembershipService groupMembershipService,
                                  EvidenceRetriever evidenceRetriever, ModelRuntimeService runtimeService,
                                  ModelInvocationDispatcher dispatcher) { }
}
