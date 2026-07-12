package com.dong.ddrag.assistant;

import com.dong.ddrag.assistant.memory.AssistantMemorySummarizer;
import com.dong.ddrag.assistant.model.entity.AssistantMessageEntity;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.modelplatform.model.enums.ConnectionOwnerType;
import com.dong.ddrag.modelplatform.model.enums.ModelScenario;
import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationContext;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationDispatcher;
import com.dong.ddrag.modelplatform.runtime.ModelRuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantMemorySummarizerTest {

    @Test
    void shouldCallModelWithFormattedMessageStringForSessionMemory() {
        ModelRuntimeService runtimeService = mock(ModelRuntimeService.class);
        ModelInvocationDispatcher dispatcher = mock(ModelInvocationDispatcher.class);
        ModelInvocationContext context = context(7L, ModelScenario.SESSION_SUMMARY);
        when(runtimeService.resolveScenario(eq(7L), eq(ModelScenario.SESSION_SUMMARY), any())).thenReturn(context);
        when(dispatcher.call(eq(context), any())).thenReturn(response("压缩后的会话工作记忆"));
        AssistantMemorySummarizer summarizer = new AssistantMemorySummarizer(
                promptTemplate("existing={existingSessionMemory}\nmessages={newMessages}\nmode={currentToolMode}\ngroup={currentGroupId}"),
                promptTemplate("compact={existingCompactSummary}\nsession={sessionMemory}\nmessages={messagesToCompact}"),
                promptTemplate("compact={compactSummary}\nsession={sessionMemory}\nrecent={recentMessages}\nquestion={currentQuestion}"),
                runtimeService,
                dispatcher
        );

        String summary = summarizer.summarizeSessionMemory(
                7L,
                11L,
                "旧摘要",
                List.of(
                        buildMessage("USER", "我叫微明"),
                        buildMessage("ASSISTANT", "收到")
                ),
                AssistantToolMode.CHAT,
                null
        );

        assertThat(summary).isEqualTo("压缩后的会话工作记忆");
        assertThat(invokeFormatMessages(summarizer, List.of(
                buildMessage("USER", "我叫微明"),
                buildMessage("ASSISTANT", "收到")
        ))).contains("[USER] 我叫微明").contains("[ASSISTANT] 收到");
        verify(runtimeService).resolveScenario(eq(7L), eq(ModelScenario.SESSION_SUMMARY), any());
        verify(dispatcher).call(eq(context), any());
    }

    private AssistantMessageEntity buildMessage(String role, String content) {
        AssistantMessageEntity entity = new AssistantMessageEntity();
        entity.setRole(role);
        entity.setContent(content);
        entity.setCreatedAt(LocalDateTime.of(2026, 4, 21, 20, 0));
        return entity;
    }

    private String invokeFormatMessages(
            AssistantMemorySummarizer summarizer,
            List<AssistantMessageEntity> messages
    ) {
        try {
            Method method = AssistantMemorySummarizer.class.getDeclaredMethod("formatMessages", List.class);
            method.setAccessible(true);
            return (String) method.invoke(summarizer, messages);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("无法调用 formatMessages", exception);
        }
    }

    private PromptTemplate promptTemplate(String template) {
        return PromptTemplate.builder()
                .template(template)
                .build();
    }

    private ModelInvocationContext context(Long userId, ModelScenario scenario) {
        return new ModelInvocationContext(userId, scenario, 11L, 21L, 1L, ProviderType.DASHSCOPE,
                "qwen-plus", "internal", ConnectionOwnerType.PLATFORM, null, null, null, null, null);
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
