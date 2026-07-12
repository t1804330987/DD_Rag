package com.dong.ddrag.assistant.service;

import com.dong.ddrag.assistant.agent.AssistantAgentFacade;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryService;
import com.dong.ddrag.assistant.model.dto.chat.AssistantChatRequest;
import com.dong.ddrag.assistant.model.dto.message.AssistantMessageCreateDTO;
import com.dong.ddrag.assistant.model.vo.chat.AssistantAgentResult;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.assistant.model.vo.chat.AssistantChatResponse;
import com.dong.ddrag.assistant.model.vo.chat.AssistantChatStreamEvent;
import com.dong.ddrag.assistant.model.vo.message.AssistantMessageVO;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.groupmembership.service.GroupMembershipService;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.modelplatform.runtime.ModelCallCancellation;
import com.dong.ddrag.modelplatform.concurrency.AssistantTurnGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

@Service
public class AssistantService {

    private final AssistantConversationService assistantConversationService;
    private final AssistantAgentFacade assistantAgentFacade;
    private final AssistantRuntimeMemoryService assistantRuntimeMemoryService;
    private final GroupMembershipService groupMembershipService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;
    private final AssistantTurnRequestService assistantTurnRequestService;
    private final AssistantTurnGuard assistantTurnGuard;

    public AssistantService(
            AssistantConversationService assistantConversationService,
            AssistantAgentFacade assistantAgentFacade,
            AssistantRuntimeMemoryService assistantRuntimeMemoryService,
            GroupMembershipService groupMembershipService,
            CurrentUserService currentUserService,
            ObjectMapper objectMapper
    ) {
        this.assistantConversationService = assistantConversationService;
        this.assistantAgentFacade = assistantAgentFacade;
        this.assistantRuntimeMemoryService = assistantRuntimeMemoryService;
        this.groupMembershipService = groupMembershipService;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
        this.assistantTurnRequestService = null;
        this.assistantTurnGuard = new AssistantTurnGuard();
    }

    public AssistantService(
            AssistantConversationService assistantConversationService,
            AssistantAgentFacade assistantAgentFacade,
            AssistantRuntimeMemoryService assistantRuntimeMemoryService,
            GroupMembershipService groupMembershipService,
            CurrentUserService currentUserService,
            ObjectMapper objectMapper,
            AssistantTurnRequestService assistantTurnRequestService
    ) {
        this(
                assistantConversationService,
                assistantAgentFacade,
                assistantRuntimeMemoryService,
                groupMembershipService,
                currentUserService,
                objectMapper,
                assistantTurnRequestService,
                new AssistantTurnGuard()
        );
    }

    @Autowired
    public AssistantService(
            AssistantConversationService assistantConversationService,
            AssistantAgentFacade assistantAgentFacade,
            AssistantRuntimeMemoryService assistantRuntimeMemoryService,
            GroupMembershipService groupMembershipService,
            CurrentUserService currentUserService,
            ObjectMapper objectMapper,
            AssistantTurnRequestService assistantTurnRequestService,
            AssistantTurnGuard assistantTurnGuard
    ) {
        this.assistantConversationService = assistantConversationService;
        this.assistantAgentFacade = assistantAgentFacade;
        this.assistantRuntimeMemoryService = assistantRuntimeMemoryService;
        this.groupMembershipService = groupMembershipService;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
        this.assistantTurnRequestService = assistantTurnRequestService;
        this.assistantTurnGuard = assistantTurnGuard;
    }

    public AssistantChatResponse chat(HttpServletRequest request, AssistantChatRequest chatRequest) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        AssistantChatRequest safeRequest = requireChatRequest(chatRequest);

        if (assistantTurnRequestService != null) {
            return chatIdempotently(request, currentUser.userId(), safeRequest);
        }

        AssistantMessageVO userMessage = saveUserMessage(currentUser.userId(), safeRequest);
        AssistantRuntimeMemoryService.AssistantRuntimeMemoryDecision memoryDecision = beforeAnswer(
                currentUser.userId(),
                safeRequest,
                userMessage.messageId()
        );
        if (memoryDecision.requiresConfirmation()) {
            AssistantMessageVO assistantMessage = saveAssistantMessage(
                    currentUser.userId(),
                    safeRequest,
                    new AssistantExecutionResult(memoryDecision.assistantReply(), null, List.of())
            );
            return new AssistantChatResponse(
                    safeRequest.sessionId(),
                    assistantMessage.messageId(),
                    assistantMessage.content(),
                    safeRequest.toolMode(),
                    safeRequest.groupId(),
                    List.of()
            );
        }
        AssistantChatRequest effectiveRequest = withMessage(safeRequest, memoryDecision.effectiveUserMessage());
        AssistantExecutionResult executionResult = executeAssistant(
                request,
                currentUser.userId(),
                effectiveRequest
        );
        AssistantMessageVO assistantMessage = saveAssistantMessage(
                currentUser.userId(),
                effectiveRequest,
                executionResult
        );
        return new AssistantChatResponse(
                safeRequest.sessionId(),
                assistantMessage.messageId(),
                assistantMessage.content(),
                safeRequest.toolMode(),
                safeRequest.groupId(),
                executionResult.citations()
        );
    }

    public void streamChat(
            HttpServletRequest request,
            AssistantChatRequest chatRequest,
            AssistantStreamEventEmitter eventEmitter
    ) {
        if (assistantTurnRequestService != null) {
            streamChatIdempotently(request, chatRequest, eventEmitter, new ModelCallCancellation(), null);
            return;
        }
        streamChat(request, chatRequest, eventEmitter, new ModelCallCancellation(), (deltaEmitter, effectiveUserMessage) ->
                assistantAgentFacade.streamChat(
                        currentUserService.requireBusinessUser(request).userId(),
                        chatRequest.sessionId(),
                        chatRequest.toolMode(),
                        chatRequest.groupId(),
                        effectiveUserMessage,
                        deltaEmitter
                ));
    }

    /** Performs the authentication part of stream admission before an SSE response is committed. */
    public void requireBusinessStreamUser(HttpServletRequest request) {
        currentUserService.requireBusinessUser(request);
    }

    public void streamChat(
            HttpServletRequest request,
            AssistantChatRequest chatRequest,
            AssistantStreamEventEmitter eventEmitter,
            ModelCallCancellation cancellation
    ) {
        if (assistantTurnRequestService != null) {
            streamChatIdempotently(request, chatRequest, eventEmitter, cancellation, null);
            return;
        }
        streamChat(request, chatRequest, eventEmitter, cancellation, (deltaEmitter, effectiveUserMessage) ->
                assistantAgentFacade.streamChat(
                        currentUserService.requireBusinessUser(request).userId(),
                        chatRequest.sessionId(),
                        chatRequest.toolMode(),
                        chatRequest.groupId(),
                        effectiveUserMessage,
                        deltaEmitter,
                        cancellation
                ));
    }

    public void streamChat(
            HttpServletRequest request,
            AssistantChatRequest chatRequest,
            AssistantStreamEventEmitter eventEmitter,
            ChatStreamExecutor streamExecutor
    ) {
        streamChat(request, chatRequest, eventEmitter, new ModelCallCancellation(), streamExecutor);
    }

    public void streamChat(
            HttpServletRequest request,
            AssistantChatRequest chatRequest,
            AssistantStreamEventEmitter eventEmitter,
            ModelCallCancellation cancellation,
            ChatStreamExecutor streamExecutor
    ) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        AssistantChatRequest safeRequest = requireChatRequest(chatRequest);
        if (assistantTurnRequestService != null) {
            streamChatIdempotently(request, safeRequest, eventEmitter, cancellation, streamExecutor);
            return;
        }
        AssistantMessageVO userMessage = saveUserMessage(currentUser.userId(), safeRequest);
        AssistantRuntimeMemoryService.AssistantRuntimeMemoryDecision memoryDecision = beforeAnswer(
                currentUser.userId(),
                safeRequest,
                userMessage.messageId()
        );

        eventEmitter.emit(AssistantChatStreamEvent.start(
                safeRequest.sessionId(),
                safeRequest.toolMode(),
                safeRequest.groupId()
        ));

        if (memoryDecision.requiresConfirmation()) {
            eventEmitter.emit(AssistantChatStreamEvent.delta(
                    safeRequest.sessionId(),
                    safeRequest.toolMode(),
                    safeRequest.groupId(),
                    memoryDecision.assistantReply()
            ));
            AssistantMessageVO assistantMessage = saveAssistantMessage(
                    currentUser.userId(),
                    safeRequest,
                    new AssistantExecutionResult(memoryDecision.assistantReply(), null, List.of())
            );
            eventEmitter.emit(AssistantChatStreamEvent.done(
                    safeRequest.sessionId(),
                    safeRequest.toolMode(),
                    safeRequest.groupId(),
                    assistantMessage.messageId(),
                    memoryDecision.assistantReply(),
                    List.of()
            ));
            return;
        }

        AssistantChatRequest effectiveRequest = withMessage(safeRequest, memoryDecision.effectiveUserMessage());
        AssistantExecutionResult executionResult = executeAssistantStreaming(
                request,
                currentUser.userId(),
                effectiveRequest,
                delta -> eventEmitter.emit(AssistantChatStreamEvent.delta(
                        safeRequest.sessionId(),
                        safeRequest.toolMode(),
                        safeRequest.groupId(),
                        delta
                )),
                streamExecutor
        );
        if (cancellation.isRequested()) {
            throw new BusinessException(cancellation.isHardTimedOut() ? "CALL_TIMEOUT" : "CALL_CANCELLED");
        }
        // 保存助手回复
        AssistantMessageVO assistantMessage = saveAssistantMessage(
                currentUser.userId(),
                effectiveRequest,
                executionResult
        );

        // 发送done事件，表示流式回答结束
        eventEmitter.emit(AssistantChatStreamEvent.done(
                safeRequest.sessionId(),
                safeRequest.toolMode(),
                safeRequest.groupId(),
                assistantMessage.messageId(),
                executionResult.reply(),
                executionResult.citations()
        ));
    }

    private void streamChatIdempotently(
            HttpServletRequest request,
            AssistantChatRequest requestBody,
            AssistantStreamEventEmitter eventEmitter,
            ModelCallCancellation cancellation,
            ChatStreamExecutor streamExecutor
    ) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        AssistantChatRequest initialRequest = requireChatRequest(requestBody);
        AssistantTurnRequestService.PreparedTurn preparedTurn = assistantTurnRequestService.prepare(currentUser.userId(), initialRequest);
        if (!preparedTurn.isAccepted()) {
            emitReplay(eventEmitter, preparedTurn);
            return;
        }
        AssistantChatRequest safeRequest = preparedTurn.request();
        try (AssistantTurnGuard.TurnPermit ignored = guardNewSessionTurn(initialRequest, safeRequest)) {
            eventEmitter.emit(AssistantChatStreamEvent.start(safeRequest.sessionId(), safeRequest.toolMode(), safeRequest.groupId()));
            AssistantRuntimeMemoryService.AssistantRuntimeMemoryDecision memoryDecision = beforeAnswer(
                    currentUser.userId(), safeRequest, preparedTurn.userMessageId());
            AssistantExecutionResult executionResult;
            if (memoryDecision.requiresConfirmation()) {
                executionResult = new AssistantExecutionResult(memoryDecision.assistantReply(), null, List.of());
                eventEmitter.emit(AssistantChatStreamEvent.delta(
                        safeRequest.sessionId(), safeRequest.toolMode(), safeRequest.groupId(), executionResult.reply()
                ));
            } else if (streamExecutor != null) {
                AssistantChatRequest effectiveRequest = withMessage(safeRequest, memoryDecision.effectiveUserMessage());
                executionResult = executeAssistantStreaming(
                        request, currentUser.userId(), effectiveRequest,
                        delta -> eventEmitter.emit(AssistantChatStreamEvent.delta(
                                safeRequest.sessionId(), safeRequest.toolMode(), safeRequest.groupId(), delta
                        )), streamExecutor
                );
            } else {
                requireKnowledgeBaseReadableIfNeeded(request, safeRequest);
                AssistantChatRequest effectiveRequest = withMessage(safeRequest, memoryDecision.effectiveUserMessage());
                AssistantAgentResult agentResult = assistantAgentFacade.streamChat(
                        currentUser.userId(), effectiveRequest.sessionId(), effectiveRequest.toolMode(), effectiveRequest.groupId(),
                        effectiveRequest.message(),
                        delta -> eventEmitter.emit(AssistantChatStreamEvent.delta(
                                safeRequest.sessionId(), safeRequest.toolMode(), safeRequest.groupId(), delta
                        )), cancellation, preparedTurn.turnId(), safeRequest.requestId(), preparedTurn.userMessageId()
                );
                executionResult = new AssistantExecutionResult(agentResult.reply(), null, agentResult.citations());
            }
            if (cancellation.isRequested()) {
                throw new BusinessException(cancellation.isHardTimedOut() ? "CALL_TIMEOUT" : "CALL_CANCELLED");
            }
            AssistantMessageVO assistantMessage = saveAssistantMessage(currentUser.userId(), safeRequest, executionResult);
            assistantTurnRequestService.markCompleted(currentUser.userId(), safeRequest.requestId(), assistantMessage.messageId());
            eventEmitter.emit(AssistantChatStreamEvent.done(
                    safeRequest.sessionId(), safeRequest.toolMode(), safeRequest.groupId(), assistantMessage.messageId(),
                    assistantMessage.content(), executionResult.citations()
            ));
        } catch (RuntimeException exception) {
            assistantTurnRequestService.markFailed(currentUser.userId(), safeRequest.requestId(), failureCode(exception));
            throw exception;
        }
    }

    private void emitReplay(AssistantStreamEventEmitter eventEmitter, AssistantTurnRequestService.PreparedTurn preparedTurn) {
        if ("FAILED".equals(preparedTurn.replayStatus())) {
            throw new BusinessException("REQUEST_RETRY_REQUIRES_NEW_ID");
        }
        eventEmitter.emit(AssistantChatStreamEvent.start(
                preparedTurn.replaySessionId(), preparedTurn.replayToolMode(), preparedTurn.replayGroupId()
        ));
        if ("COMPLETED".equals(preparedTurn.replayStatus())) {
            eventEmitter.emit(AssistantChatStreamEvent.done(
                    preparedTurn.replaySessionId(), preparedTurn.replayToolMode(), preparedTurn.replayGroupId(),
                    preparedTurn.replayMessageId(), preparedTurn.replayReply(), List.of()
            ));
            return;
        }
        eventEmitter.emit(AssistantChatStreamEvent.error(
                preparedTurn.replaySessionId(), preparedTurn.replayToolMode(), preparedTurn.replayGroupId(), "REQUEST_IN_PROGRESS"
        ));
    }

    private AssistantChatRequest requireChatRequest(AssistantChatRequest chatRequest) {
        if (chatRequest == null) {
            throw new BusinessException("聊天请求不能为空");
        }
        if (chatRequest.toolMode() == AssistantToolMode.CHAT && chatRequest.groupId() != null) {
            throw new BusinessException("CHAT 模式不允许传 groupId");
        }
        if (chatRequest.toolMode() == AssistantToolMode.KB_SEARCH && chatRequest.groupId() == null) {
            throw new BusinessException("KB_SEARCH 模式必须传 groupId");
        }
        return chatRequest;
    }

    private AssistantRuntimeMemoryService.AssistantRuntimeMemoryDecision beforeAnswer(
            Long userId,
            AssistantChatRequest safeRequest,
            Long userMessageId
    ) {
        return assistantRuntimeMemoryService.beforeAnswer(
                userId,
                safeRequest.sessionId(),
                userMessageId,
                safeRequest.toolMode(),
                safeRequest.groupId(),
                safeRequest.message()
        );
    }

    private AssistantChatResponse chatIdempotently(
            HttpServletRequest request,
            Long userId,
            AssistantChatRequest requestBody
    ) {
        AssistantTurnRequestService.PreparedTurn preparedTurn = assistantTurnRequestService.prepare(userId, requestBody);
        if (!preparedTurn.isAccepted()) {
            return replayResponse(preparedTurn);
        }
        AssistantChatRequest safeRequest = preparedTurn.request();
        try (AssistantTurnGuard.TurnPermit ignored = guardNewSessionTurn(requestBody, safeRequest)) {
            AssistantRuntimeMemoryService.AssistantRuntimeMemoryDecision memoryDecision = beforeAnswer(
                    userId, safeRequest, preparedTurn.userMessageId());
            AssistantExecutionResult executionResult;
            if (memoryDecision.requiresConfirmation()) {
                executionResult = new AssistantExecutionResult(memoryDecision.assistantReply(), null, List.of());
            } else {
                AssistantChatRequest effectiveRequest = withMessage(safeRequest, memoryDecision.effectiveUserMessage());
                executionResult = executeAssistant(request, userId, effectiveRequest, preparedTurn);
            }
            AssistantMessageVO assistantMessage = saveAssistantMessage(userId, safeRequest, executionResult);
            assistantTurnRequestService.markCompleted(userId, safeRequest.requestId(), assistantMessage.messageId());
            return new AssistantChatResponse(
                    safeRequest.sessionId(), assistantMessage.messageId(), assistantMessage.content(), safeRequest.toolMode(),
                    safeRequest.groupId(), executionResult.citations(), preparedTurn.turnId(), "COMPLETED"
            );
        } catch (RuntimeException exception) {
            assistantTurnRequestService.markFailed(userId, safeRequest.requestId(), failureCode(exception));
            throw exception;
        }
    }

    /**
     * Existing sessions are admitted by the controller before any request/message write so a busy
     * request cannot create a new user message. A no-session request only receives its stable
     * session id during prepare, so it acquires the same shared guard here before model execution.
     */
    private AssistantTurnGuard.TurnPermit guardNewSessionTurn(
            AssistantChatRequest originalRequest,
            AssistantChatRequest preparedRequest
    ) {
        return originalRequest.sessionId() == null
                ? assistantTurnGuard.acquire(preparedRequest.sessionId())
                : AssistantTurnGuard.noOp();
    }

    private AssistantChatResponse replayResponse(AssistantTurnRequestService.PreparedTurn preparedTurn) {
        if ("FAILED".equals(preparedTurn.replayStatus())) {
            throw new BusinessException("REQUEST_RETRY_REQUIRES_NEW_ID");
        }
        return new AssistantChatResponse(
                preparedTurn.replaySessionId(), preparedTurn.replayMessageId(), preparedTurn.replayReply(),
                preparedTurn.replayToolMode(), preparedTurn.replayGroupId(), List.of(),
                preparedTurn.turnId(), preparedTurn.replayStatus()
        );
    }

    private AssistantChatRequest withMessage(AssistantChatRequest request, String effectiveUserMessage) {
        String message = effectiveUserMessage == null || effectiveUserMessage.isBlank()
                ? request.message()
                : effectiveUserMessage;
        return new AssistantChatRequest(
                request.sessionId(),
                message,
                request.toolMode(),
                request.groupId(),
                request.requestId(),
                request.modelConnectionId(),
                request.modelId(),
                request.instructionProfileId()
        );
    }

    private AssistantExecutionResult executeAssistant(
            HttpServletRequest request,
            Long userId,
            AssistantChatRequest safeRequest
    ) {
        return executeAssistant(request, userId, safeRequest, null);
    }

    private AssistantExecutionResult executeAssistant(
            HttpServletRequest request,
            Long userId,
            AssistantChatRequest safeRequest,
            AssistantTurnRequestService.PreparedTurn preparedTurn
    ) {
        requireKnowledgeBaseReadableIfNeeded(request, safeRequest);
        // CHAT 和 KB_SEARCH 都统一走 Agent。KB_SEARCH 模式下，Agent 会通过知识库 Tool 获取证据。
        AssistantAgentResult agentResult = preparedTurn == null
                ? assistantAgentFacade.chat(userId, safeRequest.sessionId(), safeRequest.toolMode(), safeRequest.groupId(), safeRequest.message())
                : assistantAgentFacade.chat(userId, safeRequest.sessionId(), safeRequest.toolMode(), safeRequest.groupId(),
                safeRequest.message(), preparedTurn.turnId(), safeRequest.requestId(), preparedTurn.userMessageId());
        return new AssistantExecutionResult(agentResult.reply(), null, agentResult.citations());
    }

    private AssistantExecutionResult executeAssistantStreaming(
            HttpServletRequest request,
            Long userId,
            AssistantChatRequest safeRequest,
            Consumer<String> deltaConsumer,
            ChatStreamExecutor streamExecutor
    ) {
        if (safeRequest.toolMode() == AssistantToolMode.CHAT) {
            // 仅对话流式模式下，delta 直接来自 AgentFacade.streamChat 的模型流输出。
            AssistantAgentResult agentResult = streamExecutor.execute(deltaConsumer, safeRequest.message());
            return new AssistantExecutionResult(agentResult.reply(), null, agentResult.citations());
        }
        requireKnowledgeBaseReadableIfNeeded(request, safeRequest);
        AssistantAgentResult agentResult = streamExecutor.execute(deltaConsumer, safeRequest.message());
        return new AssistantExecutionResult(agentResult.reply(), null, agentResult.citations());
    }

    private AssistantMessageVO saveUserMessage(Long userId, AssistantChatRequest safeRequest) {
        return assistantConversationService.saveUserMessage(
                userId,
                new AssistantMessageCreateDTO(
                        safeRequest.sessionId(),
                        safeRequest.toolMode(),
                        safeRequest.groupId(),
                        safeRequest.message(),
                        null
                )
        );
    }

    private AssistantMessageVO saveAssistantMessage(
            Long userId,
            AssistantChatRequest safeRequest,
            AssistantExecutionResult executionResult
    ) {
        return assistantConversationService.saveAssistantMessage(
                userId,
                new AssistantMessageCreateDTO(
                        safeRequest.sessionId(),
                        safeRequest.toolMode(),
                        safeRequest.groupId(),
                        executionResult.reply(),
                        executionResult.structuredPayload()
                )
        );
    }

    private void requireKnowledgeBaseReadableIfNeeded(HttpServletRequest request, AssistantChatRequest safeRequest) {
        if (safeRequest.toolMode() == AssistantToolMode.KB_SEARCH) {
            groupMembershipService.requireGroupReadable(request, safeRequest.groupId());
        }
    }

    private record AssistantExecutionResult(
            String reply,
            String structuredPayload,
            List<com.dong.ddrag.qa.model.vo.AskQuestionResponse.Citation> citations
    ) {
    }

    @FunctionalInterface
    public interface ChatStreamExecutor {

        AssistantAgentResult execute(Consumer<String> deltaConsumer, String effectiveUserMessage);
    }

    private String failureCode(RuntimeException exception) {
        if (exception instanceof BusinessException businessException && businessException.getMessage() != null
                && businessException.getMessage().length() <= 64) {
            return businessException.getMessage();
        }
        return "ASSISTANT_FAILED";
    }
}
