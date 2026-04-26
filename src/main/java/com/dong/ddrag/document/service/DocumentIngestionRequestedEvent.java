package com.dong.ddrag.document.service;

public record DocumentIngestionRequestedEvent(
        Long documentId,
        Long groupId
) {
}
