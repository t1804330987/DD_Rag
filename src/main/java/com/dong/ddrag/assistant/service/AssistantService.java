package com.dong.ddrag.assistant.service;

import com.dong.ddrag.assistant.agent.AssistantAgentFacade;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

@Service
public class AssistantService {

    private final AssistantConversationService assistantConversationService;
    private final AssistantAgentFacade assistantAgentFacade;
    private final GroupMembershipService groupMembershipService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public AssistantService(
            AssistantConversationService assistantConversationService,
            AssistantAgentFacade assistantAgentFacade,
            GroupMembershipService groupMembershipService,
            CurrentUserService currentUserService,
            ObjectMapper objectMapper
    ) {
        this.assistantConversationService = assistantConversationService;
        this.assistantAgentFacade = assistantAgentFacade;
        this.groupMembershipService = groupMembershipService;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AssistantChatResponse chat(HttpServletRequest request, AssistantChatRequest chatRequest) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        AssistantChatRequest safeRequest = requireChatRequest(chatRequest);

        // 仅对话模式先落用户消息，再调模型，这样后续 hook 可以基于最新会话状态重建上下文。
        saveUserMessage(currentUser.userId(), safeRequest);
        AssistantExecutionResult executionResult = executeAssistant(
                request,
                currentUser.userId(),
                safeRequest
        );
        AssistantMessageVO assistantMessage = saveAssistantMessage(
                currentUser.userId(),
                safeRequest,
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

    @Transactional
    public void streamChat(
            HttpServletRequest request,
            AssistantChatRequest chatRequest,
            AssistantStreamEventEmitter eventEmitter
    ) {
        streamChat(request, chatRequest, eventEmitter, deltaEmitter ->
                assistantAgentFacade.streamChat(
                        currentUserService.requireBusinessUser(request).userId(),
                        chatRequest.sessionId(),
                        chatRequest.toolMode(),
                        chatRequest.groupId(),
                        chatRequest.message(),
                        deltaEmitter
                ));
    }

    @Transactional
    public void streamChat(
            HttpServletRequest request,
            AssistantChatRequest chatRequest,
            AssistantStreamEventEmitter eventEmitter,
            ChatStreamExecutor streamExecutor
    ) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(request);
        AssistantChatRequest safeRequest = requireChatRequest(chatRequest);
        // 流式场景和同步场景共享同一条主链，只是把模型回复拆成 delta 逐步回传给前端。
        // 用户消息是先落库，再调模型。
        saveUserMessage(currentUser.userId(), safeRequest);

        // 告诉前端：流式回答开始了
        eventEmitter.emit(AssistantChatStreamEvent.start(
                safeRequest.sessionId(),
                safeRequest.toolMode(),
                safeRequest.groupId()
        ));


        AssistantExecutionResult executionResult = executeAssistantStreaming(
                request,
                currentUser.userId(),
                safeRequest,
                // 每当模型吐出一小段文本 delta, 包装为AssistantChatStreamEvent.delta()，再通过 eventEmitter.emit(...) 发给前端
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
                safeRequest,
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
            AssistantAgentResult agentResult = streamExecutor.execute(deltaConsumer);
            return new AssistantExecutionResult(agentResult.reply(), null, agentResult.citations());
        }
        requireKnowledgeBaseReadableIfNeeded(request, safeRequest);
        AssistantAgentResult agentResult = streamExecutor.execute(deltaConsumer);
        return new AssistantExecutionResult(agentResult.reply(), null, agentResult.citations());
    }

    private void saveUserMessage(Long userId, AssistantChatRequest safeRequest) {
        assistantConversationService.saveUserMessage(
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

        AssistantAgentResult execute(Consumer<String> deltaConsumer);
    }
}
