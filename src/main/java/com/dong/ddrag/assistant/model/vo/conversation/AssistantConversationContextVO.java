package com.dong.ddrag.assistant.model.vo.conversation;

import com.dong.ddrag.assistant.model.vo.message.AssistantMessageVO;

import java.util.List;

public record AssistantConversationContextVO(
        String summaryText,
        List<AssistantMessageVO> recentMessages
) {
}
