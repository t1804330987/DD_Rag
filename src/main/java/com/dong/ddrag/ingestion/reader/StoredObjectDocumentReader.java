package com.dong.ddrag.ingestion.reader;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.document.model.entity.DocumentEntity;
import com.dong.ddrag.ingestion.parser.factory.DocumentParserFactory;
import com.dong.ddrag.ingestion.parser.strategy.DocumentParser;
import com.dong.ddrag.storage.service.ObjectStorageService;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StoredObjectDocumentReader implements DocumentReader {

    private static final String MINIO_SOURCE_PREFIX = "minio://";
    private final ObjectStorageService storageService;
    private final DocumentParserFactory parserFactory;
    private final DocumentEntity documentEntity;

    public StoredObjectDocumentReader(
            ObjectStorageService storageService,
            DocumentParserFactory parserFactory,
            DocumentEntity documentEntity
    ) {
        this.storageService = storageService;
        this.parserFactory = parserFactory;
        this.documentEntity = documentEntity;
    }

    @Override
    public List<Document> get() {
        validateDocumentEntity();
        String bucket = resolveBucket();
        String objectKey = documentEntity.getStorageObjectKey();
        DocumentParser parser = parserFactory.getParser(documentEntity.getFileExt());
        try (InputStream inputStream = storageService.getObject(bucket, objectKey)) {
            String content = parser.parse(inputStream);
            return List.of(buildDocument(content, bucket, objectKey));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("读取存储文档失败", exception);
        }
    }

    private void validateDocumentEntity() {
        if (documentEntity == null || documentEntity.getId() == null || documentEntity.getGroupId() == null) {
            throw new BusinessException("读取文档前必须提供 documentId 和 groupId");
        }
    }

    private String resolveBucket() {
        if (StringUtils.hasText(documentEntity.getStorageBucket())) {
            return documentEntity.getStorageBucket();
        }
        return storageService.getDefaultBucket();
    }

    private Document buildDocument(String content, String bucket, String objectKey) {
        return Document.builder()
                .id(String.valueOf(documentEntity.getId()))
                .text(content)
                .metadata(buildMetadata(bucket, objectKey))
                .build();
    }

    private Map<String, Object> buildMetadata(String bucket, String objectKey) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("groupId", documentEntity.getGroupId());
        metadata.put("documentId", documentEntity.getId());
        metadata.put("fileName", documentEntity.getFileName());
        metadata.put("source", MINIO_SOURCE_PREFIX + bucket + "/" + objectKey);
        return metadata;
    }
}
