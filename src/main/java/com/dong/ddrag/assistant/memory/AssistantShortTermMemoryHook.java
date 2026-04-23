package com.dong.ddrag.assistant.memory;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import com.dong.ddrag.assistant.model.vo.message.AssistantMessageVO;
import com.dong.ddrag.assistant.service.AssistantConversationService;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@HookPositions({HookPosition.BEFORE_MODEL})
public class AssistantShortTermMemoryHook extends MessagesModelHook {

    private static final Logger log = LoggerFactory.getLogger(AssistantShortTermMemoryHook.class);
    private static final int RECENT_MESSAGE_LIMIT = 10;
    private static final int RUNTIME_TOKEN_THRESHOLD = 50000;

    private final AssistantConversationService assistantConversationService;

    public AssistantShortTermMemoryHook(AssistantConversationService assistantConversationService) {
        this.assistantConversationService = assistantConversationService;
    }

    @Override
    public String getName() {
        return "assistant_short_term_memory_hook";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        if (previousMessages == null || previousMessages.isEmpty() || config == null) {
            return new AgentCommand(previousMessages, UpdatePolicy.REPLACE);
        }
        Long userId = metadataAsLong(config, "userId").orElse(null);
        Long sessionId = metadataAsLong(config, "sessionId").orElse(null);
        AssistantToolMode toolMode = metadataAsToolMode(config, "toolMode").orElse(null);
        if (userId == null || sessionId == null || toolMode == null) {
            return new AgentCommand(previousMessages, UpdatePolicy.REPLACE);
        }
        String currentQuestion = extractCurrentQuestion(previousMessages);
        if (currentQuestion == null) {
            return new AgentCommand(previousMessages, UpdatePolicy.REPLACE);
        }
        Long groupId = metadataAsLong(config, "groupId").orElse(null);
        if (log.isDebugEnabled()) {
            log.debug(
                    "ShortTermMemoryHook.beforeModel input. userId={}, sessionId={}, toolMode={}, groupId={}, previousMessages={}",
                    userId,
                    sessionId,
                    toolMode,
                    groupId,
                    summarizeMessages(previousMessages)
            );
        }
        // 这里不是在原 messages 上做增量补丁，而是直接重建一份真正送进模型的上下文：
        // compact summary -> session memory -> recent messages -> current question。
        List<Message> assembledMessages = assembleBeforeModelMessages(
                userId,
                sessionId,
                toolMode,
                groupId,
                currentQuestion,
                previousMessages
        );
        if (log.isDebugEnabled()) {
            log.debug(
                    "ShortTermMemoryHook.beforeModel output. userId={}, sessionId={}, toolMode={}, groupId={}, currentQuestion={}, assembledMessages={}",
                    userId,
                    sessionId,
                    toolMode,
                    groupId,
                    currentQuestion,
                    summarizeMessages(assembledMessages)
            );
        }
        return new AgentCommand(
                assembledMessages,
                UpdatePolicy.REPLACE
        );
    }

    public Object beforeModel(Object ignored) {
        return ignored;
    }

    public List<Message> assembleBeforeModelMessages(
            Long userId,
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId,
            String currentQuestion
    ) {
        return assembleBeforeModelMessages(
                userId,
                sessionId,
                toolMode,
                groupId,
                currentQuestion,
                List.of()
        );
    }

    public List<Message> assembleBeforeModelMessages(
            Long userId,
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId,
            String currentQuestion,
            List<Message> runtimeMessages
    ) {
        List<Message> messages = new ArrayList<>();
        AssistantConversationService.AssistantConversationContext conversationContext =
                assistantConversationService.loadConversationContext(userId, sessionId, RECENT_MESSAGE_LIMIT);
        // compact summary 和 session memory 被降级成系统消息注入，
        // 目的是让模型先看到压缩后的会话状态，再看最近几轮原始消息。
        addSystemMemory(messages, "compact summary", conversationContext.compactSummary());
        addSystemMemory(messages, "session memory", conversationContext.sessionMemory());
        appendRecentMessages(messages, conversationContext.recentMessages(), currentQuestion);
        appendRuntimeToolMessages(messages, runtimeMessages);
        messages.add(new UserMessage(currentQuestion));
        return messages;
    }

    public boolean shouldRuntimeCompact(int estimatedTokens) {
        return estimatedTokens > RUNTIME_TOKEN_THRESHOLD;
    }

    public List<Message> runtimeCompact(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int keepCount = Math.min(3, messages.size());
        return new ArrayList<>(messages.subList(messages.size() - keepCount, messages.size()));
    }

    private void addSystemMemory(List<Message> messages, String label, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        messages.add(new SystemMessage((label + System.lineSeparator() + content).trim()));
    }

    private void appendRecentMessages(
            List<Message> messages,
            List<AssistantMessageVO> recentMessages,
            String currentQuestion
    ) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return;
        }
        int lastIndex = recentMessages.size() - 1;
        for (int index = 0; index < recentMessages.size(); index++) {
            AssistantMessageVO recentMessage = recentMessages.get(index);
            if (recentMessage == null || recentMessage.content() == null || recentMessage.content().isBlank()) {
                continue;
            }
            boolean isCurrentQuestionEcho = index == lastIndex
                    && recentMessage.role() == com.dong.ddrag.assistant.model.enums.AssistantMessageRole.USER
                    && currentQuestion.equals(recentMessage.content().trim());
            if (isCurrentQuestionEcho) {
                // 当前问题会在 assembleBeforeModelMessages 的最后重新追加，这里跳过数据库里的回显，避免重复喂给模型。
                continue;
            }
            switch (recentMessage.role()) {
                case USER -> messages.add(new UserMessage(formatRecentMessage(recentMessage)));
                case ASSISTANT -> messages.add(new AssistantMessage(formatRecentMessage(recentMessage)));
                case TOOL -> messages.add(new AssistantMessage(formatToolMessage(recentMessage.content())));
            }
        }
    }

    private void appendRuntimeToolMessages(List<Message> messages, List<Message> runtimeMessages) {
        if (runtimeMessages == null || runtimeMessages.isEmpty()) {
            return;
        }
        for (Message runtimeMessage : runtimeMessages) {
            if (!(runtimeMessage instanceof ToolResponseMessage toolResponseMessage)) {
                continue;
            }
            if (toolResponseMessage.getResponses() == null || toolResponseMessage.getResponses().isEmpty()) {
                continue;
            }
            for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                if (response == null || response.responseData() == null || response.responseData().isBlank()) {
                    continue;
                }
                messages.add(new AssistantMessage(formatToolMessage(response.responseData())));
            }
        }
    }

    private String formatRecentMessage(AssistantMessageVO message) {
        String mode = message.toolMode() == null ? "UNKNOWN" : message.toolMode().name();
        return ("[历史消息 | 模式：" + mode + "]" + System.lineSeparator() + message.content()).trim();
    }

    private String formatToolMessage(String content) {
        return ("[工具观察]" + System.lineSeparator() + content).trim();
    }

    private Optional<Long> metadataAsLong(RunnableConfig config, String key) {
        try {
            return config.metadata(key)
                    .map(value -> {
                    if (value instanceof Number number) {
                        return number.longValue();
                    }
                    if (value instanceof String stringValue && !stringValue.isBlank()) {
                        return Long.parseLong(stringValue);
                    }
                    return null;
                });
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Optional<AssistantToolMode> metadataAsToolMode(RunnableConfig config, String key) {
        try {
            return config.metadata(key)
                    .map(Object::toString)
                    .filter(value -> !value.isBlank())
                    .map(AssistantToolMode::valueOf);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String extractCurrentQuestion(List<Message> previousMessages) {
        for (int index = previousMessages.size() - 1; index >= 0; index--) {
            Message message = previousMessages.get(index);
            if (message instanceof UserMessage) {
                UserMessage userMessage = (UserMessage) message;
                String text = userMessage.getText();
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    private String summarizeMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "[]";
        }
        List<String> summaries = new ArrayList<>();
        for (int index = 0; index < messages.size(); index++) {
            Message message = messages.get(index);
            if (message == null) {
                summaries.add(index + ":null");
                continue;
            }
            String type = message.getClass().getSimpleName();
            String text = message.getText();
            String normalized = text == null ? "" : text.replace("\r\n", "\n").replace('\n', ' ').trim();
            if (normalized.length() > 200) {
                normalized = normalized.substring(0, 200) + "...";
            }
            summaries.add(index + ":" + type + "[" + normalized + "]");
        }
        return summaries.toString();
    }
}
