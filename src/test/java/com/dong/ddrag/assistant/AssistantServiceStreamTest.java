package com.dong.ddrag.assistant;

import com.dong.ddrag.assistant.agent.AssistantAgentFacade;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryService;
import com.dong.ddrag.assistant.model.dto.chat.AssistantChatRequest;
import com.dong.ddrag.assistant.model.dto.message.AssistantMessageCreateDTO;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.assistant.model.vo.chat.AssistantAgentResult;
import com.dong.ddrag.assistant.model.vo.chat.AssistantChatStreamEvent;
import com.dong.ddrag.assistant.model.vo.message.AssistantMessageVO;
import com.dong.ddrag.assistant.service.AssistantConversationService;
import com.dong.ddrag.assistant.service.AssistantService;
import com.dong.ddrag.groupmembership.service.GroupMembershipService;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.modelplatform.runtime.ModelCallCancellation;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AssistantServiceStreamTest {

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

    @Test
    void shouldStreamChatModeAndPersistFinalMessage() {
        AssistantService assistantService = createAssistantService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        List<AssistantChatStreamEvent> events = new ArrayList<>();
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class)))
                .willReturn(new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户"));
        given(assistantConversationService.saveUserMessage(any(), any()))
                .willReturn(buildMessage(3001L, "你好"));
        givenRuntimeMemoryContinues();
        given(assistantConversationService.saveAssistantMessage(any(), any()))
                .willReturn(buildMessage(3002L, "我是个人智能助手"));

        assistantService.streamChat(
                request,
                new AssistantChatRequest(2001L, "你好", AssistantToolMode.CHAT, null),
                events::add,
                (deltaEmitter, effectiveUserMessage) -> {
                    assertThat(effectiveUserMessage).isEqualTo("你好");
                    then(assistantConversationService).should().saveUserMessage(any(), any());
                    then(assistantConversationService).should(never()).saveAssistantMessage(any(), any());
                    deltaEmitter.accept("我是");
                    deltaEmitter.accept("个人智能助手");
                    return AssistantAgentResult.withoutCitations("我是个人智能助手");
                }
        );

        assertThat(events).extracting(AssistantChatStreamEvent::event)
                .containsExactly("start", "delta", "delta", "done");
        assertThat(events.get(1).delta()).isEqualTo("我是");
        assertThat(events.get(2).delta()).isEqualTo("个人智能助手");
        assertThat(events.get(3).reply()).isEqualTo("我是个人智能助手");
        assertThat(events.get(3).messageId()).isEqualTo(3002L);

        ArgumentCaptor<AssistantMessageCreateDTO> assistantMessageCaptor =
                ArgumentCaptor.forClass(AssistantMessageCreateDTO.class);
        then(assistantConversationService).should().saveAssistantMessage(any(), assistantMessageCaptor.capture());
        assertThat(assistantMessageCaptor.getValue().content()).isEqualTo("我是个人智能助手");
    }

    @Test
    void shouldStreamKbSearchAfterToolResponse() {
        AssistantService assistantService = createAssistantService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        List<AssistantChatStreamEvent> events = new ArrayList<>();
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class)))
                .willReturn(new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户"));
        given(assistantConversationService.saveUserMessage(any(), any()))
                .willReturn(buildMessage(3001L, "知识库问题"));
        givenRuntimeMemoryContinues();
        given(assistantConversationService.saveAssistantMessage(any(), any()))
                .willReturn(new AssistantMessageVO(
                        3002L,
                        2001L,
                        com.dong.ddrag.assistant.model.enums.AssistantMessageRole.ASSISTANT,
                        AssistantToolMode.KB_SEARCH,
                        10L,
                        "最终回答",
                        "{\"answered\":true}",
                        LocalDateTime.of(2026, 4, 21, 10, 0)
                ));

        assistantService.streamChat(
                request,
                new AssistantChatRequest(2001L, "知识库问题", AssistantToolMode.KB_SEARCH, 10L),
                events::add,
                (deltaEmitter, effectiveUserMessage) -> {
                    assertThat(effectiveUserMessage).isEqualTo("知识库问题");
                    deltaEmitter.accept("最终回答");
                    return AssistantAgentResult.withoutCitations("最终回答");
                }
        );

        then(groupMembershipService).should().requireGroupReadable(request, 10L);
        then(assistantAgentFacade).shouldHaveNoInteractions();
        assertThat(events).extracting(AssistantChatStreamEvent::event)
                .containsExactly("start", "delta", "done");
        assertThat(events.get(2).citations()).isEmpty();
    }

    @Test
    void shouldStreamConfirmationQuestionWithoutCallingAgent() {
        AssistantService assistantService = createAssistantService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        List<AssistantChatStreamEvent> events = new ArrayList<>();
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class)))
                .willReturn(new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户"));
        given(assistantConversationService.saveUserMessage(any(), any()))
                .willReturn(buildMessage(3001L, "按这个写"));
        given(assistantRuntimeMemoryService.beforeAnswer(
                1001L,
                2001L,
                3001L,
                AssistantToolMode.CHAT,
                null,
                "按这个写"
        )).willReturn(AssistantRuntimeMemoryService.AssistantRuntimeMemoryDecision.askConfirmation("你是想改成方案 B 吗？"));
        given(assistantConversationService.saveAssistantMessage(any(), any()))
                .willReturn(buildMessage(3002L, "你是想改成方案 B 吗？"));

        assistantService.streamChat(
                request,
                new AssistantChatRequest(2001L, "按这个写", AssistantToolMode.CHAT, null),
                events::add,
                (deltaEmitter, effectiveUserMessage) -> {
                    throw new AssertionError("confirmation branch should not call stream executor");
                }
        );

        assertThat(events).extracting(AssistantChatStreamEvent::event)
                .containsExactly("start", "delta", "done");
        assertThat(events.get(1).delta()).isEqualTo("你是想改成方案 B 吗？");
        then(assistantAgentFacade).shouldHaveNoInteractions();
    }

    @Test
    void shouldNotPersistAssistantMessageAfterCancellationRequest() {
        AssistantService assistantService = createAssistantService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        ModelCallCancellation cancellation = new ModelCallCancellation();
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class)))
                .willReturn(new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户"));
        given(assistantConversationService.saveUserMessage(any(), any()))
                .willReturn(buildMessage(3001L, "你好"));
        givenRuntimeMemoryContinues();

        assertThatThrownBy(() -> assistantService.streamChat(
                request,
                new AssistantChatRequest(2001L, "你好", AssistantToolMode.CHAT, null),
                event -> { },
                cancellation,
                (deltaEmitter, message) -> {
                    cancellation.request();
                    return AssistantAgentResult.withoutCitations("迟到回复");
                }
        )).isInstanceOf(BusinessException.class).hasMessage("CALL_CANCELLED");

        then(assistantConversationService).should(never()).saveAssistantMessage(any(), any());
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
