package com.dong.ddrag.ingestion.service;

public interface DocumentIngestionProcessor {

    void process(Long documentId, Long groupId);
}
