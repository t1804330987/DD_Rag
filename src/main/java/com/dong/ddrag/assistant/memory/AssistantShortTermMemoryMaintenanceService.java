package com.dong.ddrag.assistant.memory;

import com.dong.ddrag.assistant.mapper.AssistantMessageMapper;
import com.dong.ddrag.assistant.mapper.AssistantSessionContextMapper;
import com.dong.ddrag.assistant.model.entity.AssistantMessageEntity;
import com.dong.ddrag.assistant.model.entity.AssistantSessionContextEntity;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AssistantShortTermMemoryMaintenanceService {

    private static final int TOKEN_ESTIMATE_DIVISOR = 4;

    private final AssistantMessageMapper assistantMessageMapper;
    private final AssistantSessionContextMapper assistantSessionContextMapper;
    private final AssistantMemorySummarizer assistantMemorySummarizer;
    private final int sessionTokenThreshold;
    private final int sessionMemoryMessageTrigger;
    private final int sessionMemoryTokenTrigger;
    private final int compactMessageTrigger;
    private final int compactTokenTrigger;

    public AssistantShortTermMemoryMaintenanceService(
            AssistantMessageMapper assistantMessageMapper,
            AssistantSessionContextMapper assistantSessionContextMapper,
            AssistantMemorySummarizer assistantMemorySummarizer,
            @Value("${assistant.short-term-memory.session-token-threshold:6500}") int sessionTokenThreshold,
            @Value("${assistant.short-term-memory.session-memory-message-trigger:4}") int sessionMemoryMessageTrigger,
            @Value("${assistant.short-term-memory.session-memory-token-trigger:1200}") int sessionMemoryTokenTrigger,
            @Value("${assistant.short-term-memory.compact-message-trigger:6}") int compactMessageTrigger,
            @Value("${assistant.short-term-memory.compact-token-trigger:1800}") int compactTokenTrigger
    ) {
        this.assistantMessageMapper = assistantMessageMapper;
        this.assistantSessionContextMapper = assistantSessionContextMapper;
        this.assistantMemorySummarizer = assistantMemorySummarizer;
        this.sessionTokenThreshold = sessionTokenThreshold;
        this.sessionMemoryMessageTrigger = sessionMemoryMessageTrigger;
        this.sessionMemoryTokenTrigger = sessionMemoryTokenTrigger;
        this.compactMessageTrigger = compactMessageTrigger;
        this.compactTokenTrigger = compactTokenTrigger;
    }

    public void maintainBeforeResponse(
            Long userId,
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId,
            Long currentMessageId
    ) {
        maintain(sessionId, toolMode, groupId, currentMessageId);
    }

    public void maintainAfterResponse(
            Long userId,
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId,
            Long currentMessageId
    ) {
        maintain(sessionId, toolMode, groupId, currentMessageId);
    }

    public boolean shouldMaintainSessionMemory(List<AssistantMessageEntity> newMessages, long lastRangeEndMessageId) {
        if (newMessages == null || newMessages.isEmpty()) {
            return false;
        }
        int estimatedTokens = estimateTokens(newMessages);
        return newMessages.size() >= sessionMemoryMessageTrigger || estimatedTokens >= sessionMemoryTokenTrigger;
    }

    public boolean shouldCompactSession(int estimatedTokens, long newMessageCount, long newTokenCount) {
        return estimatedTokens > sessionTokenThreshold
                && (newMessageCount >= compactMessageTrigger || newTokenCount >= compactTokenTrigger);
    }

    public int estimateTokens(List<AssistantMessageEntity> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int totalChars = messages.stream()
                .map(AssistantMessageEntity::getContent)
                .filter(content -> content != null && !content.isBlank())
                .mapToInt(String::length)
                .sum();
        return Math.max(1, totalChars / TOKEN_ESTIMATE_DIVISOR);
    }

    private void maintain(Long sessionId, AssistantToolMode toolMode, Long groupId, Long currentMessageId) {
        List<AssistantMessageEntity> allMessages = assistantMessageMapper.selectBySessionIdOrderByCreatedAt(sessionId);
        AssistantSessionContextEntity existingContext = assistantSessionContextMapper.selectBySessionId(sessionId);
        long lastRangeEndMessageId = existingContext == null || existingContext.getSessionMemoryRangeEndMessageId() == null
                ? 0L
                : existingContext.getSessionMemoryRangeEndMessageId();
        List<AssistantMessageEntity> newMessages = allMessages.stream()
                .filter(message -> message.getId() != null && message.getId() > lastRangeEndMessageId)
                .toList();
        if (!shouldMaintainSessionMemory(newMessages, lastRangeEndMessageId)) {
            return;
        }
        AssistantSessionContextEntity contextToWrite = existingContext == null
                ? new AssistantSessionContextEntity()
                : existingContext;
        contextToWrite.setSessionId(sessionId);
        contextToWrite.setSessionMemory(assistantMemorySummarizer.summarizeSessionMemory(
                existingContext == null ? null : existingContext.getSessionMemory(),
                newMessages,
                toolMode,
                groupId
        ));
        contextToWrite.setSessionMemoryBaseMessageId(newMessages.getFirst().getId());
        contextToWrite.setSessionMemoryRangeEndMessageId(newMessages.getLast().getId());
        contextToWrite.setUpdatedAt(LocalDateTime.now());
        long expectedVersion = existingContext == null || existingContext.getContextVersion() == null
                ? 0L
                : existingContext.getContextVersion();
        contextToWrite.setContextVersion(expectedVersion + 1);

        int estimatedTokens = estimateTokens(allMessages);
        int newTokenCount = estimateTokens(newMessages);
        if (shouldCompactSession(estimatedTokens, newMessages.size(), newTokenCount)) {
            contextToWrite.setCompactSummary(assistantMemorySummarizer.summarizeCompactSummary(
                    existingContext == null ? null : existingContext.getCompactSummary(),
                    contextToWrite.getSessionMemory(),
                    collectMessagesToCompact(allMessages, currentMessageId)
            ));
            contextToWrite.setCompactSummaryBaseMessageId(allMessages.getFirst().getId());
            contextToWrite.setCompactSummaryRangeEndMessageId(newMessages.getLast().getId());
        }

        int updatedRows;
        if (existingContext == null) {
            updatedRows = assistantSessionContextMapper.upsert(contextToWrite);
        } else {
            updatedRows = assistantSessionContextMapper.updateShortTermMemoryWithVersion(contextToWrite, expectedVersion);
        }
        if (updatedRows != 1) {
            throw new BusinessException("短期记忆写回失败");
        }
    }

    private List<AssistantMessageEntity> collectMessagesToCompact(
            List<AssistantMessageEntity> allMessages,
            Long currentMessageId
    ) {
        List<AssistantMessageEntity> messagesToCompact = new java.util.ArrayList<>();
        for (AssistantMessageEntity message : allMessages) {
            if (message.getId() != null && currentMessageId != null && message.getId() >= currentMessageId) {
                break;
            }
            messagesToCompact.add(message);
        }
        return messagesToCompact;
    }
}
