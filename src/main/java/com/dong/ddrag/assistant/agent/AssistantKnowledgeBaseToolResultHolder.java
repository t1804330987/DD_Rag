package com.dong.ddrag.assistant.agent;

import com.dong.ddrag.qa.model.vo.AskQuestionResponse;

import java.util.List;

public class AssistantKnowledgeBaseToolResultHolder {

    private List<AskQuestionResponse.Citation> citations = List.of();
    private KnowledgeBaseSearchState searchState = KnowledgeBaseSearchState.NOT_STARTED;

    public void recordCitations(List<AskQuestionResponse.Citation> citations) {
        this.citations = citations == null ? List.of() : List.copyOf(citations);
        this.searchState = KnowledgeBaseSearchState.COMPLETED;
    }

    public List<AskQuestionResponse.Citation> currentCitations() {
        return citations;
    }

    public boolean hasCompletedSearch() {
        return searchState == KnowledgeBaseSearchState.COMPLETED;
    }

    enum KnowledgeBaseSearchState {
        NOT_STARTED,
        COMPLETED
    }
}
