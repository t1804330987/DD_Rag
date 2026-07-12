package com.dong.ddrag.assistant.model.dto.chat;

import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

public record AssistantChatRequest(
        @Positive(message = "sessionId 非法")
        Long sessionId,
        @NotBlank(message = "message 不能为空")
        @Size(max = 4000, message = "message 长度不能超过 4000")
        String message,
        @NotNull(message = "toolMode 不能为空")
        AssistantToolMode toolMode,
        @Positive(message = "groupId 非法")
        Long groupId,
        @NotBlank(message = "requestId 不能为空")
        @Size(max = 128, message = "requestId 长度不能超过 128")
        String requestId,
        @Positive(message = "modelConnectionId 非法")
        Long modelConnectionId,
        @Positive(message = "modelId 非法")
        Long modelId,
        @Positive(message = "instructionProfileId 非法")
        Long instructionProfileId
) {
    public AssistantChatRequest(Long sessionId, String message, AssistantToolMode toolMode, Long groupId, String requestId) {
        this(sessionId, message, toolMode, groupId, requestId, null, null, null);
    }

    public AssistantChatRequest(Long sessionId, String message, AssistantToolMode toolMode, Long groupId) {
        this(sessionId, message, toolMode, groupId, java.util.UUID.randomUUID().toString(), null, null, null);
    }
}
