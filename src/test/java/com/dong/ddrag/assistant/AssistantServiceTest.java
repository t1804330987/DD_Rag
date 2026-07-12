package com.dong.ddrag.assistant;

import com.dong.ddrag.assistant.agent.AssistantAgentFacade;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryService;
import com.dong.ddrag.assistant.model.dto.chat.AssistantChatRequest;
import com.dong.ddrag.assistant.model.dto.message.AssistantMessageCreateDTO;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.assistant.model.vo.chat.AssistantAgentResult;
import com.dong.ddrag.assistant.model.vo.chat.AssistantChatResponse;
import com.dong.ddrag.assistant.model.vo.message.AssistantMessageVO;
import com.dong.ddrag.assistant.service.AssistantConversationService;
import com.dong.ddrag.assistant.service.AssistantService;
import com.dong.ddrag.assistant.service.AssistantTurnRequestService;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.groupmembership.service.GroupMembershipService;
import com.dong.ddrag.modelplatform.concurrency.AssistantTurnGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dong.ddrag.identity.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    @Mock
    private AssistantConversationService assistantConversationService;

    @Mock
    private AssistantAgentFacade assistantAgentFacade;

    @Mock
    private AssistantRuntimeMemoryService assistantRuntimeMemoryService;

    @Mock
    private GroupMembershipService groupMembershipService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private AssistantTurnRequestService assistantTurnRequestService;

    @Test
    void shouldCreateSessionAndPersistRequestBeforeCallingAgent() {
        AssistantService assistantService = createAssistantServiceWithTurnRequests();
        MockHttpServletRequest request = new MockHttpServletRequest();
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class)))
                .willReturn(new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户"));
        AssistantChatRequest input = new AssistantChatRequest(
                null, "  请   帮我  总结 \n 这段内容 ", AssistantToolMode.CHAT, null, "request-1001"
        );
        AssistantChatRequest prepared = new AssistantChatRequest(
                2001L, input.message(), AssistantToolMode.CHAT, null, "request-1001"
        );
        given(assistantTurnRequestService.prepare(1001L, input))
                .willReturn(AssistantTurnRequestService.PreparedTurn.accepted(
                        prepared, 3001L, "turn-1001"
                ));
        given(assistantRuntimeMemoryService.beforeAnswer(any(), any(), any(), any(), any(), any()))
                .willReturn(AssistantRuntimeMemoryService.AssistantRuntimeMemoryDecision.continueWith(input.message()));
        given(assistantAgentFacade.chat(1001L, 2001L, AssistantToolMode.CHAT, null, input.message(), "turn-1001", "request-1001", 3001L))
                .willReturn(AssistantAgentResult.withoutCitations("已完成"));
        given(assistantConversationService.saveAssistantMessage(any(), any())).willReturn(buildMessage(3002L, "已完成"));

        AssistantChatResponse response = assistantService.chat(request, input);

        assertThat(response.sessionId()).isEqualTo(2001L);
        assertThat(response.reply()).isEqualTo("已完成");
        then(assistantTurnRequestService).should().markCompleted(1001L, "request-1001", 3002L);
    }

    @Test
    void shouldReturnExistingCompletedResultWithoutCallingAgent() {
        AssistantService assistantService = createAssistantServiceWithTurnRequests();
        MockHttpServletRequest request = new MockHttpServletRequest();
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class)))
                .willReturn(new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户"));
        AssistantChatRequest input = new AssistantChatRequest(2001L, "你好", AssistantToolMode.CHAT, null, "request-1002");
        given(assistantTurnRequestService.prepare(1001L, input))
                .willReturn(AssistantTurnRequestService.PreparedTurn.completed(
                        2001L, 3002L, "已完成", AssistantToolMode.CHAT, null, "turn-1002"
                ));

        AssistantChatResponse response = assistantService.chat(request, input);

        assertThat(response.reply()).isEqualTo("已完成");
        assertThat(response.status()).isEqualTo("COMPLETED");
        then(assistantAgentFacade).shouldHaveNoInteractions();
        then(assistantConversationService).shouldHaveNoInteractions();
    }

    @Test
    void shouldRequireNewRequestIdAfterFailedTurn() {
        AssistantService assistantService = createAssistantServiceWithTurnRequests();
        MockHttpServletRequest request = new MockHttpServletRequest();
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class)))
                .willReturn(new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户"));
        AssistantChatRequest input = new AssistantChatRequest(2001L, "你好", AssistantToolMode.CHAT, null, "request-1003");
        given(assistantTurnRequestService.prepare(1001L, input))
                .willReturn(AssistantTurnRequestService.PreparedTurn.failed());

        assertThatThrownBy(() -> assistantService.chat(request, input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("REQUEST_RETRY_REQUIRES_NEW_ID");

        then(assistantAgentFacade).shouldHaveNoInteractions();
    }

    @Test
    void shouldReturnRunningTurnWithoutWritingDuplicateMessage() {
        AssistantService assistantService = createAssistantServiceWithTurnRequests();
        MockHttpServletRequest request = new MockHttpServletRequest();
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class)))
                .willReturn(new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户"));
        AssistantChatRequest input = new AssistantChatRequest(2001L, "你好", AssistantToolMode.CHAT, null, "request-1004");
        given(assistantTurnRequestService.prepare(1001L, input))
                .willReturn(AssistantTurnRequestService.PreparedTurn.running(2001L, "turn-1004"));

        AssistantChatResponse response = assistantService.chat(request, input);

        assertThat(response.sessionId()).isEqualTo(2001L);
        assertThat(response.status()).isEqualTo("RUNNING");
        assertThat(response.turnId()).isEqualTo("turn-1004");
        then(assistantAgentFacade).shouldHaveNoInteractions();
        then(assistantConversationService).shouldHaveNoInteractions();
    }

    @Test
    void shouldKeepUserMessageAndMarkRequestFailedWhenModelFails() {
        AssistantService assistantService = createAssistantServiceWithTurnRequests();
        MockHttpServletRequest request = new MockHttpServletRequest();
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class)))
                .willReturn(new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户"));
        AssistantChatRequest input = new AssistantChatRequest(2001L, "你好", AssistantToolMode.CHAT, null, "request-1005");
        given(assistantTurnRequestService.prepare(1001L, input))
                .willReturn(AssistantTurnRequestService.PreparedTurn.accepted(input, 3001L, "turn-1005"));
        given(assistantRuntimeMemoryService.beforeAnswer(any(), any(), any(), any(), any(), any()))
                .willReturn(AssistantRuntimeMemoryService.AssistantRuntimeMemoryDecision.continueWith("你好"));
        given(assistantAgentFacade.chat(1001L, 2001L, AssistantToolMode.CHAT, null, "你好", "turn-1005", "request-1005", 3001L))
                .willThrow(new BusinessException("PROVIDER_ERROR"));

        assertThatThrownBy(() -> assistantService.chat(request, input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("PROVIDER_ERROR");

        then(assistantConversationService).should(org.mockito.Mockito.never()).saveAssistantMessage(any(), any());
        then(assistantTurnRequestService).should().markFailed(1001L, "request-1005", "PROVIDER_ERROR");
    }

    @Test
    void shouldGuardAutoCreatedSessionBeforeInvokingModel() {
        AssistantTurnGuard turnGuard = new AssistantTurnGuard();
        AssistantService assistantService = createAssistantServiceWithTurnRequests(turnGuard);
        MockHttpServletRequest request = new MockHttpServletRequest();
        AssistantChatRequest input = new AssistantChatRequest(null, "你好", AssistantToolMode.CHAT, null, "request-new-session");
        AssistantChatRequest prepared = new AssistantChatRequest(2001L, "你好", AssistantToolMode.CHAT, null, "request-new-session");
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class)))
                .willReturn(new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户"));
        given(assistantTurnRequestService.prepare(1001L, input))
                .willReturn(AssistantTurnRequestService.PreparedTurn.accepted(prepared, 3001L, "turn-new-session"));
        given(assistantRuntimeMemoryService.beforeAnswer(any(), any(), any(), any(), any(), any()))
                .willReturn(AssistantRuntimeMemoryService.AssistantRuntimeMemoryDecision.continueWith("你好"));
        given(assistantAgentFacade.chat(1001L, 2001L, AssistantToolMode.CHAT, null, "你好",
                "turn-new-session", "request-new-session", 3001L))
                .willAnswer(invocation -> {
                    assertThatThrownBy(() -> turnGuard.acquire(2001L))
                            .isInstanceOf(BusinessException.class)
                            .hasMessage("SESSION_BUSY");
                    return AssistantAgentResult.withoutCitations("已完成");
                });
        given(assistantConversationService.saveAssistantMessage(any(), any())).willReturn(buildMessage(3002L, "已完成"));

        assistantService.chat(request, input);

        then(assistantTurnRequestService).should().markCompleted(1001L, "request-new-session", 3002L);
    }

    @Test
    void shouldCompleteChatModeFlow() {
        AssistantService assistantService = createAssistantService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class)))
                .willReturn(new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户"));
        given(assistantConversationService.saveUserMessage(any(), any()))
                .willReturn(buildMessage(3001L, "你好"));
        givenRuntimeMemoryContinues();
        given(assistantAgentFacade.chat(1001L, 2001L, AssistantToolMode.CHAT, null, "你好"))
                .willReturn(AssistantAgentResult.withoutCitations("我是个人智能助手"));
        given(assistantConversationService.saveAssistantMessage(any(), any()))
                .willReturn(buildMessage(3002L, "我是个人智能助手"));

        AssistantChatResponse response = assistantService.chat(request, new AssistantChatRequest(
                2001L,
                "你好",
                AssistantToolMode.CHAT,
                null
        ));

        assertThat(response.sessionId()).isEqualTo(2001L);
        assertThat(response.messageId()).isEqualTo(3002L);
        assertThat(response.reply()).isEqualTo("我是个人智能助手");
        assertThat(response.toolMode()).isEqualTo(AssistantToolMode.CHAT);
        assertThat(response.groupId()).isNull();

        ArgumentCaptor<AssistantMessageCreateDTO> userMessageCaptor = ArgumentCaptor.forClass(AssistantMessageCreateDTO.class);
        then(assistantConversationService).should().saveUserMessage(any(), userMessageCaptor.capture());
        assertThat(userMessageCaptor.getValue().content()).isEqualTo("你好");
        assertThat(userMessageCaptor.getValue().toolMode()).isEqualTo(AssistantToolMode.CHAT);
        assertThat(userMessageCaptor.getValue().groupId()).isNull();
    }

    @Test
    void shouldReturnConfirmationQuestionWithoutCallingAgent() {
        AssistantService assistantService = createAssistantService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class)))
                .willReturn(new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户"));
        given(assistantConversationService.saveUserMessage(any(), any()))
                .willReturn(buildMessage(3001L, "B 好像也可以，按这个写一版"));
        given(assistantRuntimeMemoryService.beforeAnswer(
                1001L,
                2001L,
                3001L,
                AssistantToolMode.CHAT,
                null,
                "B 好像也可以，按这个写一版"
        )).willReturn(AssistantRuntimeMemoryService.AssistantRuntimeMemoryDecision.askConfirmation(
                "你是想把论文选题改成方案 B 吗？"
        ));
        given(assistantConversationService.saveAssistantMessage(any(), any()))
                .willReturn(buildMessage(3002L, "你是想把论文选题改成方案 B 吗？"));

        AssistantChatResponse response = assistantService.chat(request, new AssistantChatRequest(
                2001L,
                "B 好像也可以，按这个写一版",
                AssistantToolMode.CHAT,
                null
        ));

        assertThat(response.reply()).isEqualTo("你是想把论文选题改成方案 B 吗？");
        then(assistantAgentFacade).shouldHaveNoInteractions();
    }

    @Test
    void shouldRejectKbSearchWithoutGroupId() {
        AssistantService assistantService = createAssistantService();
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class)))
                .willReturn(new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户"));

        assertThatThrownBy(() -> assistantService.chat(
                new MockHttpServletRequest(),
                new AssistantChatRequest(2001L, "测试", AssistantToolMode.KB_SEARCH, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("KB_SEARCH 模式必须传 groupId");
    }

    @Test
    void shouldRejectGroupIdForChatMode() {
        AssistantService assistantService = createAssistantService();

        assertThatThrownBy(() -> assistantService.chat(
                new MockHttpServletRequest(),
                new AssistantChatRequest(2001L, "测试", AssistantToolMode.CHAT, 3001L)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CHAT 模式不允许传 groupId");
    }

    private AssistantService createAssistantService() {
        return new AssistantService(
                assistantConversationService,
                assistantAgentFacade,
                assistantRuntimeMemoryService,
                groupMembershipService,
                currentUserService,
                new ObjectMapper()
        );
    }

    private AssistantService createAssistantServiceWithTurnRequests() {
        return createAssistantServiceWithTurnRequests(new AssistantTurnGuard());
    }

    private AssistantService createAssistantServiceWithTurnRequests(AssistantTurnGuard turnGuard) {
        return new AssistantService(
                assistantConversationService,
                assistantAgentFacade,
                assistantRuntimeMemoryService,
                groupMembershipService,
                currentUserService,
                new ObjectMapper(),
                assistantTurnRequestService,
                turnGuard
        );
    }

    private void givenRuntimeMemoryContinues() {
        given(assistantRuntimeMemoryService.beforeAnswer(any(), any(), any(), any(), any(), any()))
                .willAnswer(invocation -> AssistantRuntimeMemoryService.AssistantRuntimeMemoryDecision.continueWith(
                        invocation.getArgument(5)
                ));
    }

    private AssistantMessageVO buildMessage(Long messageId, String content) {
        return new AssistantMessageVO(
                messageId,
                2001L,
                com.dong.ddrag.assistant.model.enums.AssistantMessageRole.ASSISTANT,
                AssistantToolMode.CHAT,
                null,
                content,
                null,
                LocalDateTime.of(2026, 4, 21, 10, 0)
        );
    }
}
