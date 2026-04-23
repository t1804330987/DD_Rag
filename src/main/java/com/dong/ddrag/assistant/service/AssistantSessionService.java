package com.dong.ddrag.assistant.service;

import com.dong.ddrag.assistant.mapper.AssistantSessionMapper;
import com.dong.ddrag.assistant.mapper.AssistantMessageMapper;
import com.dong.ddrag.assistant.mapper.AssistantSessionContextMapper;
import com.dong.ddrag.assistant.model.dto.session.UpdateAssistantSessionRequest;
import com.dong.ddrag.assistant.model.entity.AssistantSessionEntity;
import com.dong.ddrag.assistant.model.enums.AssistantSessionStatus;
import com.dong.ddrag.assistant.model.vo.session.AssistantSessionDetailVO;
import com.dong.ddrag.assistant.model.vo.session.AssistantSessionListItemVO;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.identity.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AssistantSessionService {

    private static final String DEFAULT_SESSION_TITLE = "新会话";
    private final AssistantSessionMapper assistantSessionMapper;
    private final AssistantMessageMapper assistantMessageMapper;
    private final AssistantSessionContextMapper assistantSessionContextMapper;
    private final CurrentUserService currentUserService;

    public AssistantSessionService(
            AssistantSessionMapper assistantSessionMapper,
            AssistantMessageMapper assistantMessageMapper,
            AssistantSessionContextMapper assistantSessionContextMapper,
            CurrentUserService currentUserService
    ) {
        this.assistantSessionMapper = assistantSessionMapper;
        this.assistantMessageMapper = assistantMessageMapper;
        this.assistantSessionContextMapper = assistantSessionContextMapper;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public AssistantSessionDetailVO createSession(HttpServletRequest request) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        AssistantSessionEntity entity = buildNewSession(currentUser.userId());
        int affectedRows = assistantSessionMapper.insert(entity);
        if (affectedRows != 1 || entity.getId() == null) {
            throw new BusinessException("创建会话失败");
        }
        return toDetailVO(entity);
    }

    public List<AssistantSessionListItemVO> listSessions(HttpServletRequest request) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        return assistantSessionMapper.selectByUserIdOrderByLastMessageAtDesc(currentUser.userId()).stream()
                .map(this::toListItemVO)
                .toList();
    }

    public AssistantSessionDetailVO getSessionDetail(HttpServletRequest request, Long sessionId) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        return toDetailVO(requireOwnedSession(requireSessionId(sessionId), currentUser.userId()));
    }

    @Transactional
    public AssistantSessionDetailVO renameSession(
            HttpServletRequest request,
            Long sessionId,
            UpdateAssistantSessionRequest requestBody
    ) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        AssistantSessionEntity session = requireOwnedSession(requireSessionId(sessionId), currentUser.userId());
        String nextTitle = normalizeTitle(requestBody);
        LocalDateTime now = LocalDateTime.now();
        int updatedRows = assistantSessionMapper.updateTitle(session.getId(), currentUser.userId(), nextTitle, now);
        if (updatedRows != 1) {
            throw new BusinessException("重命名会话失败");
        }
        session.setTitle(nextTitle);
        session.setUpdatedAt(now);
        return toDetailVO(session);
    }

    @Transactional
    public void deleteSession(HttpServletRequest request, Long sessionId) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        AssistantSessionEntity session = requireOwnedSession(requireSessionId(sessionId), currentUser.userId());
        assistantSessionContextMapper.deleteBySessionId(session.getId());
        assistantMessageMapper.deleteBySessionId(session.getId());
        int deletedRows = assistantSessionMapper.deleteByIdAndUserId(session.getId(), currentUser.userId());
        if (deletedRows != 1) {
            throw new BusinessException("删除会话失败");
        }
    }

    @Transactional
    public void autoRenameSessionIfNeeded(Long currentUserId, Long sessionId, String firstUserMessage) {
        if (currentUserId == null || currentUserId <= 0 || sessionId == null || sessionId <= 0) {
            return;
        }
        AssistantSessionEntity session = requireOwnedSession(sessionId, currentUserId);
        if (!DEFAULT_SESSION_TITLE.equals(session.getTitle())) {
            return;
        }
        String generatedTitle = generateSessionTitle(firstUserMessage);
        if (generatedTitle == null || DEFAULT_SESSION_TITLE.equals(generatedTitle)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int updatedRows = assistantSessionMapper.updateTitle(sessionId, currentUserId, generatedTitle, now);
        if (updatedRows == 1) {
            session.setTitle(generatedTitle);
            session.setUpdatedAt(now);
        }
    }

    private AssistantSessionEntity requireOwnedSession(Long sessionId, Long currentUserId) {
        AssistantSessionEntity entity = assistantSessionMapper.selectByIdAndUserId(sessionId, currentUserId);
        if (entity == null) {
            throw new BusinessException("会话不存在");
        }
        return entity;
    }

    private Long requireSessionId(Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            throw new BusinessException("sessionId 非法");
        }
        return sessionId;
    }

    private String normalizeTitle(UpdateAssistantSessionRequest requestBody) {
        if (requestBody == null || requestBody.title() == null) {
            throw new BusinessException("title 不能为空");
        }
        String title = requestBody.title().trim().replaceAll("\\s+", " ");
        if (title.isEmpty()) {
            throw new BusinessException("title 不能为空");
        }
        if (title.length() > 255) {
            throw new BusinessException("title 长度不能超过 255");
        }
        return title;
    }

    private String generateSessionTitle(String firstUserMessage) {
        if (firstUserMessage == null) {
            return DEFAULT_SESSION_TITLE;
        }
        String normalized = firstUserMessage
                .replaceAll("\\s+", " ")
                .replace('\n', ' ')
                .trim();
        if (normalized.isEmpty()) {
            return DEFAULT_SESSION_TITLE;
        }
        int maxLength = 24;
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength).trim();
    }

    private AssistantSessionEntity buildNewSession(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        AssistantSessionEntity entity = new AssistantSessionEntity();
        entity.setUserId(userId);
        entity.setTitle(DEFAULT_SESSION_TITLE);
        entity.setStatus(AssistantSessionStatus.ACTIVE.name());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private AssistantSessionListItemVO toListItemVO(AssistantSessionEntity entity) {
        return new AssistantSessionListItemVO(
                entity.getId(),
                entity.getTitle(),
                entity.getLastMessageAt()
        );
    }

    private AssistantSessionDetailVO toDetailVO(AssistantSessionEntity entity) {
        return new AssistantSessionDetailVO(
                entity.getId(),
                entity.getTitle(),
                entity.getStatus(),
                entity.getLastMessageAt(),
                entity.getCreatedAt()
        );
    }
}
