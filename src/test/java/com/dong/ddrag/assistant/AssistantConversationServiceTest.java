package com.dong.ddrag.assistant;

import com.dong.ddrag.assistant.mapper.AssistantMessageMapper;
import com.dong.ddrag.assistant.mapper.AssistantSessionContextMapper;
import com.dong.ddrag.assistant.mapper.AssistantSessionMapper;
import com.dong.ddrag.assistant.memory.AssistantShortTermMemoryMaintenanceService;
import com.dong.ddrag.assistant.memory.AssistantSessionSummaryService;
import com.dong.ddrag.assistant.model.dto.message.AssistantMessageCreateDTO;
import com.dong.ddrag.assistant.model.entity.AssistantMessageEntity;
import com.dong.ddrag.assistant.model.entity.AssistantSessionContextEntity;
import com.dong.ddrag.assistant.model.entity.AssistantSessionEntity;
import com.dong.ddrag.assistant.model.enums.AssistantMessageRole;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.assistant.model.vo.conversation.AssistantConversationContextVO;
import com.dong.ddrag.assistant.model.vo.message.AssistantMessageVO;
import com.dong.ddrag.assistant.service.AssistantConversationService;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.identity.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class AssistantConversationServiceTest {

    @Mock
    private AssistantMessageMapper assistantMessageMapper;

    @Mock
    private AssistantSessionContextMapper assistantSessionContextMapper;

    @Mock
    private AssistantSessionMapper assistantSessionMapper;

    @Mock
    private AssistantSessionSummaryService assistantSessionSummaryService;

    @Mock
    private AssistantShortTermMemoryMaintenanceService assistantShortTermMemoryMaintenanceService;

    @Mock
    private CurrentUserService currentUserService;

    @Test
    void shouldDropGroupIdForChatModeUserMessage() {
        AssistantConversationService service = createService();
        given(assistantSessionMapper.selectByIdAndUserId(2001L, 1001L)).willReturn(buildSession(2001L, 1001L));
        given(assistantSessionMapper.updateLastMessageAt(any(), any(), any())).willReturn(1);
        given(assistantMessageMapper.insert(any(AssistantMessageEntity.class))).willAnswer(invocation -> {
            AssistantMessageEntity entity = invocation.getArgument(0);
            entity.setId(3001L);
            return 1;
        });

        AssistantMessageVO message = service.saveUserMessage(
                1001L,
                new AssistantMessageCreateDTO(
                        2001L,
                        AssistantToolMode.CHAT,
                        9001L,
                        "你好",
                        "{\"intent\":\"chat\"}"
                )
        );

        ArgumentCaptor<AssistantMessageEntity> entityCaptor = ArgumentCaptor.forClass(AssistantMessageEntity.class);
        then(assistantMessageMapper).should().insert(entityCaptor.capture());
        AssistantMessageEntity persisted = entityCaptor.getValue();
        assertThat(persisted.getRole()).isEqualTo(AssistantMessageRole.USER.name());
        assertThat(persisted.getToolMode()).isEqualTo(AssistantToolMode.CHAT.name());
        assertThat(persisted.getGroupId()).isNull();
        assertThat(message.groupId()).isNull();
    }

    @Test
    void shouldPersistGroupIdForKbSearchAssistantMessage() {
        AssistantConversationService service = createService();
        given(assistantSessionMapper.selectByIdAndUserId(2001L, 1001L)).willReturn(buildSession(2001L, 1001L));
        given(assistantSessionMapper.updateLastMessageAt(any(), any(), any())).willReturn(1);
        given(assistantMessageMapper.insert(any(AssistantMessageEntity.class))).willAnswer(invocation -> {
            AssistantMessageEntity entity = invocation.getArgument(0);
            entity.setId(3002L);
            return 1;
        });

        AssistantMessageVO message = service.saveAssistantMessage(
                1001L,
                new AssistantMessageCreateDTO(
                        2001L,
                        AssistantToolMode.KB_SEARCH,
                        9002L,
                        "这里是知识库答案",
                        "{\"citations\":[]}"
                )
        );

        ArgumentCaptor<AssistantMessageEntity> entityCaptor = ArgumentCaptor.forClass(AssistantMessageEntity.class);
        then(assistantMessageMapper).should().insert(entityCaptor.capture());
        AssistantMessageEntity persisted = entityCaptor.getValue();
        assertThat(persisted.getRole()).isEqualTo(AssistantMessageRole.ASSISTANT.name());
        assertThat(persisted.getToolMode()).isEqualTo(AssistantToolMode.KB_SEARCH.name());
        assertThat(persisted.getGroupId()).isEqualTo(9002L);
        assertThat(message.groupId()).isEqualTo(9002L);
    }

    @Test
    void shouldLoadRecentMessagesInChronologicalOrder() {
        AssistantConversationService service = createService();
        given(assistantSessionMapper.selectByIdAndUserId(2001L, 1001L)).willReturn(buildSession(2001L, 1001L));
        given(assistantMessageMapper.selectRecentBySessionId(2001L, 3)).willReturn(List.of(
                buildMessage(3003L, AssistantMessageRole.ASSISTANT, AssistantToolMode.KB_SEARCH, 9002L, "第三条", LocalDateTime.of(2026, 4, 21, 10, 3)),
                buildMessage(3002L, AssistantMessageRole.USER, AssistantToolMode.CHAT, null, "第二条", LocalDateTime.of(2026, 4, 21, 10, 2)),
                buildMessage(3001L, AssistantMessageRole.USER, AssistantToolMode.CHAT, null, "第一条", LocalDateTime.of(2026, 4, 21, 10, 1))
        ));

        List<AssistantMessageVO> messages = service.loadRecentMessages(1001L, 2001L, 3);

        assertThat(messages)
                .extracting(AssistantMessageVO::messageId)
                .containsExactly(3001L, 3002L, 3003L);
        assertThat(messages)
                .extracting(AssistantMessageVO::content)
                .containsExactly("第一条", "第二条", "第三条");
    }

    @Test
    void shouldRejectInvalidStructuredPayload() {
        AssistantConversationService service = createService();
        given(assistantSessionMapper.selectByIdAndUserId(2001L, 1001L)).willReturn(buildSession(2001L, 1001L));

        assertThatThrownBy(() -> service.saveUserMessage(
                1001L,
                new AssistantMessageCreateDTO(2001L, AssistantToolMode.CHAT, null, "你好", "{bad json}")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("structuredPayload 非法");
    }

    @Test
    void shouldLoadConversationContextForCurrentUserRequest() {
        AssistantConversationService service = createService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        given(currentUserService.requireBusinessUser(request))
                .willReturn(new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户"));
        given(assistantSessionMapper.selectByIdAndUserId(2001L, 1001L)).willReturn(buildSession(2001L, 1001L));
        AssistantSessionContextEntity contextEntity = new AssistantSessionContextEntity();
        contextEntity.setSessionId(2001L);
        contextEntity.setCompactSummary("压缩摘要");
        contextEntity.setSessionMemory("会话记忆");
        given(assistantSessionContextMapper.selectBySessionId(2001L)).willReturn(contextEntity);
        given(assistantMessageMapper.selectRecentBySessionId(2001L, 2)).willReturn(List.of(
                buildMessage(3002L, AssistantMessageRole.ASSISTANT, AssistantToolMode.CHAT, null, "第二条", LocalDateTime.of(2026, 4, 21, 10, 2)),
                buildMessage(3001L, AssistantMessageRole.USER, AssistantToolMode.CHAT, null, "第一条", LocalDateTime.of(2026, 4, 21, 10, 1))
        ));
        given(assistantSessionSummaryService.loadReusableSummary(2001L, null)).willReturn("已有摘要");

        AssistantConversationContextVO context = service.getConversationContext(request, 2001L, 2);

        assertThat(context.summaryText()).isEqualTo("已有摘要");
        assertThat(context.recentMessages()).hasSize(2);
        assertThat(context.recentMessages()).extracting(AssistantMessageVO::content).containsExactly("第一条", "第二条");
    }

    @Test
    void shouldLoadCompactSummaryAndSessionMemoryIntoConversationContext() {
        AssistantConversationService service = createService();
        given(assistantSessionMapper.selectByIdAndUserId(2001L, 1001L)).willReturn(buildSession(2001L, 1001L));
        AssistantSessionContextEntity contextEntity = new AssistantSessionContextEntity();
        contextEntity.setSessionId(2001L);
        contextEntity.setCompactSummary("压缩后的旧历史");
        contextEntity.setSessionMemory("当前会话主线");
        given(assistantSessionContextMapper.selectBySessionId(2001L)).willReturn(contextEntity);
        given(assistantMessageMapper.selectRecentBySessionId(2001L, 2)).willReturn(List.of(
                buildMessage(3002L, AssistantMessageRole.ASSISTANT, AssistantToolMode.CHAT, null, "第二条", LocalDateTime.of(2026, 4, 21, 10, 2)),
                buildMessage(3001L, AssistantMessageRole.USER, AssistantToolMode.CHAT, null, "第一条", LocalDateTime.of(2026, 4, 21, 10, 1))
        ));
        given(assistantSessionSummaryService.loadReusableSummary(2001L, null)).willReturn(null);
        given(assistantMessageMapper.countBySessionId(2001L)).willReturn(2L);
        given(assistantMessageMapper.selectBySessionIdOrderByCreatedAt(2001L)).willReturn(List.of(
                buildMessage(3001L, AssistantMessageRole.USER, AssistantToolMode.CHAT, null, "第一条", LocalDateTime.of(2026, 4, 21, 10, 1)),
                buildMessage(3002L, AssistantMessageRole.ASSISTANT, AssistantToolMode.CHAT, null, "第二条", LocalDateTime.of(2026, 4, 21, 10, 2))
        ));

        AssistantConversationService.AssistantConversationContext context =
                service.loadConversationContext(1001L, 2001L, 2);

        assertThat(context.compactSummary()).isEqualTo("压缩后的旧历史");
        assertThat(context.sessionMemory()).isEqualTo("当前会话主线");
        assertThat(context.recentMessages()).hasSize(2);
    }

    private AssistantConversationService createService() {
        return new AssistantConversationService(
                assistantMessageMapper,
                assistantSessionContextMapper,
                assistantSessionMapper,
                assistantSessionSummaryService,
                assistantShortTermMemoryMaintenanceService,
                currentUserService,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );
    }

    private AssistantSessionEntity buildSession(Long sessionId, Long userId) {
        AssistantSessionEntity entity = new AssistantSessionEntity();
        entity.setId(sessionId);
        entity.setUserId(userId);
        entity.setTitle("测试会话");
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(LocalDateTime.of(2026, 4, 21, 10, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 4, 21, 10, 0));
        return entity;
    }

    private AssistantMessageEntity buildMessage(
            Long messageId,
            AssistantMessageRole role,
            AssistantToolMode toolMode,
            Long groupId,
            String content,
            LocalDateTime createdAt
    ) {
        AssistantMessageEntity entity = new AssistantMessageEntity();
        entity.setId(messageId);
        entity.setSessionId(2001L);
        entity.setRole(role.name());
        entity.setToolMode(toolMode.name());
        entity.setGroupId(groupId);
        entity.setContent(content);
        entity.setStructuredPayload(null);
        entity.setCreatedAt(createdAt);
        return entity;
    }
}
