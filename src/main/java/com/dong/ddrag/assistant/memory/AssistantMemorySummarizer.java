package com.dong.ddrag.assistant.memory;

import com.dong.ddrag.assistant.model.entity.AssistantMessageEntity;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.common.exception.BusinessException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.dong.ddrag.modelplatform.model.enums.ModelScenario;
import com.dong.ddrag.modelplatform.runtime.GovernedChatModel;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationContext;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationDispatcher;
import com.dong.ddrag.modelplatform.runtime.ModelRuntimeService;

@Service
public class AssistantMemorySummarizer {

    private final PromptTemplate assistantSessionMemoryPromptTemplate;
    private final PromptTemplate assistantCompactSummaryPromptTemplate;
    private final PromptTemplate assistantRuntimeCompactPromptTemplate;
    private final ModelRuntimeService modelRuntimeService;
    private final ModelInvocationDispatcher invocationDispatcher;

    public AssistantMemorySummarizer(
            @Qualifier("assistantSessionMemoryPromptTemplate") PromptTemplate assistantSessionMemoryPromptTemplate,
            @Qualifier("assistantCompactSummaryPromptTemplate") PromptTemplate assistantCompactSummaryPromptTemplate,
            @Qualifier("assistantRuntimeCompactPromptTemplate") PromptTemplate assistantRuntimeCompactPromptTemplate,
            ModelRuntimeService modelRuntimeService,
            ModelInvocationDispatcher invocationDispatcher
    ) {
        this.assistantSessionMemoryPromptTemplate = assistantSessionMemoryPromptTemplate;
        this.assistantCompactSummaryPromptTemplate = assistantCompactSummaryPromptTemplate;
        this.assistantRuntimeCompactPromptTemplate = assistantRuntimeCompactPromptTemplate;
        this.modelRuntimeService = modelRuntimeService;
        this.invocationDispatcher = invocationDispatcher;
    }

    public String summarizeSessionMemory(
            Long userId, Long sessionId,
            String existingSessionMemory,
            List<AssistantMessageEntity> newMessages,
            AssistantToolMode toolMode,
            Long groupId
    ) {
        return callForText(userId, sessionId, ModelScenario.SESSION_SUMMARY, assistantSessionMemoryPromptTemplate.create(Map.of(
                "existingSessionMemory", defaultText(existingSessionMemory),
                "newMessages", formatMessages(newMessages),
                "currentToolMode", toolMode == null ? "UNKNOWN" : toolMode.name(),
                "currentGroupId", groupId == null ? "NONE" : String.valueOf(groupId)
        )), "生成 session memory 失败");
    }

    public String summarizeCompactSummary(
            Long userId, Long sessionId,
            String existingCompactSummary,
            String sessionMemory,
            List<AssistantMessageEntity> messagesToCompact
    ) {
        return callForText(userId, sessionId, ModelScenario.SESSION_SUMMARY, assistantCompactSummaryPromptTemplate.create(Map.of(
                "existingCompactSummary", defaultText(existingCompactSummary),
                "sessionMemory", defaultText(sessionMemory),
                "messagesToCompact", formatMessages(messagesToCompact)
        )), "生成 compact summary 失败");
    }

    public String summarizeRuntimeContext(Long userId, Long sessionId,
            String compactSummary,
            String sessionMemory,
            String recentMessages,
            String currentQuestion
    ) {
        return callForText(userId, sessionId, ModelScenario.SESSION_SUMMARY, assistantRuntimeCompactPromptTemplate.create(Map.of(
                "compactSummary", defaultText(compactSummary),
                "sessionMemory", defaultText(sessionMemory),
                "recentMessages", defaultText(recentMessages),
                "currentQuestion", defaultText(currentQuestion)
        )), "生成运行时压缩上下文失败");
    }

    String formatMessages(List<AssistantMessageEntity> messages) {
        if (messages == null || messages.isEmpty()) {
            return "NONE";
        }
        StringBuilder builder = new StringBuilder();
        for (AssistantMessageEntity message : messages) {
            if (message == null) {
                continue;
            }
            String content = normalize(message.getContent());
            if (content.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(System.lineSeparator());
            }
            builder.append('[')
                    .append(defaultText(message.getRole()))
                    .append("] ")
                    .append(content);
        }
        return builder.isEmpty() ? "NONE" : builder.toString();
    }

    private String callForText(Long userId, Long sessionId, ModelScenario scenario, Prompt prompt, String errorMessage) {
        try {
            String content = chatClient(userId, sessionId, scenario).prompt(prompt)
                    .call()
                    .content();
            String normalized = normalize(content);
            if (normalized.isEmpty()) {
                throw new BusinessException(errorMessage + "，模型返回为空");
            }
            return normalized;
        } catch (RuntimeException exception) {
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(errorMessage, exception);
        }
    }

    private ChatClient chatClient(Long userId, Long sessionId, ModelScenario scenario) {
        ModelInvocationContext context = modelRuntimeService.resolveScenario(userId, scenario,
                new ModelRuntimeService.InvocationCorrelation(UUID.randomUUID().toString(), null, null, null, sessionId));
        return ChatClient.builder(new GovernedChatModel(context, invocationDispatcher)).build();
    }

    private String normalize(String content) {
        if (content == null) {
            return "";
        }
        return content.replace("\r\n", "\n").trim();
    }

    private String defaultText(String value) {
        return normalize(value).isEmpty() ? "NONE" : normalize(value);
    }
}
