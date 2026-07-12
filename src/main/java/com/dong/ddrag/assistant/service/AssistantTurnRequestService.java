package com.dong.ddrag.assistant.service;

import com.dong.ddrag.assistant.mapper.AssistantMessageMapper;
import com.dong.ddrag.assistant.model.dto.chat.AssistantChatRequest;
import com.dong.ddrag.assistant.model.dto.message.AssistantMessageCreateDTO;
import com.dong.ddrag.assistant.model.entity.AssistantMessageEntity;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.assistant.model.vo.message.AssistantMessageVO;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.modelplatform.mapper.AssistantTurnRequestMapper;
import com.dong.ddrag.modelplatform.model.entity.AssistantTurnRequestEntity;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Persists request identity and user input before any Provider work begins. */
@Service
public class AssistantTurnRequestService {
    private final AssistantTurnRequestMapper turnRequestMapper;
    private final AssistantMessageMapper assistantMessageMapper;
    private final AssistantSessionService assistantSessionService;
    private final AssistantConversationService assistantConversationService;
    private final TransactionTemplate transactionTemplate;

    public AssistantTurnRequestService(
            AssistantTurnRequestMapper turnRequestMapper,
            AssistantMessageMapper assistantMessageMapper,
            AssistantSessionService assistantSessionService,
            AssistantConversationService assistantConversationService,
            PlatformTransactionManager transactionManager
    ) {
        this.turnRequestMapper = turnRequestMapper;
        this.assistantMessageMapper = assistantMessageMapper;
        this.assistantSessionService = assistantSessionService;
        this.assistantConversationService = assistantConversationService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PreparedTurn prepare(Long userId, AssistantChatRequest request) {
        RegisteredRequest registeredRequest = registerOrLoad(userId, request.requestId());
        AssistantTurnRequestEntity record = registeredRequest.request();
        if (!registeredRequest.created()) {
            return toReplay(record);
        }
        try {
            return transactionTemplate.execute(status -> prepareAcceptedRequest(userId, request, record));
        } catch (RuntimeException exception) {
            markFailed(userId, request.requestId(), failureCode(exception));
            throw exception;
        }
    }

    /**
     * The request identity is committed before this block so a duplicate can replay it.  Everything
     * that gives an accepted request its visible conversation state must commit together, however:
     * otherwise a failed bind leaves an orphan session or user message behind.
     */
    private PreparedTurn prepareAcceptedRequest(
            Long userId,
            AssistantChatRequest request,
            AssistantTurnRequestEntity record
    ) {
        Long sessionId = request.sessionId() == null
                ? assistantSessionService.createSessionForUser(userId, request.modelConnectionId(), request.modelId(),
                request.instructionProfileId()).sessionId()
                : request.sessionId();
        AssistantChatRequest effectiveRequest = withSessionId(request, sessionId);
        AssistantMessageVO userMessage = assistantConversationService.saveUserMessage(
                userId,
                new AssistantMessageCreateDTO(sessionId, request.toolMode(), request.groupId(), request.message(), null)
        );
        if (turnRequestMapper.bindPrepared(record.getId(), userId, sessionId, userMessage.messageId(), LocalDateTime.now()) != 1) {
            throw new BusinessException("ASSISTANT_REQUEST_STATE_INVALID");
        }
        return PreparedTurn.accepted(effectiveRequest, userMessage.messageId(), record.getTurnId());
    }

    public void markCompleted(Long userId, String requestId, Long assistantMessageId) {
        turnRequestMapper.completeByUserIdAndRequestId(userId, requestId, assistantMessageId, LocalDateTime.now());
    }

    public void markFailed(Long userId, String requestId, String failureCode) {
        turnRequestMapper.failByUserIdAndRequestId(userId, requestId, failureCode, LocalDateTime.now());
    }

    private RegisteredRequest registerOrLoad(Long userId, String requestId) {
        AssistantTurnRequestEntity candidate = new AssistantTurnRequestEntity();
        candidate.setUserId(userId);
        candidate.setRequestId(requestId);
        candidate.setTurnId(UUID.randomUUID().toString());
        candidate.setStatus("RUNNING");
        candidate.setCreatedAt(LocalDateTime.now());
        candidate.setUpdatedAt(candidate.getCreatedAt());
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (turnRequestMapper.insert(candidate) != 1) {
                    throw new BusinessException("ASSISTANT_REQUEST_CREATE_FAILED");
                }
            });
            return new RegisteredRequest(candidate, true);
        } catch (DataIntegrityViolationException exception) {
            AssistantTurnRequestEntity existing = turnRequestMapper.selectByUserIdAndRequestId(userId, requestId);
            if (existing == null) {
                throw new BusinessException("ASSISTANT_REQUEST_CONFLICT");
            }
            return new RegisteredRequest(existing, false);
        }
    }

    private PreparedTurn toReplay(AssistantTurnRequestEntity request) {
        if ("RUNNING".equals(request.getStatus())) {
            return PreparedTurn.running(request.getSessionId(), request.getTurnId());
        }
        if ("FAILED".equals(request.getStatus())) {
            return PreparedTurn.failed();
        }
        AssistantMessageEntity message = request.getAssistantMessageId() == null
                ? null : assistantMessageMapper.selectById(request.getAssistantMessageId());
        if (message == null) {
            throw new BusinessException("ASSISTANT_REQUEST_RESULT_UNAVAILABLE");
        }
        return PreparedTurn.completed(request.getSessionId(), message.getId(), message.getContent(),
                AssistantToolMode.valueOf(message.getToolMode()), message.getGroupId(), request.getTurnId());
    }

    private AssistantChatRequest withSessionId(AssistantChatRequest request, Long sessionId) {
        return new AssistantChatRequest(sessionId, request.message(), request.toolMode(), request.groupId(), request.requestId(),
                request.modelConnectionId(), request.modelId(), request.instructionProfileId());
    }

    private String failureCode(RuntimeException exception) {
        if (exception instanceof BusinessException businessException && businessException.getMessage() != null) {
            return businessException.getMessage().length() <= 64 ? businessException.getMessage() : "ASSISTANT_FAILED";
        }
        return "ASSISTANT_FAILED";
    }

    private record RegisteredRequest(AssistantTurnRequestEntity request, boolean created) { }

    public record PreparedTurn(
            AssistantChatRequest request,
            Long userMessageId,
            String turnId,
            String replayStatus,
            Long replaySessionId,
            Long replayMessageId,
            String replayReply,
            AssistantToolMode replayToolMode,
            Long replayGroupId
    ) {
        public static PreparedTurn accepted(AssistantChatRequest request, Long userMessageId, String turnId) {
            return new PreparedTurn(request, userMessageId, turnId, null, null, null, null, null, null);
        }

        public static PreparedTurn running(Long sessionId, String turnId) {
            return new PreparedTurn(null, null, turnId, "RUNNING", sessionId, null, null, null, null);
        }

        public static PreparedTurn completed(Long sessionId, Long messageId, String reply,
                                             AssistantToolMode toolMode, Long groupId, String turnId) {
            return new PreparedTurn(null, null, turnId, "COMPLETED", sessionId, messageId, reply, toolMode, groupId);
        }

        public static PreparedTurn failed() {
            return new PreparedTurn(null, null, null, "FAILED", null, null, null, null, null);
        }

        public boolean isAccepted() {
            return replayStatus == null;
        }
    }
}
