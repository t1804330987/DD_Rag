package com.dong.ddrag.assistant.service;

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
import com.dong.ddrag.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dong.ddrag.identity.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class AssistantConversationService {

    private static final int MAX_RECENT_MESSAGE_LIMIT = 100;

    private final AssistantMessageMapper assistantMessageMapper;
    private final AssistantSessionContextMapper assistantSessionContextMapper;
    private final AssistantSessionMapper assistantSessionMapper;
    private final AssistantSessionSummaryService assistantSessionSummaryService;
    private final AssistantShortTermMemoryMaintenanceService assistantShortTermMemoryMaintenanceService;
    private final AssistantSessionService assistantSessionService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public AssistantConversationService(
            AssistantMessageMapper assistantMessageMapper,
            AssistantSessionContextMapper assistantSessionContextMapper,
            AssistantSessionMapper assistantSessionMapper,
            AssistantSessionSummaryService assistantSessionSummaryService,
            AssistantShortTermMemoryMaintenanceService assistantShortTermMemoryMaintenanceService,
            AssistantSessionService assistantSessionService,
            CurrentUserService currentUserService,
            ObjectMapper objectMapper
    ) {
        this.assistantMessageMapper = assistantMessageMapper;
        this.assistantSessionContextMapper = assistantSessionContextMapper;
        this.assistantSessionMapper = assistantSessionMapper;
        this.assistantSessionSummaryService = assistantSessionSummaryService;
        this.assistantShortTermMemoryMaintenanceService = assistantShortTermMemoryMaintenanceService;
        this.assistantSessionService = assistantSessionService;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AssistantMessageVO saveUserMessage(Long currentUserId, AssistantMessageCreateDTO dto) {
        return saveMessage(currentUserId, dto, AssistantMessageRole.USER);
    }

    @Transactional
    public AssistantMessageVO saveAssistantMessage(Long currentUserId, AssistantMessageCreateDTO dto) {
        return saveMessage(currentUserId, dto, AssistantMessageRole.ASSISTANT);
    }

    public List<AssistantMessageVO> loadRecentMessages(Long currentUserId, Long sessionId, int limit) {
        AssistantSessionEntity session = requireOwnedSession(requireUserId(currentUserId), requireSessionId(sessionId));
        int safeLimit = normalizeLimit(limit);
        return assistantMessageMapper.selectRecentBySessionId(session.getId(), safeLimit).stream()
                .sorted(Comparator.comparing(AssistantMessageEntity::getCreatedAt).thenComparing(AssistantMessageEntity::getId))
                .map(this::toMessageVO)
                .toList();
    }

    public AssistantConversationContext loadConversationContext(Long currentUserId, Long sessionId, int recentLimit) {
        AssistantSessionEntity session = requireOwnedSession(requireUserId(currentUserId), requireSessionId(sessionId));
        int safeLimit = normalizeLimit(recentLimit);
        AssistantSessionContextEntity sessionContext = assistantSessionContextMapper.selectBySessionId(session.getId());
        List<AssistantMessageVO> recentMessages = assistantMessageMapper.selectRecentBySessionId(session.getId(), safeLimit).stream()
                .sorted(Comparator.comparing(AssistantMessageEntity::getCreatedAt).thenComparing(AssistantMessageEntity::getId))
                .map(this::toMessageVO)
                .toList();
        String compactSummary = normalizeOptionalText(sessionContext == null ? null : sessionContext.getCompactSummary());
        String sessionMemory = normalizeOptionalText(sessionContext == null ? null : sessionContext.getSessionMemory());
        // 运行时上下文优先复用已沉淀的 summary / compact / session memory，避免每次都把全量消息塞给模型。
        String summaryText = assistantSessionSummaryService.loadReusableSummary(session.getId(), session.getLastMessageAt());
        if (summaryText != null) {
            return new AssistantConversationContext(summaryText, compactSummary, sessionMemory, recentMessages);
        }
        Long totalMessages = assistantMessageMapper.countBySessionId(session.getId());
        List<AssistantMessageEntity> allMessages = assistantMessageMapper.selectBySessionIdOrderByCreatedAt(session.getId());
        int estimatedTokens = assistantSessionSummaryService.estimateTokens(allMessages);
        if (assistantSessionSummaryService.shouldSummarize(
                totalMessages == null ? 0 : totalMessages,
                estimatedTokens,
                session.getLastMessageAt()
        )) {
            String generatedSummary = assistantSessionSummaryService.summarizeAndPersist(session.getId(), allMessages, safeLimit);
            return new AssistantConversationContext(generatedSummary, compactSummary, sessionMemory, recentMessages);
        }
        return new AssistantConversationContext(null, compactSummary, sessionMemory, recentMessages);
    }

    public AssistantConversationContextVO getConversationContext(
            HttpServletRequest request,
            Long sessionId,
            int recentLimit
    ) {
        Long currentUserId = currentUserService.requireBusinessUser(request).userId();
        AssistantConversationContext context = loadConversationContext(currentUserId, sessionId, recentLimit);
        return new AssistantConversationContextVO(context.summaryText(), context.recentMessages());
    }

    private AssistantMessageVO saveMessage(
            Long currentUserId,
            AssistantMessageCreateDTO dto,
            AssistantMessageRole role
    ) {
        Long userId = requireUserId(currentUserId);
        AssistantMessageCreateDTO safeDto = requireCreateDTO(dto);
        AssistantSessionEntity session = requireOwnedSession(userId, requireSessionId(safeDto.sessionId()));
        LocalDateTime now = LocalDateTime.now();
        AssistantMessageEntity entity = buildMessageEntity(session.getId(), safeDto, role, now);
        int affectedRows = assistantMessageMapper.insert(entity);
        if (affectedRows != 1 || entity.getId() == null) {
            throw new BusinessException("消息保存失败");
        }
        int updatedRows = assistantSessionMapper.updateLastMessageAt(session.getId(), userId, now);
        if (updatedRows != 1) {
            throw new BusinessException("会话更新时间刷新失败");
        }
        if (role == AssistantMessageRole.USER) {
            Long messageCount = assistantMessageMapper.countBySessionId(session.getId());
            if (messageCount != null && messageCount == 1L) {
                assistantSessionService.autoRenameSessionIfNeeded(userId, session.getId(), safeDto.content());
            }
        }
        // 消息持久化完成后再维护短期记忆，这样短期摘要看到的是数据库里的真实会话状态。
        maintainShortTermMemory(userId, safeDto, role, entity.getId());
        return toMessageVO(entity);
    }

    private AssistantMessageEntity buildMessageEntity(
            Long sessionId,
            AssistantMessageCreateDTO dto,
            AssistantMessageRole role,
            LocalDateTime createdAt
    ) {
        AssistantMessageEntity entity = new AssistantMessageEntity();
        entity.setSessionId(sessionId);
        entity.setRole(role.name());
        entity.setToolMode(dto.toolMode().name());
        entity.setGroupId(normalizeGroupId(dto.toolMode(), dto.groupId()));
        entity.setContent(requireContent(dto.content()));
        entity.setStructuredPayload(normalizeStructuredPayload(dto.structuredPayload()));
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private AssistantSessionEntity requireOwnedSession(Long currentUserId, Long sessionId) {
        AssistantSessionEntity session = assistantSessionMapper.selectByIdAndUserId(sessionId, currentUserId);
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        return session;
    }

    private AssistantMessageCreateDTO requireCreateDTO(AssistantMessageCreateDTO dto) {
        if (dto == null) {
            throw new BusinessException("消息请求不能为空");
        }
        if (dto.toolMode() == null) {
            throw new BusinessException("toolMode 不能为空");
        }
        return dto;
    }

    private Long requireUserId(Long currentUserId) {
        if (currentUserId == null || currentUserId <= 0) {
            throw new BusinessException("userId 非法");
        }
        return currentUserId;
    }

    private Long requireSessionId(Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            throw new BusinessException("sessionId 非法");
        }
        return sessionId;
    }

    private String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("content 不能为空");
        }
        return content;
    }

    private String normalizeOptionalText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            throw new BusinessException("limit 非法");
        }
        return Math.min(limit, MAX_RECENT_MESSAGE_LIMIT);
    }

    private Long normalizeGroupId(AssistantToolMode toolMode, Long groupId) {
        if (groupId != null && groupId <= 0) {
            throw new BusinessException("groupId 非法");
        }
        if (toolMode == AssistantToolMode.CHAT) {
            return null;
        }
        if (groupId == null) {
            throw new BusinessException("KB_SEARCH 模式必须提供 groupId");
        }
        return groupId;
    }

    private String normalizeStructuredPayload(String structuredPayload) {
        if (structuredPayload == null || structuredPayload.isBlank()) {
            return null;
        }
        try {
            objectMapper.readTree(structuredPayload);
            return structuredPayload;
        } catch (JsonProcessingException exception) {
            throw new BusinessException("structuredPayload 非法", exception);
        }
    }

    private void maintainShortTermMemory(
            Long userId,
            AssistantMessageCreateDTO dto,
            AssistantMessageRole role,
            Long messageId
    ) {
        if (messageId == null) {
            return;
        }
        if (role == AssistantMessageRole.USER) {
            // 用户消息入库后先做一次“响应前维护”，让下一次模型调用能读到最新的 session memory / compact summary。
            assistantShortTermMemoryMaintenanceService.maintainBeforeResponse(
                    userId,
                    dto.sessionId(),
                    dto.toolMode(),
                    dto.groupId(),
                    messageId
            );
            return;
        }
        // 助手消息入库后再做一次“响应后维护”，让摘要状态和完整对话继续保持同步。
        assistantShortTermMemoryMaintenanceService.maintainAfterResponse(
                userId,
                dto.sessionId(),
                dto.toolMode(),
                dto.groupId(),
                messageId
        );
    }

    private AssistantMessageVO toMessageVO(AssistantMessageEntity entity) {
        return new AssistantMessageVO(
                entity.getId(),
                entity.getSessionId(),
                AssistantMessageRole.valueOf(entity.getRole()),
                entity.getToolMode() == null ? null : AssistantToolMode.valueOf(entity.getToolMode()),
                entity.getGroupId(),
                entity.getContent(),
                entity.getStructuredPayload(),
                entity.getCreatedAt()
        );
    }

    public record AssistantConversationContext(
            String summaryText,
            String compactSummary,
            String sessionMemory,
            List<AssistantMessageVO> recentMessages
    ) {
    }
}
