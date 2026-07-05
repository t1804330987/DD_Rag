package com.dong.ddrag.harness.assistant;

import com.dong.ddrag.assistant.agent.AssistantAgentFacade;
import com.dong.ddrag.assistant.mapper.AssistantMessageMapper;
import com.dong.ddrag.assistant.mapper.AssistantSessionContextMapper;
import com.dong.ddrag.assistant.memory.AssistantShortTermMemoryHook;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryAction;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryChange;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryExtractor;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryService;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryState;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryStateApplier;
import com.dong.ddrag.assistant.model.dto.chat.AssistantChatRequest;
import com.dong.ddrag.assistant.model.dto.message.AssistantMessageCreateDTO;
import com.dong.ddrag.assistant.model.entity.AssistantSessionContextEntity;
import com.dong.ddrag.assistant.model.enums.AssistantMessageRole;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.assistant.model.vo.chat.AssistantAgentResult;
import com.dong.ddrag.assistant.model.vo.chat.AssistantChatResponse;
import com.dong.ddrag.assistant.model.vo.message.AssistantMessageVO;
import com.dong.ddrag.assistant.service.AssistantConversationService;
import com.dong.ddrag.assistant.service.AssistantService;
import com.dong.ddrag.groupmembership.service.GroupMembershipService;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class AssistantRuntimeMemoryHarnessTest {

    private static final Long USER_ID = 1001L;
    private static final Long SESSION_ID = 2001L;
    private static final Long USER_MESSAGE_ID = 3002L;
    private static final long NOW = 10_000L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void explicitReplacementUpdatesRuntimeMemoryAndKeepsCurrentRequest() throws Exception {
        HarnessRuntime runtime = createRuntime();
        given(runtime.contextMapper.selectBySessionId(SESSION_ID))
                .willReturn(contextWithState(1L, stateWithConclusion("方案 A")));
        given(runtime.extractor.extract(any(), any(), eq("把论文选题改成方案 B")))
                .willReturn(List.of(new AssistantRuntimeMemoryChange(
                        AssistantRuntimeMemoryAction.REPLACE,
                        "rm_1",
                        null,
                        "方案 B",
                        "用户明确要求改成方案 B",
                        null
                )));
        given(runtime.contextMapper.updateShortTermMemoryWithVersion(any(), eq(1L))).willReturn(1);

        AssistantRuntimeMemoryService.AssistantRuntimeMemoryDecision decision = runtime.service.beforeAnswer(
                USER_ID,
                SESSION_ID,
                USER_MESSAGE_ID,
                AssistantToolMode.CHAT,
                null,
                "把论文选题改成方案 B"
        );

        assertThat(decision.requiresConfirmation()).isFalse();
        assertThat(decision.effectiveUserMessage()).isEqualTo("把论文选题改成方案 B");
        AssistantRuntimeMemoryState saved = captureRuntimeState(runtime.contextMapper);
        assertThat(saved.pending()).isNull();
        assertThat(saved.conclusions().getFirst().activeValue()).isEqualTo("方案 B");
    }

    @Test
    void ambiguousReplacementAsksConfirmationAndDoesNotCallAgent() throws Exception {
        HarnessRuntime runtime = createRuntime();
        AssistantConversationService conversationService = mock(AssistantConversationService.class);
        AssistantAgentFacade agentFacade = mock(AssistantAgentFacade.class);
        AssistantService assistantService = createAssistantService(runtime.service, conversationService, agentFacade);
        givenSavedUserAndAssistantMessages(conversationService, "B 好像也可以，按这个写一版", "你是想把论文选题从方案 A 改成方案 B 吗？");
        given(runtime.contextMapper.selectBySessionId(SESSION_ID))
                .willReturn(contextWithState(1L, stateWithConclusion("方案 A")));
        given(runtime.extractor.extract(any(), any(), eq("B 好像也可以，按这个写一版")))
                .willReturn(List.of(new AssistantRuntimeMemoryChange(
                        AssistantRuntimeMemoryAction.ASK_CONFIRMATION,
                        "rm_1",
                        "论文选题",
                        "方案 B",
                        "表达模糊",
                        "你是想把论文选题从方案 A 改成方案 B 吗？"
                )));
        given(runtime.contextMapper.updateShortTermMemoryWithVersion(any(), eq(1L))).willReturn(1);

        AssistantChatResponse response = assistantService.chat(new MockHttpServletRequest(), new AssistantChatRequest(
                SESSION_ID,
                "B 好像也可以，按这个写一版",
                AssistantToolMode.CHAT,
                null
        ));

        assertThat(response.reply()).isEqualTo("你是想把论文选题从方案 A 改成方案 B 吗？");
        then(agentFacade).shouldHaveNoInteractions();
        AssistantRuntimeMemoryState saved = captureRuntimeState(runtime.contextMapper);
        assertThat(saved.pending()).isNotNull();
        assertThat(saved.conclusions().getFirst().activeValue()).isEqualTo("方案 A");
    }

    @Test
    void confirmationAppliesPendingReplacementAndResumesOriginalRequest() throws Exception {
        HarnessRuntime runtime = createRuntime();
        AssistantConversationService conversationService = mock(AssistantConversationService.class);
        AssistantAgentFacade agentFacade = mock(AssistantAgentFacade.class);
        AssistantService assistantService = createAssistantService(runtime.service, conversationService, agentFacade);
        AssistantRuntimeMemoryState pendingState = pendingReplacementState();
        givenSavedUserAndAssistantMessages(conversationService, "是的", "按方案 B 继续处理");
        given(runtime.contextMapper.selectBySessionId(SESSION_ID))
                .willReturn(contextWithState(1L, pendingState));
        given(runtime.contextMapper.updateShortTermMemoryWithVersion(any(), eq(1L))).willReturn(1);
        given(agentFacade.chat(USER_ID, SESSION_ID, AssistantToolMode.CHAT, null, "B 好像也可以，按这个写一版"))
                .willReturn(AssistantAgentResult.withoutCitations("按方案 B 继续处理"));

        AssistantChatResponse response = assistantService.chat(new MockHttpServletRequest(), new AssistantChatRequest(
                SESSION_ID,
                "是的",
                AssistantToolMode.CHAT,
                null
        ));

        assertThat(response.reply()).isEqualTo("按方案 B 继续处理");
        then(agentFacade).should().chat(USER_ID, SESSION_ID, AssistantToolMode.CHAT, null, "B 好像也可以，按这个写一版");
        AssistantRuntimeMemoryState saved = captureRuntimeState(runtime.contextMapper);
        assertThat(saved.pending()).isNull();
        assertThat(saved.conclusions().getFirst().activeValue()).isEqualTo("方案 B");
    }

    @Test
    void rejectionClearsPendingWithoutOverwritingActiveMemory() throws Exception {
        HarnessRuntime runtime = createRuntime();
        given(runtime.contextMapper.selectBySessionId(SESSION_ID))
                .willReturn(contextWithState(1L, pendingReplacementState()));
        given(runtime.contextMapper.updateShortTermMemoryWithVersion(any(), eq(1L))).willReturn(1);

        AssistantRuntimeMemoryService.AssistantRuntimeMemoryDecision decision = runtime.service.beforeAnswer(
                USER_ID,
                SESSION_ID,
                USER_MESSAGE_ID,
                AssistantToolMode.CHAT,
                null,
                "不确认"
        );

        assertThat(decision.requiresConfirmation()).isFalse();
        assertThat(decision.effectiveUserMessage()).isEqualTo("不确认");
        AssistantRuntimeMemoryState saved = captureRuntimeState(runtime.contextMapper);
        assertThat(saved.pending()).isNull();
        assertThat(saved.conclusions().getFirst().activeValue()).isEqualTo("方案 A");
        then(runtime.extractor).shouldHaveNoInteractions();
    }

    @Test
    void runtimeMemoryIsInjectedBeforeStaleSummaryMemory() {
        AssistantConversationService conversationService = mock(AssistantConversationService.class);
        AssistantShortTermMemoryHook hook = new AssistantShortTermMemoryHook(conversationService);
        given(conversationService.loadConversationContext(USER_ID, SESSION_ID, 10))
                .willReturn(new AssistantConversationService.AssistantConversationContext(
                        "runtime memory\n论文选题 = 方案 B",
                        "压缩摘要里仍然说方案 A",
                        "会话工作记忆",
                        List.of()
                ));

        List<Message> messages = hook.assembleBeforeModelMessages(
                USER_ID,
                SESSION_ID,
                AssistantToolMode.CHAT,
                null,
                "继续写"
        );

        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(0).getText()).contains("runtime memory").contains("方案 B");
        assertThat(messages.get(1).getText()).contains("compact summary").contains("方案 A");
        assertThat(messages.get(2).getText()).contains("session memory");
    }

    private HarnessRuntime createRuntime() {
        AssistantSessionContextMapper contextMapper = mock(AssistantSessionContextMapper.class);
        AssistantMessageMapper messageMapper = mock(AssistantMessageMapper.class);
        AssistantRuntimeMemoryExtractor extractor = mock(AssistantRuntimeMemoryExtractor.class);
        AssistantRuntimeMemoryService service = new AssistantRuntimeMemoryService(
                contextMapper,
                messageMapper,
                extractor,
                new AssistantRuntimeMemoryStateApplier(),
                objectMapper,
                Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)
        );
        return new HarnessRuntime(service, contextMapper, extractor);
    }

    private AssistantService createAssistantService(
            AssistantRuntimeMemoryService runtimeMemoryService,
            AssistantConversationService conversationService,
            AssistantAgentFacade agentFacade
    ) {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class)))
                .willReturn(new CurrentUserService.CurrentUser(USER_ID, "u1001", "测试用户"));
        return new AssistantService(
                conversationService,
                agentFacade,
                runtimeMemoryService,
                mock(GroupMembershipService.class),
                currentUserService,
                objectMapper
        );
    }

    private void givenSavedUserAndAssistantMessages(
            AssistantConversationService conversationService,
            String userMessage,
            String assistantMessage
    ) {
        given(conversationService.saveUserMessage(eq(USER_ID), any(AssistantMessageCreateDTO.class)))
                .willReturn(message(USER_MESSAGE_ID, AssistantMessageRole.USER, userMessage));
        given(conversationService.saveAssistantMessage(eq(USER_ID), any(AssistantMessageCreateDTO.class)))
                .willReturn(message(3003L, AssistantMessageRole.ASSISTANT, assistantMessage));
    }

    private AssistantMessageVO message(Long messageId, AssistantMessageRole role, String content) {
        return new AssistantMessageVO(
                messageId,
                SESSION_ID,
                role,
                AssistantToolMode.CHAT,
                null,
                content,
                null,
                LocalDateTime.of(2026, 5, 22, 10, 0)
        );
    }

    private AssistantSessionContextEntity contextWithState(Long contextVersion, AssistantRuntimeMemoryState state) throws Exception {
        AssistantSessionContextEntity entity = new AssistantSessionContextEntity();
        entity.setSessionId(SESSION_ID);
        entity.setContextVersion(contextVersion);
        entity.setRuntimeMemoryState(objectMapper.writeValueAsString(state));
        return entity;
    }

    private AssistantRuntimeMemoryState stateWithConclusion(String value) {
        return new AssistantRuntimeMemoryState(
                1L,
                List.of(new AssistantRuntimeMemoryState.Conclusion(
                        "rm_1",
                        "论文选题",
                        value,
                        3001L,
                        1_000L,
                        List.of()
                )),
                null
        );
    }

    private AssistantRuntimeMemoryState pendingReplacementState() {
        return new AssistantRuntimeMemoryState(
                2L,
                stateWithConclusion("方案 A").conclusions(),
                new AssistantRuntimeMemoryState.Pending(
                        AssistantRuntimeMemoryAction.ASK_CONFIRMATION,
                        "rm_1",
                        "论文选题",
                        "方案 B",
                        USER_MESSAGE_ID,
                        "B 好像也可以，按这个写一版",
                        "你是想把论文选题从方案 A 改成方案 B 吗？",
                        1_000L
                )
        );
    }

    private AssistantRuntimeMemoryState captureRuntimeState(AssistantSessionContextMapper contextMapper) throws Exception {
        org.mockito.ArgumentCaptor<AssistantSessionContextEntity> captor =
                org.mockito.ArgumentCaptor.forClass(AssistantSessionContextEntity.class);
        then(contextMapper).should().updateShortTermMemoryWithVersion(captor.capture(), any());
        return objectMapper.readValue(captor.getValue().getRuntimeMemoryState(), AssistantRuntimeMemoryState.class);
    }

    private record HarnessRuntime(
            AssistantRuntimeMemoryService service,
            AssistantSessionContextMapper contextMapper,
            AssistantRuntimeMemoryExtractor extractor
    ) {
    }
}
