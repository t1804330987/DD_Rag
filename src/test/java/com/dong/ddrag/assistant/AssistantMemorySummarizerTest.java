package com.dong.ddrag.assistant;

import com.dong.ddrag.assistant.memory.AssistantMemorySummarizer;
import com.dong.ddrag.assistant.model.entity.AssistantMessageEntity;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantMemorySummarizerTest {

    @Test
    void shouldCallModelWithFormattedMessageStringForSessionMemory() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class);
        CallResponseSpec callResponseSpec = mock(CallResponseSpec.class);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("压缩后的会话工作记忆");
        AssistantMemorySummarizer summarizer = new AssistantMemorySummarizer(
                builder,
                promptTemplate("existing={existingSessionMemory}\nmessages={newMessages}\nmode={currentToolMode}\ngroup={currentGroupId}"),
                promptTemplate("compact={existingCompactSummary}\nsession={sessionMemory}\nmessages={messagesToCompact}"),
                promptTemplate("compact={compactSummary}\nsession={sessionMemory}\nrecent={recentMessages}\nquestion={currentQuestion}")
        );

        String summary = summarizer.summarizeSessionMemory(
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
}
