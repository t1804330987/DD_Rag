package com.dong.ddrag.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dong.ddrag.modelplatform.model.enums.ConnectionOwnerType;
import com.dong.ddrag.modelplatform.model.enums.ModelScenario;
import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationContext;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationDispatcher;
import com.dong.ddrag.modelplatform.runtime.ModelRuntimeService;
import com.dong.ddrag.qa.model.QueryPlanResult;
import com.dong.ddrag.qa.model.QueryPlanStrategy;
import com.dong.ddrag.qa.service.QueryPlanningService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;

class QueryPlanningServiceTest {
    @Test
    void shouldUseQueryPlanningScenarioForStructuredPlan() {
        ModelRuntimeService runtimeService = mock(ModelRuntimeService.class);
        ModelInvocationDispatcher dispatcher = mock(ModelInvocationDispatcher.class);
        ModelInvocationContext context = context(7L);
        when(runtimeService.resolveScenario(eq(7L), eq(ModelScenario.QUERY_PLANNING), any())).thenReturn(context);
        when(dispatcher.call(eq(context), any(Prompt.class))).thenReturn(response("""
                {"strategy":"REWRITE","queries":["文档上传流程"]}
                """));

        QueryPlanResult result = service(runtimeService, dispatcher).plan(7L, "请问上传流程是什么");

        assertThat(result.strategy()).isEqualTo(QueryPlanStrategy.REWRITE);
        assertThat(result.queries()).containsExactly("请问上传流程是什么", "文档上传流程");
        verify(runtimeService).resolveScenario(eq(7L), eq(ModelScenario.QUERY_PLANNING), any());
        verify(dispatcher).call(eq(context), any(Prompt.class));
    }

    @Test
    void shouldFallbackWhenUserContextIsMissingWithoutModelInvocation() {
        ModelRuntimeService runtimeService = mock(ModelRuntimeService.class);
        ModelInvocationDispatcher dispatcher = mock(ModelInvocationDispatcher.class);

        QueryPlanResult result = service(runtimeService, dispatcher).plan("上传流程");

        assertThat(result).isEqualTo(QueryPlanResult.fallback("上传流程"));
        org.mockito.Mockito.verifyNoInteractions(runtimeService, dispatcher);
    }

    @Test
    void shouldFallbackWhenGovernedInvocationFails() {
        ModelRuntimeService runtimeService = mock(ModelRuntimeService.class);
        ModelInvocationDispatcher dispatcher = mock(ModelInvocationDispatcher.class);
        ModelInvocationContext context = context(7L);
        when(runtimeService.resolveScenario(eq(7L), eq(ModelScenario.QUERY_PLANNING), any())).thenReturn(context);
        when(dispatcher.call(eq(context), any(Prompt.class))).thenThrow(new IllegalStateException("unavailable"));

        QueryPlanResult result = service(runtimeService, dispatcher).plan(7L, "上传流程");

        assertThat(result).isEqualTo(QueryPlanResult.fallback("上传流程"));
    }

    private QueryPlanningService service(ModelRuntimeService runtimeService, ModelInvocationDispatcher dispatcher) {
        return new QueryPlanningService(PromptTemplate.builder().template("问题：{question}").build(),
                runtimeService, dispatcher);
    }

    private ModelInvocationContext context(Long userId) {
        return new ModelInvocationContext(userId, ModelScenario.QUERY_PLANNING, 11L, 21L, 1L,
                ProviderType.DASHSCOPE, "qwen-plus", "internal", ConnectionOwnerType.PLATFORM,
                null, null, null, null, null);
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
