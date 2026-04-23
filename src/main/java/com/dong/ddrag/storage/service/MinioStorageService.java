package com.dong.ddrag.storage.service;

import com.dong.ddrag.common.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(prefix = "storage.minio", name = {"endpoint", "access-key", "secret-key"})
public class MinioStorageService implements ObjectStorageService {

    private static final long UNKNOWN_STREAM_SIZE = -1L;
    private final MinioClient minioClient;
    private final String bucket;
    private final Object bucketLock = new Object();
    private final Set<String> readyBuckets = ConcurrentHashMap.newKeySet();

    public MinioStorageService(Environment environment) {
        String endpoint = requiredProperty(environment, "storage.minio.endpoint");
        String accessKey = requiredProperty(environment, "storage.minio.access-key");
        String secretKey = requiredProperty(environment, "storage.minio.secret-key");
        this.bucket = environment.getProperty("storage.minio.bucket", "dd-rag-documents");
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Override
    public String getDefaultBucket() {
        return bucket;
    }

    @Override
    public InputStream getObject(String bucket, String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception exception) {
            throw new BusinessException("对象存储读取失败", exception);
        }
    }

    @Override
    public void putObject(String bucket, String objectKey, InputStream inputStream, long objectSize, String contentType) {
        try {
            ensureBucketExists(bucket);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(inputStream, objectSize, UNKNOWN_STREAM_SIZE)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception exception) {
            throw new BusinessException("对象存储上传失败", exception);
        }
    }

    @Override
    public void deleteObject(String bucket, String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception exception) {
            throw new BusinessException("对象存储删除失败", exception);
        }
    }

    private void ensureBucketExists(String bucket) throws Exception {
        if (readyBuckets.contains(bucket)) {
            return;
        }
        synchronized (bucketLock) {
            if (readyBuckets.contains(bucket)) {
                return;
            }
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            readyBuckets.add(bucket);
        }
    }

    private String requiredProperty(Environment environment, String propertyName) {
        String value = environment.getProperty(propertyName);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("对象存储配置缺失: " + propertyName);
        }
        return value;
    }
}
