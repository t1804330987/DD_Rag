package com.dong.ddrag.assistant;

import com.dong.ddrag.assistant.mapper.AssistantSessionMapper;
import com.dong.ddrag.assistant.model.entity.AssistantSessionEntity;
import com.dong.ddrag.assistant.model.vo.session.AssistantSessionDetailVO;
import com.dong.ddrag.assistant.service.AssistantSessionService;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.identity.service.CurrentUserService;
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
    private CurrentUserService currentUserService;

    @Test
    void shouldCreateSessionWithDefaultTitle() {
        AssistantSessionService assistantSessionService = createAssistantSessionService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        CurrentUserService.CurrentUser currentUser =
                new CurrentUserService.CurrentUser(1001L, "u1001", "测试用户甲");
        given(currentUserService.requireBusinessUser(any(HttpServletRequest.class))).willReturn(currentUser);
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

    private AssistantSessionService createAssistantSessionService() {
        return new AssistantSessionService(assistantSessionMapper, currentUserService);
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
}
