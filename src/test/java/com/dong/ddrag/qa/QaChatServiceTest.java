package com.dong.ddrag.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dong.ddrag.modelplatform.model.enums.ConnectionOwnerType;
import com.dong.ddrag.modelplatform.model.enums.ModelScenario;
import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationContext;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationDispatcher;
import com.dong.ddrag.modelplatform.runtime.ModelRuntimeService;
import com.dong.ddrag.qa.model.EvidenceLevel;
import com.dong.ddrag.qa.model.vo.AskQuestionResponse;
import com.dong.ddrag.qa.rag.EvidenceRetriever;
import com.dong.ddrag.qa.rag.RetrievedEvidenceBundle;
import com.dong.ddrag.qa.service.QaChatService;
import com.dong.ddrag.qa.support.CitationAssembler;
import com.dong.ddrag.qa.support.QaAnswerParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

class QaChatServiceTest {

    @Test
    void shouldReturnUnansweredWithoutEvidenceWithoutResolvingModel() {
        EvidenceRetriever retriever = mock(EvidenceRetriever.class);
        ModelRuntimeService runtimeService = mock(ModelRuntimeService.class);
        ModelInvocationDispatcher dispatcher = mock(ModelInvocationDispatcher.class);
        when(retriever.retrieve(7L, 2001L, "上传流程")).thenReturn(RetrievedEvidenceBundle.empty());

        AskQuestionResponse response = service(retriever, runtimeService, dispatcher)
                .ask(7L, 2001L, "上传流程");

        assertThat(response.answered()).isFalse();
        assertThat(response.reasonCode()).isEqualTo("INSUFFICIENT_EVIDENCE");
        verifyNoInteractions(runtimeService, dispatcher);
    }

    @Test
    void shouldUseQaAnswerScenarioAndReturnGroundedCitations() {
        EvidenceRetriever retriever = mock(EvidenceRetriever.class);
        ModelRuntimeService runtimeService = mock(ModelRuntimeService.class);
        ModelInvocationDispatcher dispatcher = mock(ModelInvocationDispatcher.class);
        ModelInvocationContext context = context(7L, ModelScenario.QA_ANSWER);
        when(retriever.retrieve(7L, 2001L, "上传流程")).thenReturn(bundle());
        when(runtimeService.resolveScenario(eq(7L), eq(ModelScenario.QA_ANSWER), any()))
                .thenReturn(context);
        when(dispatcher.call(eq(context), any(Prompt.class))).thenReturn(response("""
                {"answered":true,"answer":"产品团队每两周发布一次。"}
                """));

        AskQuestionResponse answer = service(retriever, runtimeService, dispatcher)
                .ask(7L, 2001L, "上传流程");

        assertThat(answer.answered()).isTrue();
        assertThat(answer.answer()).isEqualTo("产品团队每两周发布一次。");
        assertThat(answer.citations()).hasSize(1);
        verify(runtimeService).resolveScenario(eq(7L), eq(ModelScenario.QA_ANSWER), any());
        verify(dispatcher).call(eq(context), any(Prompt.class));
    }

    @Test
    void shouldFallbackToRawAnswerThroughASecondGovernedInvocation() {
        EvidenceRetriever retriever = mock(EvidenceRetriever.class);
        ModelRuntimeService runtimeService = mock(ModelRuntimeService.class);
        ModelInvocationDispatcher dispatcher = mock(ModelInvocationDispatcher.class);
        ModelInvocationContext first = context(7L, ModelScenario.QA_ANSWER);
        ModelInvocationContext second = context(7L, ModelScenario.QA_ANSWER);
        when(retriever.retrieve(7L, 2001L, "上传流程")).thenReturn(bundle());
        when(runtimeService.resolveScenario(eq(7L), eq(ModelScenario.QA_ANSWER), any()))
                .thenReturn(first, second);
        when(dispatcher.call(eq(first), any(Prompt.class))).thenReturn(response("not-json"));
        when(dispatcher.call(eq(second), any(Prompt.class))).thenReturn(response("""
                {"answered":true,"answer":"产品团队每两周发布一次。"}
                """));

        AskQuestionResponse answer = service(retriever, runtimeService, dispatcher)
                .ask(7L, 2001L, "上传流程");

        assertThat(answer.answered()).isTrue();
        assertThat(answer.answer()).isEqualTo("产品团队每两周发布一次。");
        verify(dispatcher).call(eq(first), any(Prompt.class));
        verify(dispatcher).call(eq(second), any(Prompt.class));
    }

    @Test
    void shouldRejectLegacyCallWithoutActualUserContext() {
        QaChatService service = service(mock(EvidenceRetriever.class), mock(ModelRuntimeService.class),
                mock(ModelInvocationDispatcher.class));

        assertThatThrownBy(() -> service.ask(2001L, "上传流程"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("实际用户上下文");
    }

    private QaChatService service(EvidenceRetriever retriever, ModelRuntimeService runtimeService,
                                  ModelInvocationDispatcher dispatcher) {
        return new QaChatService(
                promptTemplate("系统规则"),
                retrievalAdvisor(),
                promptTemplate("问题：{question}\n证据等级：{evidenceLevel}\n回答策略：{evidenceGuidance}"),
                retriever,
                new QaAnswerParser(new ObjectMapper()),
                new CitationAssembler(),
                runtimeService,
                dispatcher
        );
    }

    private RetrievedEvidenceBundle bundle() {
        return new RetrievedEvidenceBundle(List.of(Document.builder().id("E1").text("产品团队每两周发布一次。")
                .metadata(Map.of("evidenceId", "E1", "documentId", 1001L, "chunkId", 9001L,
                        "chunkIndex", 0, "fileName", "产品手册.md", "score", 0.91D)).build()),
                EvidenceLevel.SUFFICIENT, "仅基于证据回答。");
    }

    private ModelInvocationContext context(Long userId, ModelScenario scenario) {
        return new ModelInvocationContext(userId, scenario, 11L, 21L, 1L, ProviderType.DASHSCOPE,
                "qwen-plus", "qa-route", ConnectionOwnerType.PLATFORM, null, null, null, null, null);
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private PromptTemplate promptTemplate(String template) {
        return PromptTemplate.builder().template(template).build();
    }

    private RetrievalAugmentationAdvisor retrievalAdvisor() {
        DocumentRetriever retriever = mock(DocumentRetriever.class);
        when(retriever.retrieve(any())).thenReturn(List.of());
        return RetrievalAugmentationAdvisor.builder().documentRetriever(retriever).build();
    }
}
