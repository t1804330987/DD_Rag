package com.dong.ddrag.assistant;

import com.dong.ddrag.assistant.mapper.AssistantMessageMapper;
import com.dong.ddrag.assistant.mapper.AssistantSessionContextMapper;
import com.dong.ddrag.assistant.mapper.AssistantSessionMapper;
import com.dong.ddrag.assistant.model.entity.AssistantSessionEntity;
import com.dong.ddrag.assistant.model.vo.session.AssistantSessionDetailVO;
import com.dong.ddrag.assistant.service.AssistantSessionService;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.modelplatform.service.AssistantInstructionProfileService;
import com.dong.ddrag.modelplatform.runtime.ModelRuntimeService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AssistantSessionServiceTest {

    @Mock
    private AssistantSessionMapper assistantSessionMapper;

    @Mock
    private AssistantMessageMapper assistantMessageMapper;

    @Mock
    private AssistantSessionContextMapper assistantSessionContextMapper;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private AssistantInstructionProfileService instructionProfileService;

    @Mock
    private ModelRuntimeService modelRuntimeService;

    @Test
    void shouldCreateSessionWithDefaultTitle() {
        AssistantSessionService assistantSessionService = createAssistantSessionService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        CurrentUserService.CurrentUser currentUser =
                new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户甲");
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class))).willReturn(currentUser);
        given(instructionProfileService.resolveDefault(1001L))
                .willReturn(new AssistantInstructionProfileService.ResolvedInstruction(null, null, null, null));
        given(assistantSessionMapper.insert(any(AssistantSessionEntity.class))).willAnswer(invocation -> {
            AssistantSessionEntity entity = invocation.getArgument(0);
            entity.setId(3001L);
            entity.setCreatedAt(LocalDateTime.of(2026, 4, 21, 10, 0));
            entity.setUpdatedAt(entity.getCreatedAt());
            return 1;
        });

        AssistantSessionDetailVO session = assistantSessionService.createSession(request);

        assertThat(session.sessionId()).isEqualTo(3001L);
        assertThat(session.title()).isEqualTo("新会话");
        then(assistantSessionMapper).should().insert(any(AssistantSessionEntity.class));
    }

    @Test
    void shouldRejectAccessToOtherUsersSession() {
        AssistantSessionService assistantSessionService = createAssistantSessionService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        CurrentUserService.CurrentUser currentUser =
                new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户甲");
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class))).willReturn(currentUser);
        given(assistantSessionMapper.selectByIdAndUserId(2002L, 1001L)).willReturn(null);

        assertThatThrownBy(() -> assistantSessionService.getSessionDetail(request, 2002L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("会话不存在");
    }

    @Test
    void shouldListOnlyCurrentUsersSessions() {
        AssistantSessionService assistantSessionService = createAssistantSessionService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        CurrentUserService.CurrentUser currentUser =
                new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户甲");
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class))).willReturn(currentUser);
        given(assistantSessionMapper.selectByUserIdOrderByLastMessageAtDesc(1001L)).willReturn(List.of(buildSession(2001L, 1001L)));

        assertThat(assistantSessionService.listSessions(request))
                .extracting("sessionId")
                .containsExactly(2001L);
        then(assistantSessionMapper).should().selectByUserIdOrderByLastMessageAtDesc(1001L);
        then(assistantSessionMapper).shouldHaveNoMoreInteractions();
    }

    @Test
    void shouldUseNormalizedFirstMessageAsDefaultSessionTitle() {
        AssistantSessionService assistantSessionService = createAssistantSessionService();
        given(assistantSessionMapper.selectByIdAndUserId(2001L, 1001L)).willReturn(buildDefaultSession(2001L, 1001L));
        given(assistantSessionMapper.updateTitle(anyLong(), anyLong(), any(), any())).willReturn(1);

        assistantSessionService.autoRenameSessionIfNeeded(1001L, 2001L, "  请   帮我\n总结   这段内容  ");

        then(assistantSessionMapper).should().updateTitle(
                org.mockito.ArgumentMatchers.eq(2001L),
                org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.eq("请 帮我 总结 这段内容"),
                any()
        );
    }

    @Test
    void shouldValidateAndPersistSelectedModelForOwnedSession() {
        AssistantSessionService assistantSessionService = createAssistantSessionService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        CurrentUserService.CurrentUser currentUser =
                new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户甲");
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class))).willReturn(currentUser);
        given(assistantSessionMapper.selectByIdAndUserId(2001L, 1001L)).willReturn(buildSession(2001L, 1001L));
        given(assistantSessionMapper.updateCurrentModel(anyLong(), anyLong(), anyLong(), anyLong(), any())).willReturn(1);

        assistantSessionService.selectModel(request, 2001L, 301L, 401L);

        then(modelRuntimeService).should().requireAvailableAssistantModel(1001L, 301L, 401L);
        then(assistantSessionMapper).should().updateCurrentModel(anyLong(), anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void shouldKeepPlatformDefaultInstructionWhenCreatingSessionWithoutProfile() {
        AssistantSessionService assistantSessionService = createAssistantSessionService();
        given(instructionProfileService.resolveForSession(1001L, null))
                .willReturn(new AssistantInstructionProfileService.ResolvedInstruction(null, null, null, null));
        given(assistantSessionMapper.insert(any(AssistantSessionEntity.class))).willAnswer(invocation -> {
            AssistantSessionEntity entity = invocation.getArgument(0);
            entity.setId(3001L);
            return 1;
        });

        AssistantSessionDetailVO session = assistantSessionService.createSessionForUser(1001L, null, null, null);

        assertThat(session.currentInstructionProfileId()).isNull();
        then(instructionProfileService).should().resolveForSession(1001L, null);
        then(instructionProfileService).shouldHaveNoMoreInteractions();
    }

    private AssistantSessionService createAssistantSessionService() {
        return new AssistantSessionService(
                assistantSessionMapper,
                assistantMessageMapper,
                assistantSessionContextMapper,
                currentUserService,
                instructionProfileService,
                modelRuntimeService
        );
    }

    private AssistantSessionEntity buildSession(Long sessionId, Long userId) {
        AssistantSessionEntity entity = new AssistantSessionEntity();
        entity.setId(sessionId);
        entity.setUserId(userId);
        entity.setTitle("我的会话");
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(LocalDateTime.of(2026, 4, 21, 10, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 4, 21, 10, 5));
        entity.setLastMessageAt(LocalDateTime.of(2026, 4, 21, 10, 4));
        return entity;
    }

    private AssistantSessionEntity buildDefaultSession(Long sessionId, Long userId) {
        AssistantSessionEntity entity = buildSession(sessionId, userId);
        entity.setTitle("新会话");
        return entity;
    }
}
