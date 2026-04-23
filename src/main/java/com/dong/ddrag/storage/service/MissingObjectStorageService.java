package com.dong.ddrag.storage.service;

import com.dong.ddrag.common.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@ConditionalOnExpression(
        "'${storage.minio.endpoint:}' == '' "
                + "or '${storage.minio.access-key:}' == '' "
                + "or '${storage.minio.secret-key:}' == ''"
)
public class MissingObjectStorageService implements ObjectStorageService {

    private final String bucket;

    public MissingObjectStorageService(Environment environment) {
        this.bucket = environment.getProperty("storage.minio.bucket", "dd-rag-documents");
    }

    @Override
    public String getDefaultBucket() {
        return bucket;
    }

    @Override
    public InputStream getObject(String bucket, String objectKey) {
        throw new BusinessException("对象存储未配置");
    }

    @Override
    public void putObject(String bucket, String objectKey, InputStream inputStream, long objectSize, String contentType) {
        throw new BusinessException("对象存储未配置");
    }

    @Override
    public void deleteObject(String bucket, String objectKey) {
        throw new BusinessException("对象存储未配置");
    }
}
