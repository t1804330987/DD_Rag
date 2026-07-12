package com.dong.ddrag.assistant.memory.runtime;

import com.dong.ddrag.assistant.mapper.AssistantMessageMapper;
import com.dong.ddrag.assistant.mapper.AssistantSessionContextMapper;
import com.dong.ddrag.assistant.model.entity.AssistantMessageEntity;
import com.dong.ddrag.assistant.model.entity.AssistantSessionContextEntity;
import com.dong.ddrag.assistant.model.enums.AssistantMessageRole;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.assistant.model.vo.message.AssistantMessageVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AssistantRuntimeMemoryService {

    private static final Logger log = LoggerFactory.getLogger(AssistantRuntimeMemoryService.class);
    private static final int RECENT_MESSAGE_LIMIT = 8;
    private static final long PENDING_TTL_MILLIS = 10 * 60 * 1000L;

    private final AssistantSessionContextMapper assistantSessionContextMapper;
    private final AssistantMessageMapper assistantMessageMapper;
    private final AssistantRuntimeMemoryExtractor extractor;
    private final AssistantRuntimeMemoryStateApplier applier;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AssistantRuntimeMemoryService(
            AssistantSessionContextMapper assistantSessionContextMapper,
            AssistantMessageMapper assistantMessageMapper,
            AssistantRuntimeMemoryExtractor extractor,
            AssistantRuntimeMemoryStateApplier applier,
            ObjectMapper objectMapper
    ) {
        this(
                assistantSessionContextMapper,
                assistantMessageMapper,
                extractor,
                applier,
                objectMapper,
                Clock.systemUTC()
        );
    }

    public AssistantRuntimeMemoryService(
            AssistantSessionContextMapper assistantSessionContextMapper,
            AssistantMessageMapper assistantMessageMapper,
            AssistantRuntimeMemoryExtractor extractor,
            AssistantRuntimeMemoryStateApplier applier,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.assistantSessionContextMapper = assistantSessionContextMapper;
        this.assistantMessageMapper = assistantMessageMapper;
        this.extractor = extractor;
        this.applier = applier;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public AssistantRuntimeMemoryDecision beforeAnswer(
            Long userId,
            Long sessionId,
            Long userMessageId,
            AssistantToolMode toolMode,
            Long groupId,
            String currentUserMessage
    ) {
        long now = clock.millis();
        AssistantRuntimeMemoryState state = loadState(sessionId);
        AssistantRuntimeMemoryState.Pending pending = state.pending();
        if (pending != null) {
            return handlePending(sessionId, userMessageId, currentUserMessage, pending, now);
        }
        if (shouldSkipExtraction(currentUserMessage)) {
            return AssistantRuntimeMemoryDecision.continueWith(currentUserMessage);
        }
        List<AssistantRuntimeMemoryChange> changes;
        try {
            changes = extractor.extract(userId, sessionId, state, loadRecentMessages(sessionId), currentUserMessage);
        } catch (RuntimeException exception) {
            log.warn("Runtime memory extraction failed, skip update. sessionId={}, error={}", sessionId, exception.toString());
            return AssistantRuntimeMemoryDecision.continueWith(currentUserMessage);
        }
        if (changes == null || changes.isEmpty()) {
            return AssistantRuntimeMemoryDecision.continueWith(currentUserMessage);
        }
        Optional<AssistantRuntimeMemoryState> saved = writeMutatedState(
                sessionId,
                base -> applier.apply(base, changes, userMessageId, now, currentUserMessage)
        );
        if (saved.isEmpty()) {
            return AssistantRuntimeMemoryDecision.continueWith(currentUserMessage);
        }
        AssistantRuntimeMemoryState.Pending savedPending = saved.get().pending();
        if (savedPending != null && savedPending.confirmationQuestion() != null) {
            return AssistantRuntimeMemoryDecision.askConfirmation(savedPending.confirmationQuestion());
        }
        return AssistantRuntimeMemoryDecision.continueWith(currentUserMessage);
    }

    public AssistantRuntimeMemoryState loadState(Long sessionId) {
        AssistantSessionContextEntity context = assistantSessionContextMapper.selectBySessionId(sessionId);
        return parseState(context == null ? null : context.getRuntimeMemoryState());
    }

    private AssistantRuntimeMemoryDecision handlePending(
            Long sessionId,
            Long userMessageId,
            String currentUserMessage,
            AssistantRuntimeMemoryState.Pending pending,
            long now
    ) {
        if (isExpired(pending, now)) {
            writeMutatedState(sessionId, applier::clearPending);
            return AssistantRuntimeMemoryDecision.continueWith(currentUserMessage);
        }
        PendingReply reply = classifyPendingReply(currentUserMessage);
        if (reply == PendingReply.CONFIRM) {
            Optional<AssistantRuntimeMemoryState> saved = writeMutatedState(
                    sessionId,
                    base -> applier.applyPending(base, userMessageId, now)
            );
            if (saved.isPresent()) {
                return AssistantRuntimeMemoryDecision.continueWith(pending.originalUserRequest());
            }
            return AssistantRuntimeMemoryDecision.continueWith(currentUserMessage);
        }
        writeMutatedState(sessionId, applier::clearPending);
        return AssistantRuntimeMemoryDecision.continueWith(currentUserMessage);
    }

    private Optional<AssistantRuntimeMemoryState> writeMutatedState(
            Long sessionId,
            StateMutation mutation
    ) {
        for (int attempt = 0; attempt < 2; attempt++) {
            AssistantSessionContextEntity existing = assistantSessionContextMapper.selectBySessionId(sessionId);
            AssistantRuntimeMemoryState base = parseState(existing == null ? null : existing.getRuntimeMemoryState());
            AssistantRuntimeMemoryState next = mutation.apply(base);
            AssistantSessionContextEntity contextToWrite = existing == null
                    ? new AssistantSessionContextEntity()
                    : existing;
            contextToWrite.setSessionId(sessionId);
            contextToWrite.setRuntimeMemoryState(serializeState(next));
            long expectedVersion = existing == null || existing.getContextVersion() == null
                    ? 0L
                    : existing.getContextVersion();
            contextToWrite.setContextVersion(expectedVersion + 1);
            contextToWrite.setUpdatedAt(LocalDateTime.now());
            int updatedRows = existing == null
                    ? assistantSessionContextMapper.upsert(contextToWrite)
                    : assistantSessionContextMapper.updateShortTermMemoryWithVersion(contextToWrite, expectedVersion);
            if (updatedRows == 1) {
                return Optional.of(next);
            }
            log.warn("Runtime memory optimistic lock conflict. sessionId={}, attempt={}", sessionId, attempt + 1);
        }
        log.warn("Runtime memory update skipped after retry failure. sessionId={}", sessionId);
        return Optional.empty();
    }

    private AssistantRuntimeMemoryState parseState(String runtimeMemoryState) {
        if (runtimeMemoryState == null || runtimeMemoryState.isBlank()) {
            return AssistantRuntimeMemoryState.empty();
        }
        try {
            return objectMapper.readValue(runtimeMemoryState, AssistantRuntimeMemoryState.class);
        } catch (JsonProcessingException exception) {
            log.warn("Runtime memory state JSON is invalid, treating as empty. error={}", exception.toString());
            return AssistantRuntimeMemoryState.empty();
        }
    }

    private String serializeState(AssistantRuntimeMemoryState state) {
        try {
            return objectMapper.writeValueAsString(state == null ? AssistantRuntimeMemoryState.empty() : state);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("runtime memory state 序列化失败", exception);
        }
    }

    private List<AssistantMessageVO> loadRecentMessages(Long sessionId) {
        List<AssistantMessageEntity> messages = assistantMessageMapper.selectRecentBySessionId(sessionId, RECENT_MESSAGE_LIMIT);
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .map(this::toMessageVO)
                .toList();
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

    private boolean shouldSkipExtraction(String message) {
        if (message == null || message.trim().length() < 5) {
            return true;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return !(normalized.contains("改成")
                || normalized.contains("修改")
                || normalized.contains("换成")
                || normalized.contains("替换")
                || normalized.contains("推翻")
                || normalized.contains("修正")
                || normalized.contains("不用")
                || normalized.contains("取消")
                || normalized.contains("撤销")
                || normalized.contains("按这个")
                || normalized.contains("也可以"));
    }

    private boolean isExpired(AssistantRuntimeMemoryState.Pending pending, long now) {
        return pending.createdAt() == null || now - pending.createdAt() > PENDING_TTL_MILLIS;
    }

    private PendingReply classifyPendingReply(String message) {
        if (message == null) {
            return PendingReply.UNRELATED;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches(".*(不|否|取消|算了|不要|no|cancel).*")) {
            return PendingReply.CANCEL;
        }
        if (normalized.matches(".*(是|对|确认|可以|好的|好|yes|ok|yep).*")) {
            return PendingReply.CONFIRM;
        }
        return PendingReply.UNRELATED;
    }

    private enum PendingReply {
        CONFIRM,
        CANCEL,
        UNRELATED
    }

    @FunctionalInterface
    private interface StateMutation {
        AssistantRuntimeMemoryState apply(AssistantRuntimeMemoryState state);
    }

    public record AssistantRuntimeMemoryDecision(
            boolean requiresConfirmation,
            String assistantReply,
            String effectiveUserMessage
    ) {

        public static AssistantRuntimeMemoryDecision continueWith(String effectiveUserMessage) {
            return new AssistantRuntimeMemoryDecision(false, null, effectiveUserMessage);
        }

        public static AssistantRuntimeMemoryDecision askConfirmation(String assistantReply) {
            return new AssistantRuntimeMemoryDecision(true, assistantReply, null);
        }
    }
}
