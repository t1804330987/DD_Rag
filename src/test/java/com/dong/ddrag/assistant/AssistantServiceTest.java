package com.dong.ddrag.assistant;

import com.dong.ddrag.assistant.agent.AssistantAgentFacade;
import com.dong.ddrag.assistant.model.dto.chat.AssistantChatRequest;
import com.dong.ddrag.assistant.model.dto.message.AssistantMessageCreateDTO;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.assistant.model.vo.chat.AssistantAgentResult;
import com.dong.ddrag.assistant.model.vo.chat.AssistantChatResponse;
import com.dong.ddrag.assistant.model.vo.message.AssistantMessageVO;
import com.dong.ddrag.assistant.service.AssistantConversationService;
import com.dong.ddrag.assistant.service.AssistantService;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.groupmembership.service.GroupMembershipService;
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
    private GroupMembershipService groupMembershipService;

    @Mock
    private CurrentUserService currentUserService;

    @Test
    void shouldCompleteChatModeFlow() {
        AssistantService assistantService = createAssistantService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class)))
                .willReturn(new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户"));
        given(assistantConversationService.saveUserMessage(any(), any()))
                .willReturn(buildMessage(3001L, "你好"));
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
