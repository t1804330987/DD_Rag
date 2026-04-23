package com.dong.ddrag.assistant;

import com.dong.ddrag.assistant.agent.AssistantAgentFacade;
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
        given(assistantConversationService.saveAssistantMessage(any(), any()))
                .willReturn(buildMessage(3002L, "我是个人智能助手"));

        assistantService.streamChat(
                request,
                new AssistantChatRequest(2001L, "你好", AssistantToolMode.CHAT, null),
                events::add,
                (deltaEmitter) -> {
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
                (deltaEmitter) -> {
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

    private AssistantService createAssistantService() {
        return new AssistantService(
                assistantConversationService,
                assistantAgentFacade,
                groupMembershipService,
                currentUserService,
                new ObjectMapper()
        );
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
