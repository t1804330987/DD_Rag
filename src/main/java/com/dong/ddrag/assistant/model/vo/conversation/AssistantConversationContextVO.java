package com.dong.ddrag.assistant.model.vo.conversation;

import com.dong.ddrag.assistant.model.vo.message.AssistantMessageVO;

import java.util.List;

public record AssistantConversationContextVO(
        List<AssistantMessageVO> recentMessages
) {
}
