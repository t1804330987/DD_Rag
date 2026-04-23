package com.dong.ddrag.assistant.model.vo.message;

import com.dong.ddrag.assistant.model.enums.AssistantMessageRole;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;

import java.time.LocalDateTime;

public record AssistantMessageVO(
        Long messageId,
        Long sessionId,
        AssistantMessageRole role,
        AssistantToolMode toolMode,
        Long groupId,
        String content,
        String structuredPayload,
        LocalDateTime createdAt
) {
}
