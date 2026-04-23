package com.dong.ddrag.assistant.model.vo.session;

import java.time.LocalDateTime;

public record AssistantSessionDetailVO(
        Long sessionId,
        String title,
        String status,
        LocalDateTime lastMessageAt,
        LocalDateTime createdAt
) {
}
