package com.dong.ddrag.assistant.model.vo.chat;

import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import com.dong.ddrag.qa.model.vo.AskQuestionResponse;

import java.util.List;

public record AssistantChatStreamEvent(
        String event,
        Long sessionId,
        AssistantToolMode toolMode,
        Long groupId,
        String delta,
        Long messageId,
        String reply,
        List<AskQuestionResponse.Citation> citations,
        String error
) {

    public static AssistantChatStreamEvent start(
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId
    ) {
        return new AssistantChatStreamEvent(
                "start",
                sessionId,
                toolMode,
                groupId,
                null,
                null,
                null,
                List.of(),
                null
        );
    }

    public static AssistantChatStreamEvent delta(
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId,
            String delta
    ) {
        return new AssistantChatStreamEvent(
                "delta",
                sessionId,
                toolMode,
                groupId,
                delta,
                null,
                null,
                List.of(),
                null
        );
    }

    public static AssistantChatStreamEvent done(
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId,
            Long messageId,
            String reply,
            List<AskQuestionResponse.Citation> citations
    ) {
        return new AssistantChatStreamEvent(
                "done",
                sessionId,
                toolMode,
                groupId,
                null,
                messageId,
                reply,
                citations == null ? List.of() : citations,
                null
        );
    }

    public static AssistantChatStreamEvent error(
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId,
            String error
    ) {
        return new AssistantChatStreamEvent(
                "error",
                sessionId,
                toolMode,
                groupId,
                null,
                null,
                null,
                List.of(),
                error
        );
    }
}
