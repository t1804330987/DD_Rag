package com.dong.ddrag.qa.rag;

public interface EvidenceRetriever {

    int DEFAULT_TOP_K = 5;

    default RetrievedEvidenceBundle retrieve(Long groupId, String question) {
        return retrieve(groupId, question, DEFAULT_TOP_K);
    }

    RetrievedEvidenceBundle retrieve(Long groupId, String question, int topK);
}
