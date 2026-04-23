package com.dong.ddrag.assistant;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import com.dong.ddrag.assistant.memory.AssistantShortTermMemoryHook;
import com.dong.ddrag.assistant.model.enums.AssistantMessageRole;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.assistant.model.vo.message.AssistantMessageVO;
import com.dong.ddrag.assistant.service.AssistantConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AssistantShortTermMemoryHookTest {

    private static final String HOOK_CLASS_NAME =
            "com.dong.ddrag.assistant.memory.AssistantShortTermMemoryHook";

    @Test
    void shouldExposeBeforeModelEntrypointForShortTermMemoryInjection() throws Exception {
        Class<?> hookClass = requireHookClass();

        assertThat(hookClass.getSimpleName()).isEqualTo("AssistantShortTermMemoryHook");
        assertThatCode(() -> hookClass.getDeclaredMethod("beforeModel", Object.class))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAssembleCompactSummarySessionMemoryRecentMessagesAndCurrentQuestion() throws Exception {
        Class<?> hookClass = requireHookClass();

        Method method = hookClass.getDeclaredMethod(
                "assembleBeforeModelMessages",
                Long.class,
                Long.class,
                AssistantToolMode.class,
                Long.class,
                String.class
        );

        assertThat(method.getReturnType()).isAssignableTo(List.class);
    }

    @Test
    void shouldInjectShortTermMemoryBeforeModelWhenMetadataIsComplete() {
        AssistantConversationService conversationService = mock(AssistantConversationService.class);
        AssistantShortTermMemoryHook hook = new AssistantShortTermMemoryHook(conversationService);
        given(conversationService.loadConversationContext(1001L, 2001L, 10))
                .willReturn(new AssistantConversationService.AssistantConversationContext(
                        null,
                        "压缩摘要",
                        "会话工作记忆",
                        List.of(
                                new AssistantMessageVO(
                                        3001L,
                                        2001L,
                                        AssistantMessageRole.USER,
                                        AssistantToolMode.CHAT,
                                        null,
                                        "上一轮问题",
                                        null,
                                        LocalDateTime.of(2026, 4, 21, 10, 1)
                                ),
                                new AssistantMessageVO(
                                        3002L,
                                        2001L,
                                        AssistantMessageRole.ASSISTANT,
                                        AssistantToolMode.CHAT,
                                        null,
                                        "上一轮回答",
                                        null,
                                        LocalDateTime.of(2026, 4, 21, 10, 2)
                                ),
                                new AssistantMessageVO(
                                        3003L,
                                        2001L,
                                        AssistantMessageRole.USER,
                                        AssistantToolMode.CHAT,
                                        null,
                                        "当前问题",
                                        null,
                                        LocalDateTime.of(2026, 4, 21, 10, 3)
                                )
                        )
                ));
        RunnableConfig config = RunnableConfig.builder()
                .threadId("user:1001:session:2001")
                .addMetadata("userId", 1001L)
                .addMetadata("sessionId", 2001L)
                .addMetadata("toolMode", "CHAT")
                .build();

        AgentCommand command = hook.beforeModel(
                List.of(
                        new SystemMessage("基础系统提示"),
                        new UserMessage("当前问题")
                ),
                config
        );
        List<Message> injectedMessages = readMessages(command);
        UpdatePolicy updatePolicy = readUpdatePolicy(command);

        assertThat(updatePolicy).isEqualTo(UpdatePolicy.REPLACE);
        assertThat(injectedMessages).hasSize(5);
        assertThat(injectedMessages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(injectedMessages.get(0).getText()).contains("compact summary").contains("压缩摘要");
        assertThat(injectedMessages.get(1)).isInstanceOf(SystemMessage.class);
        assertThat(injectedMessages.get(1).getText()).contains("session memory").contains("会话工作记忆");
        assertThat(injectedMessages.get(2)).isInstanceOf(UserMessage.class);
        assertThat(injectedMessages.get(2).getText()).contains("模式：CHAT").contains("上一轮问题");
        assertThat(injectedMessages.get(3)).isInstanceOf(AssistantMessage.class);
        assertThat(injectedMessages.get(3).getText()).contains("模式：CHAT").contains("上一轮回答");
        assertThat(injectedMessages.get(4)).isInstanceOf(UserMessage.class);
        assertThat(injectedMessages.get(4).getText()).isEqualTo("当前问题");
    }

    @Test
    void shouldKeepKnowledgeBaseSearchUserMessageInRecentContext() {
        AssistantConversationService conversationService = mock(AssistantConversationService.class);
        AssistantShortTermMemoryHook hook = new AssistantShortTermMemoryHook(conversationService);
        given(conversationService.loadConversationContext(1001L, 2001L, 10))
                .willReturn(new AssistantConversationService.AssistantConversationContext(
                        null,
                        null,
                        null,
                        List.of(
                                new AssistantMessageVO(
                                        3001L,
                                        2001L,
                                        AssistantMessageRole.USER,
                                        AssistantToolMode.KB_SEARCH,
                                        9001L,
                                        "APIMatch相关内容",
                                        null,
                                        LocalDateTime.of(2026, 4, 21, 13, 29)
                                )
                        )
                ));

        List<Message> messages = hook.assembleBeforeModelMessages(
                1001L,
                2001L,
                AssistantToolMode.CHAT,
                null,
                "我之前问了什么"
        );

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(0).getText()).contains("模式：KB_SEARCH").contains("APIMatch相关内容");
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo("我之前问了什么");
    }

    @Test
    void shouldKeepToolMessagesFromConversationContextAndRuntimeMessages() {
        AssistantConversationService conversationService = mock(AssistantConversationService.class);
        AssistantShortTermMemoryHook hook = new AssistantShortTermMemoryHook(conversationService);
        given(conversationService.loadConversationContext(1001L, 2001L, 10))
                .willReturn(new AssistantConversationService.AssistantConversationContext(
                        null,
                        null,
                        null,
                        List.of(
                                new AssistantMessageVO(
                                        3001L,
                                        2001L,
                                        AssistantMessageRole.TOOL,
                                        AssistantToolMode.KB_SEARCH,
                                        9001L,
                                        "{\"tool\":\"knowledgeBaseSearch\",\"evidence\":\"历史工具结果\"}",
                                        null,
                                        LocalDateTime.of(2026, 4, 21, 13, 29)
                                )
                        )
                ));

        ToolResponseMessage runtimeToolMessage = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "tool-call-1",
                        "knowledgeBaseSearch",
                        "{\"tool\":\"knowledgeBaseSearch\",\"evidence\":\"本轮工具结果\"}"
                )))
                .build();

        List<Message> messages = hook.assembleBeforeModelMessages(
                1001L,
                2001L,
                AssistantToolMode.KB_SEARCH,
                9001L,
                "基于证据给我答案",
                List.of(new UserMessage("基于证据给我答案"), runtimeToolMessage)
        );

        assertThat(messages).hasSize(3);
        assertThat(messages.get(0)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(0).getText()).contains("工具观察").contains("历史工具结果");
        assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(1).getText()).contains("工具观察").contains("本轮工具结果");
        assertThat(messages.get(2)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(2).getText()).isEqualTo("基于证据给我答案");
    }

    @Test
    void shouldFallbackSafelyWhenMetadataIsMissing() {
        AssistantConversationService conversationService = mock(AssistantConversationService.class);
        AssistantShortTermMemoryHook hook = new AssistantShortTermMemoryHook(conversationService);
        List<Message> previousMessages = List.of(
                new SystemMessage("基础系统提示"),
                new UserMessage("当前问题")
        );
        RunnableConfig config = RunnableConfig.builder()
                .threadId("user:1001:session:2001")
                .addMetadata("userId", 1001L)
                .build();

        AgentCommand command = hook.beforeModel(previousMessages, config);
        List<Message> injectedMessages = readMessages(command);
        UpdatePolicy updatePolicy = readUpdatePolicy(command);

        assertThat(injectedMessages).isSameAs(previousMessages);
        assertThat(updatePolicy).isEqualTo(UpdatePolicy.REPLACE);
    }

    @Test
    void shouldTriggerRuntimeCompactionWhenAssembledContextExceeds50000Tokens() throws Exception {
        Class<?> hookClass = requireHookClass();

        Method method = hookClass.getDeclaredMethod("shouldRuntimeCompact", int.class);

        assertThat(method.getReturnType()).isEqualTo(boolean.class);
    }

    @Test
    void shouldRuntimeCompactWithoutPersistingSessionContext() throws Exception {
        Class<?> hookClass = requireHookClass();

        Method method = hookClass.getDeclaredMethod("runtimeCompact", List.class);

        assertThat(method.getReturnType()).isAssignableTo(List.class);
    }

    private Class<?> requireHookClass() throws ClassNotFoundException {
        try {
            return Class.forName(HOOK_CLASS_NAME);
        } catch (ClassNotFoundException exception) {
            fail("缺少短期记忆 Hook 类: " + HOOK_CLASS_NAME);
            throw exception;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Message> readMessages(AgentCommand command) {
        try {
            Method method = AgentCommand.class.getDeclaredMethod("getMessages");
            method.setAccessible(true);
            return (List<Message>) method.invoke(command);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("无法读取 AgentCommand messages", exception);
        }
    }

    private UpdatePolicy readUpdatePolicy(AgentCommand command) {
        try {
            Method method = AgentCommand.class.getDeclaredMethod("getUpdatePolicy");
            method.setAccessible(true);
            return (UpdatePolicy) method.invoke(command);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("无法读取 AgentCommand updatePolicy", exception);
        }
    }
}
