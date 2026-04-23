package com.dong.ddrag.storage.service;

import java.io.InputStream;

public interface ObjectStorageService {

    String getDefaultBucket();

    InputStream getObject(String bucket, String objectKey);

    void putObject(String bucket, String objectKey, InputStream inputStream, long objectSize, String contentType);

    void deleteObject(String bucket, String objectKey);
}
