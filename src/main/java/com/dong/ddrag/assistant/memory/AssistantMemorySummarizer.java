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

@Service
public class AssistantMemorySummarizer {

    private final ChatClient chatClient;
    private final PromptTemplate assistantSessionMemoryPromptTemplate;
    private final PromptTemplate assistantCompactSummaryPromptTemplate;
    private final PromptTemplate assistantRuntimeCompactPromptTemplate;

    public AssistantMemorySummarizer(
            ChatClient.Builder chatClientBuilder,
            @Qualifier("assistantSessionMemoryPromptTemplate") PromptTemplate assistantSessionMemoryPromptTemplate,
            @Qualifier("assistantCompactSummaryPromptTemplate") PromptTemplate assistantCompactSummaryPromptTemplate,
            @Qualifier("assistantRuntimeCompactPromptTemplate") PromptTemplate assistantRuntimeCompactPromptTemplate
    ) {
        this.chatClient = chatClientBuilder.build();
        this.assistantSessionMemoryPromptTemplate = assistantSessionMemoryPromptTemplate;
        this.assistantCompactSummaryPromptTemplate = assistantCompactSummaryPromptTemplate;
        this.assistantRuntimeCompactPromptTemplate = assistantRuntimeCompactPromptTemplate;
    }

    public String summarizeSessionMemory(
            String existingSessionMemory,
            List<AssistantMessageEntity> newMessages,
            AssistantToolMode toolMode,
            Long groupId
    ) {
        return callForText(assistantSessionMemoryPromptTemplate.create(Map.of(
                "existingSessionMemory", defaultText(existingSessionMemory),
                "newMessages", formatMessages(newMessages),
                "currentToolMode", toolMode == null ? "UNKNOWN" : toolMode.name(),
                "currentGroupId", groupId == null ? "NONE" : String.valueOf(groupId)
        )), "生成 session memory 失败");
    }

    public String summarizeCompactSummary(
            String existingCompactSummary,
            String sessionMemory,
            List<AssistantMessageEntity> messagesToCompact
    ) {
        return callForText(assistantCompactSummaryPromptTemplate.create(Map.of(
                "existingCompactSummary", defaultText(existingCompactSummary),
                "sessionMemory", defaultText(sessionMemory),
                "messagesToCompact", formatMessages(messagesToCompact)
        )), "生成 compact summary 失败");
    }

    public String summarizeRuntimeContext(
            String compactSummary,
            String sessionMemory,
            String recentMessages,
            String currentQuestion
    ) {
        return callForText(assistantRuntimeCompactPromptTemplate.create(Map.of(
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

    private String callForText(Prompt prompt, String errorMessage) {
        try {
            String content = chatClient.prompt(prompt)
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
