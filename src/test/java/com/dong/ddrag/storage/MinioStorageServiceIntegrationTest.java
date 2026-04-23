package com.dong.ddrag.storage;

import com.dong.ddrag.storage.service.MinioStorageService;
import com.dong.ddrag.storage.service.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.env.MockEnvironment;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_MINIO_TESTS", matches = "true")
class MinioStorageServiceIntegrationTest {

    @Test
    void shouldUploadAndDeleteObjectWithLocalMinio() {
        ObjectStorageService objectStorageService = new MinioStorageService(localMinioEnvironment());
        assertThat(objectStorageService).isInstanceOf(MinioStorageService.class);
        byte[] content = "hello minio".getBytes(StandardCharsets.UTF_8);
        String bucket = objectStorageService.getDefaultBucket();
        String objectKey = "integration/" + UUID.randomUUID() + ".txt";

        objectStorageService.putObject(
                bucket,
                objectKey,
                new ByteArrayInputStream(content),
                content.length,
                "text/plain"
        );
        objectStorageService.deleteObject(bucket, objectKey);
    }

    private MockEnvironment localMinioEnvironment() {
        return new MockEnvironment()
                .withProperty("storage.minio.endpoint", "http://localhost:9000")
                .withProperty("storage.minio.access-key", "minioadmin")
                .withProperty("storage.minio.secret-key", "minioadmin")
                .withProperty("storage.minio.bucket", "dd-rag-test-documents");
    }
}
