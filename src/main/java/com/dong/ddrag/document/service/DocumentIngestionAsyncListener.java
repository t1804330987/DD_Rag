package com.dong.ddrag.document.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DocumentIngestionAsyncListener {

    private final DocumentIngestionAsyncService documentIngestionAsyncService;

    public DocumentIngestionAsyncListener(DocumentIngestionAsyncService documentIngestionAsyncService) {
        this.documentIngestionAsyncService = documentIngestionAsyncService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DocumentIngestionRequestedEvent event) {
        documentIngestionAsyncService.ingestDocument(event.documentId(), event.groupId());
    }
}
