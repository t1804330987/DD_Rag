package com.dong.ddrag.assistant.model.vo.session;

import java.time.LocalDateTime;

public record AssistantSessionListItemVO(
        Long sessionId,
        String title,
        LocalDateTime lastMessageAt
) {
}
