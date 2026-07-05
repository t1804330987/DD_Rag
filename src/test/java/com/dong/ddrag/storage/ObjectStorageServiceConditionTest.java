package com.dong.ddrag.storage;

import com.dong.ddrag.storage.service.MinioStorageService;
import com.dong.ddrag.storage.service.MissingObjectStorageService;
import com.dong.ddrag.storage.service.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectStorageServiceConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MinioStorageService.class, MissingObjectStorageService.class);

    @Test
    void shouldUseMinioStorageWhenStoragePropertiesArePresent() {
        contextRunner
                .withPropertyValues(
                        "storage.minio.endpoint=http://localhost:9000",
                        "storage.minio.access-key=minioadmin",
                        "storage.minio.secret-key=minioadmin"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ObjectStorageService.class);
                    assertThat(context.getBean(ObjectStorageService.class)).isInstanceOf(MinioStorageService.class);
                });
    }

    @Test
    void shouldUseMissingStorageWhenMinioPropertiesAreAbsent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ObjectStorageService.class);
            assertThat(context.getBean(ObjectStorageService.class)).isInstanceOf(MissingObjectStorageService.class);
        });
    }
}
