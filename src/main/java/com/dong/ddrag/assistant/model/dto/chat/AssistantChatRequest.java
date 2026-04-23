package com.dong.ddrag.assistant.model.dto.chat;

import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AssistantChatRequest(
        @NotNull(message = "sessionId 不能为空")
        @Positive(message = "sessionId 非法")
        Long sessionId,
        @NotBlank(message = "message 不能为空")
        @Size(max = 4000, message = "message 长度不能超过 4000")
        String message,
        @NotNull(message = "toolMode 不能为空")
        AssistantToolMode toolMode,
        @Positive(message = "groupId 非法")
        Long groupId
) {
}
