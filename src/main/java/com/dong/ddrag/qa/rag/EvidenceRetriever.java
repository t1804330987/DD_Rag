package com.dong.ddrag.qa.rag;

public interface EvidenceRetriever {

    int DEFAULT_TOP_K = 5;

    default RetrievedEvidenceBundle retrieve(Long groupId, String question) {
        return retrieve(groupId, question, DEFAULT_TOP_K);
    }

    default RetrievedEvidenceBundle retrieve(Long userId, Long groupId, String question) {
        return retrieve(userId, groupId, question, DEFAULT_TOP_K);
    }

    default RetrievedEvidenceBundle retrieve(Long userId, Long groupId, String question, int topK) {
        return retrieve(groupId, question, topK);
    }

    RetrievedEvidenceBundle retrieve(Long groupId, String question, int topK);
}
