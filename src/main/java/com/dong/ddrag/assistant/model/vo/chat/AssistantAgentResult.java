package com.dong.ddrag.assistant.model.vo.chat;

import com.dong.ddrag.qa.model.vo.AskQuestionResponse;

import java.util.List;

public record AssistantAgentResult(
        String reply,
        List<AskQuestionResponse.Citation> citations
) {

    public AssistantAgentResult {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    public static AssistantAgentResult withoutCitations(String reply) {
        return new AssistantAgentResult(reply, List.of());
    }
}
