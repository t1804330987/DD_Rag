package com.dong.ddrag.assistant.memory.runtime;

import com.dong.ddrag.assistant.model.vo.message.AssistantMessageVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.dong.ddrag.modelplatform.model.enums.ModelScenario;
import com.dong.ddrag.modelplatform.runtime.GovernedChatModel;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationContext;
import com.dong.ddrag.modelplatform.runtime.ModelInvocationDispatcher;
import com.dong.ddrag.modelplatform.runtime.ModelRuntimeService;

@Service
public class AssistantRuntimeMemoryPromptExtractor implements AssistantRuntimeMemoryExtractor {

    private static final int MAX_CHANGES = 3;

    private final PromptTemplate promptTemplate;
    private final ObjectMapper objectMapper;
    private final ModelRuntimeService modelRuntimeService;
    private final ModelInvocationDispatcher invocationDispatcher;

    public AssistantRuntimeMemoryPromptExtractor(
            @Qualifier("assistantRuntimeMemoryExtractionPromptTemplate") PromptTemplate promptTemplate,
            ObjectMapper objectMapper,
            ModelRuntimeService modelRuntimeService,
            ModelInvocationDispatcher invocationDispatcher
    ) {
        this.promptTemplate = promptTemplate;
        this.objectMapper = objectMapper;
        this.modelRuntimeService = modelRuntimeService;
        this.invocationDispatcher = invocationDispatcher;
    }

    @Override
    public List<AssistantRuntimeMemoryChange> extract(
            Long userId,
            Long sessionId,
            AssistantRuntimeMemoryState state,
            List<AssistantMessageVO> recentMessages,
            String currentUserMessage
    ) {
        Prompt prompt = promptTemplate.create(Map.of(
                "existingConclusions", formatConclusions(state),
                "recentMessages", formatRecentMessages(recentMessages),
                "currentUserMessage", currentUserMessage == null ? "" : currentUserMessage
        ));
        String content = chatClient(userId, sessionId).prompt(prompt).call().content();
        ExtractorResponse response = parseResponse(content);
        List<AssistantRuntimeMemoryChange> changes = response.changes() == null
                ? List.of()
                : response.changes().stream().limit(MAX_CHANGES).toList();
        validateChanges(state == null ? AssistantRuntimeMemoryState.empty() : state, changes);
        return changes;
    }

    @Override
    public List<AssistantRuntimeMemoryChange> extract(AssistantRuntimeMemoryState state,
                                                       List<AssistantMessageVO> recentMessages,
                                                       String currentUserMessage) {
        throw new IllegalStateException("运行时记忆提取必须提供用户和会话上下文");
    }

    private ChatClient chatClient(Long userId, Long sessionId) {
        ModelInvocationContext context = modelRuntimeService.resolveScenario(userId,
                ModelScenario.RUNTIME_MEMORY_EXTRACTION,
                new ModelRuntimeService.InvocationCorrelation(UUID.randomUUID().toString(), null, null, null, sessionId));
        return ChatClient.builder(new GovernedChatModel(context, invocationDispatcher)).build();
    }

    private ExtractorResponse parseResponse(String content) {
        try {
            return objectMapper.readValue(content, ExtractorResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("runtime memory extractor 返回非法 JSON", exception);
        }
    }

    private void validateChanges(AssistantRuntimeMemoryState state, List<AssistantRuntimeMemoryChange> changes) {
        Set<String> existingKeyIds = state.conclusions().stream()
                .map(AssistantRuntimeMemoryState.Conclusion::keyId)
                .collect(java.util.stream.Collectors.toSet());
        for (AssistantRuntimeMemoryChange change : changes) {
            if (change == null || change.action() == null || change.action() == AssistantRuntimeMemoryAction.NOOP) {
                continue;
            }
            if (change.action() == AssistantRuntimeMemoryAction.ADD) {
                requireText(change.keyLabel(), "ADD 必须包含 keyLabel");
                requireText(change.value(), "ADD 必须包含 value");
            }
            if (change.action() == AssistantRuntimeMemoryAction.REPLACE) {
                requireExistingTarget(existingKeyIds, change.targetKeyId());
                requireText(change.value(), "REPLACE 必须包含 value");
            }
            if (change.action() == AssistantRuntimeMemoryAction.REVOKE) {
                requireExistingTarget(existingKeyIds, change.targetKeyId());
            }
            if (change.action() == AssistantRuntimeMemoryAction.ASK_CONFIRMATION) {
                requireText(change.confirmationQuestion(), "ASK_CONFIRMATION 必须包含 confirmationQuestion");
                requireText(change.value(), "ASK_CONFIRMATION 必须包含 value");
            }
        }
    }

    private void requireExistingTarget(Set<String> existingKeyIds, String targetKeyId) {
        if (targetKeyId == null || !existingKeyIds.contains(targetKeyId)) {
            throw new IllegalArgumentException("REPLACE/REVOKE 必须引用已有 targetKeyId");
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private String formatConclusions(AssistantRuntimeMemoryState state) {
        AssistantRuntimeMemoryState safeState = state == null ? AssistantRuntimeMemoryState.empty() : state;
        if (safeState.conclusions().isEmpty()) {
            return "NONE";
        }
        StringBuilder builder = new StringBuilder();
        for (AssistantRuntimeMemoryState.Conclusion conclusion : safeState.conclusions()) {
            if (conclusion == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(System.lineSeparator());
            }
            builder.append("- ")
                    .append(conclusion.keyId())
                    .append(" | ")
                    .append(conclusion.keyLabel())
                    .append(" = ")
                    .append(conclusion.activeValue() == null ? "已废弃，暂无替代" : conclusion.activeValue());
        }
        return builder.isEmpty() ? "NONE" : builder.toString();
    }

    private String formatRecentMessages(List<AssistantMessageVO> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return "NONE";
        }
        StringBuilder builder = new StringBuilder();
        for (AssistantMessageVO message : recentMessages) {
            if (message == null || message.content() == null || message.content().isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(System.lineSeparator());
            }
            builder.append('[')
                    .append(message.role() == null ? "UNKNOWN" : message.role().name())
                    .append("] ")
                    .append(message.content().trim());
        }
        return builder.isEmpty() ? "NONE" : builder.toString();
    }

    private record ExtractorResponse(List<AssistantRuntimeMemoryChange> changes) {
    }
}
