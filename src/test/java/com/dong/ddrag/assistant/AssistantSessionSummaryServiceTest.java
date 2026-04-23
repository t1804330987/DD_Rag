package com.dong.ddrag.assistant;

import com.dong.ddrag.assistant.mapper.AssistantMessageMapper;
import com.dong.ddrag.assistant.mapper.AssistantSessionContextMapper;
import com.dong.ddrag.assistant.mapper.AssistantSessionMapper;
import com.dong.ddrag.assistant.memory.AssistantShortTermMemoryMaintenanceService;
import com.dong.ddrag.assistant.memory.AssistantSessionSummaryService;
import com.dong.ddrag.assistant.model.entity.AssistantMessageEntity;
import com.dong.ddrag.assistant.model.entity.AssistantSessionContextEntity;
import com.dong.ddrag.assistant.model.entity.AssistantSessionEntity;
import com.dong.ddrag.assistant.model.enums.AssistantMessageRole;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.assistant.service.AssistantConversationService;
import com.dong.ddrag.identity.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AssistantSessionSummaryServiceTest {

    @Mock
    private AssistantSessionContextMapper assistantSessionContextMapper;

    @Mock
    private AssistantMessageMapper assistantMessageMapper;

    @Mock
    private AssistantSessionMapper assistantSessionMapper;

    @Mock
    private AssistantShortTermMemoryMaintenanceService assistantShortTermMemoryMaintenanceService;

    @Mock
    private CurrentUserService currentUserService;

    @Test
    void shouldReuseExistingSummaryWhenNotStale() {
        AssistantSessionSummaryService service = createSummaryService();
        AssistantSessionContextEntity contextEntity = new AssistantSessionContextEntity();
        contextEntity.setSessionId(2001L);
        contextEntity.setSummaryText("已有摘要");
        contextEntity.setUpdatedAt(LocalDateTime.of(2026, 4, 21, 10, 5));
        given(assistantSessionContextMapper.selectBySessionId(2001L)).willReturn(contextEntity);

        String summary = service.loadReusableSummary(2001L, LocalDateTime.of(2026, 4, 21, 10, 5));

        assertThat(summary).isEqualTo("已有摘要");
    }

    @Test
    void shouldPersistSummaryWhenThresholdExceeded() {
        AssistantSessionSummaryService service = createSummaryService();
        given(assistantSessionContextMapper.upsert(any(AssistantSessionContextEntity.class))).willReturn(1);

        String summary = service.summarizeAndPersist(2001L, List.of(
                buildMessage(1L, "USER", "第一轮提问"),
                buildMessage(2L, "ASSISTANT", "第一轮回答"),
                buildMessage(3L, "USER", "第二轮提问"),
                buildMessage(4L, "ASSISTANT", "第二轮回答")
        ), 2);

        assertThat(summary).contains("历史摘要:");
        assertThat(summary).contains("用户：第一轮提问");
        assertThat(summary).contains("助手：第一轮回答");
        ArgumentCaptor<AssistantSessionContextEntity> captor = ArgumentCaptor.forClass(AssistantSessionContextEntity.class);
        then(assistantSessionContextMapper).should().upsert(captor.capture());
        assertThat(captor.getValue().getSourceMessageId()).isEqualTo(2L);
    }

    @Test
    void shouldLoadConversationContextWithSummaryAndRecentMessages() {
        AssistantSessionSummaryService summaryService = createSummaryService();
        AssistantConversationService conversationService = new AssistantConversationService(
                assistantMessageMapper,
                assistantSessionContextMapper,
                assistantSessionMapper,
                summaryService,
                assistantShortTermMemoryMaintenanceService,
                currentUserService,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );
        given(assistantSessionMapper.selectByIdAndUserId(2001L, 1001L))
                .willReturn(buildSession(2001L, 1001L, LocalDateTime.of(2026, 4, 21, 10, 5)));
        AssistantSessionContextEntity contextEntity = new AssistantSessionContextEntity();
        contextEntity.setSessionId(2001L);
        contextEntity.setSummaryText("已有摘要");
        contextEntity.setUpdatedAt(LocalDateTime.of(2026, 4, 21, 10, 5));
        given(assistantSessionContextMapper.selectBySessionId(2001L)).willReturn(contextEntity);
        given(assistantMessageMapper.selectRecentBySessionId(2001L, 2)).willReturn(List.of(
                buildMessage(4L, "ASSISTANT", "第二轮回答"),
                buildMessage(3L, "USER", "第二轮提问")
        ));

        AssistantConversationService.AssistantConversationContext context =
                conversationService.loadConversationContext(1001L, 2001L, 2);

        assertThat(context.summaryText()).isEqualTo("已有摘要");
        assertThat(context.recentMessages()).hasSize(2);
        assertThat(context.recentMessages().getFirst().content()).isEqualTo("第二轮提问");
        then(assistantMessageMapper).shouldHaveNoMoreInteractions();
    }

    @Test
    void shouldGenerateSummaryWhenNoReusableSummaryAndThresholdExceeded() {
        AssistantSessionSummaryService summaryService = createSummaryService();
        AssistantConversationService conversationService = new AssistantConversationService(
                assistantMessageMapper,
                assistantSessionContextMapper,
                assistantSessionMapper,
                summaryService,
                assistantShortTermMemoryMaintenanceService,
                currentUserService,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );
        given(assistantSessionMapper.selectByIdAndUserId(2001L, 1001L))
                .willReturn(buildSession(2001L, 1001L, LocalDateTime.of(2026, 4, 10, 10, 5)));
        given(assistantSessionContextMapper.selectBySessionId(2001L)).willReturn(null);
        List<AssistantMessageEntity> allMessages = List.of(
                buildMessage(1L, "USER", "第一轮提问"),
                buildMessage(2L, "ASSISTANT", "第一轮回答"),
                buildMessage(3L, "USER", "第二轮提问"),
                buildMessage(4L, "ASSISTANT", "第二轮回答")
        );
        given(assistantMessageMapper.selectRecentBySessionId(2001L, 2)).willReturn(List.of(
                buildMessage(4L, "ASSISTANT", "第二轮回答"),
                buildMessage(3L, "USER", "第二轮提问")
        ));
        given(assistantMessageMapper.countBySessionId(2001L)).willReturn(4L);
        given(assistantMessageMapper.selectBySessionIdOrderByCreatedAt(2001L)).willReturn(allMessages);
        given(assistantSessionContextMapper.upsert(any(AssistantSessionContextEntity.class))).willReturn(1);

        AssistantConversationService.AssistantConversationContext context =
                conversationService.loadConversationContext(1001L, 2001L, 2);

        assertThat(context.summaryText()).contains("历史摘要:");
        assertThat(context.recentMessages()).hasSize(2);
        then(assistantSessionContextMapper).should().upsert(any(AssistantSessionContextEntity.class));
    }

    private AssistantSessionSummaryService createSummaryService() {
        return new AssistantSessionSummaryService(
                assistantSessionContextMapper,
                Clock.fixed(Instant.parse("2026-04-21T10:05:00Z"), ZoneId.of("Asia/Shanghai")),
                3,
                100,
                7
        );
    }

    private AssistantSessionEntity buildSession(Long sessionId, Long userId, LocalDateTime lastMessageAt) {
        AssistantSessionEntity entity = new AssistantSessionEntity();
        entity.setId(sessionId);
        entity.setUserId(userId);
        entity.setTitle("测试会话");
        entity.setStatus("ACTIVE");
        entity.setLastMessageAt(lastMessageAt);
        entity.setCreatedAt(LocalDateTime.of(2026, 4, 21, 10, 0));
        entity.setUpdatedAt(lastMessageAt);
        return entity;
    }

    private AssistantMessageEntity buildMessage(Long messageId, String role, String content) {
        AssistantMessageEntity entity = new AssistantMessageEntity();
        entity.setId(messageId);
        entity.setSessionId(2001L);
        entity.setRole(role);
        entity.setToolMode(AssistantToolMode.CHAT.name());
        entity.setContent(content);
        entity.setCreatedAt(LocalDateTime.of(2026, 4, 21, 10, messageId.intValue()));
        return entity;
    }
}
