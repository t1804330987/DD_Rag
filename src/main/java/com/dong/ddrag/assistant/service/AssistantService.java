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
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
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
    }

    public AssistantChatResponse chat(HttpServletRequest request, AssistantChatRequest chatRequest) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        AssistantChatRequest safeRequest = requireChatRequest(chatRequest);

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
        streamChat(request, chatRequest, eventEmitter, (deltaEmitter, effectiveUserMessage) ->
                assistantAgentFacade.streamChat(
                        currentUserService.requireBusinessUser(request).userId(),
                        chatRequest.sessionId(),
                        chatRequest.toolMode(),
                        chatRequest.groupId(),
                        effectiveUserMessage,
                        deltaEmitter
                ));
    }

    public void streamChat(
            HttpServletRequest request,
            AssistantChatRequest chatRequest,
            AssistantStreamEventEmitter eventEmitter,
            ChatStreamExecutor streamExecutor
    ) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        AssistantChatRequest safeRequest = requireChatRequest(chatRequest);
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

    private AssistantChatRequest withMessage(AssistantChatRequest request, String effectiveUserMessage) {
        String message = effectiveUserMessage == null || effectiveUserMessage.isBlank()
                ? request.message()
                : effectiveUserMessage;
        return new AssistantChatRequest(
                request.sessionId(),
                message,
                request.toolMode(),
                request.groupId()
        );
    }

    private AssistantExecutionResult executeAssistant(
            HttpServletRequest request,
            Long userId,
            AssistantChatRequest safeRequest
    ) {
        requireKnowledgeBaseReadableIfNeeded(request, safeRequest);
        // CHAT 和 KB_SEARCH 都统一走 Agent。KB_SEARCH 模式下，Agent 会通过知识库 Tool 获取证据。
        AssistantAgentResult agentResult = assistantAgentFacade.chat(
                userId,
                safeRequest.sessionId(),
                safeRequest.toolMode(),
                safeRequest.groupId(),
                safeRequest.message()
        );
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
}
