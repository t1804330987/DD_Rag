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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AssistantServiceKbSearchTest {

    @Mock
    private AssistantConversationService assistantConversationService;

    @Mock
    private AssistantAgentFacade assistantAgentFacade;

    @Mock
    private GroupMembershipService groupMembershipService;

    @Mock
    private CurrentUserService currentUserService;

    @Test
    void shouldExecuteKbSearchWithPermissionCheck() {
        AssistantService assistantService = createAssistantService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class)))
                .willReturn(new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户"));
        given(assistantConversationService.saveUserMessage(any(), any()))
                .willReturn(buildMessage(3001L, "上传流程是什么", AssistantToolMode.KB_SEARCH, 2001L, null));
        given(assistantAgentFacade.chat(1001L, 2001L, AssistantToolMode.KB_SEARCH, 2001L, "上传流程是什么"))
                .willReturn(AssistantAgentResult.withoutCitations("这是 Agent 基于知识库证据生成的答案"));
        given(assistantConversationService.saveAssistantMessage(any(), any()))
                .willReturn(buildMessage(3002L, "这是 Agent 基于知识库证据生成的答案", AssistantToolMode.KB_SEARCH, 2001L, null));

        AssistantChatResponse response = assistantService.chat(request, new AssistantChatRequest(
                2001L,
                "上传流程是什么",
                AssistantToolMode.KB_SEARCH,
                2001L
        ));

        assertThat(response.reply()).isEqualTo("这是 Agent 基于知识库证据生成的答案");
        assertThat(response.toolMode()).isEqualTo(AssistantToolMode.KB_SEARCH);
        assertThat(response.groupId()).isEqualTo(2001L);
        then(groupMembershipService).should().requireGroupReadable(request, 2001L);
        then(assistantAgentFacade).should().chat(1001L, 2001L, AssistantToolMode.KB_SEARCH, 2001L, "上传流程是什么");

        ArgumentCaptor<AssistantMessageCreateDTO> assistantMessageCaptor = ArgumentCaptor.forClass(AssistantMessageCreateDTO.class);
        then(assistantConversationService).should().saveAssistantMessage(eq(1001L), assistantMessageCaptor.capture());
        assertThat(assistantMessageCaptor.getValue().structuredPayload()).isNull();
    }

    @Test
    void shouldRejectKbSearchWithoutGroupId() {
        AssistantService assistantService = createAssistantService();

        assertThatThrownBy(() -> assistantService.chat(
                new MockHttpServletRequest(),
                new AssistantChatRequest(2001L, "测试", AssistantToolMode.KB_SEARCH, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("KB_SEARCH 模式必须传 groupId");
    }

    @Test
    void shouldPropagatePermissionFailureBeforeCallingTool() {
        AssistantService assistantService = createAssistantService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class)))
                .willReturn(new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户"));
        given(assistantConversationService.saveUserMessage(any(), any()))
                .willReturn(buildMessage(3001L, "测试", AssistantToolMode.KB_SEARCH, 2001L, null));
        given(groupMembershipService.requireGroupReadable(request, 2001L))
                .willThrow(new BusinessException("当前用户不是目标群组成员"));

        assertThatThrownBy(() -> assistantService.chat(
                request,
                new AssistantChatRequest(2001L, "测试", AssistantToolMode.KB_SEARCH, 2001L)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前用户不是目标群组成员");

        then(assistantAgentFacade).shouldHaveNoInteractions();
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

    private AssistantMessageVO buildMessage(
            Long messageId,
            String content,
            AssistantToolMode toolMode,
            Long groupId,
            String structuredPayload
    ) {
        return new AssistantMessageVO(
                messageId,
                2001L,
                com.dong.ddrag.assistant.model.enums.AssistantMessageRole.ASSISTANT,
                toolMode,
                groupId,
                content,
                structuredPayload,
                LocalDateTime.of(2026, 4, 21, 10, 0)
        );
    }
}
