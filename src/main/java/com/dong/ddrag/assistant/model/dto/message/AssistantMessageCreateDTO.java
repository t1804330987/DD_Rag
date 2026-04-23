package com.dong.ddrag.assistant.model.dto.message;

import com.dong.ddrag.assistant.model.enums.AssistantToolMode;

public record AssistantMessageCreateDTO(
        Long sessionId,
        AssistantToolMode toolMode,
        Long groupId,
        String content,
        String structuredPayload
) {
}
