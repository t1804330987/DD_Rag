package com.dong.ddrag.assistant.model.vo.chat;

import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.qa.model.vo.AskQuestionResponse;

import java.util.List;

public record AssistantChatResponse(
        Long sessionId,
        Long messageId,
        String reply,
        AssistantToolMode toolMode,
        Long groupId,
        List<AskQuestionResponse.Citation> citations,
        String turnId,
        String status
) {
    public AssistantChatResponse(
            Long sessionId,
            Long messageId,
            String reply,
            AssistantToolMode toolMode,
            Long groupId,
            List<AskQuestionResponse.Citation> citations
    ) {
        this(sessionId, messageId, reply, toolMode, groupId, citations, null, "COMPLETED");
    }
}
