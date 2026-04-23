package com.dong.ddrag.assistant.model.vo.tool;

import java.util.List;

public record KnowledgeBaseSearchToolResponse(
        boolean found,
        String reasonCode,
        String reasonMessage,
        String evidenceLevel,
        String evidenceGuidance,
        List<Evidence> evidences,
        List<com.dong.ddrag.qa.model.vo.AskQuestionResponse.Citation> citations
) {

    public KnowledgeBaseSearchToolResponse {
        evidences = evidences == null ? List.of() : List.copyOf(evidences);
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    public record Evidence(
            Long documentId,
            Long chunkId,
            Integer chunkIndex,
            String fileName,
            double score,
            String snippet
    ) {
    }
}
