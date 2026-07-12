package com.dong.ddrag.modelplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRuntimePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void shouldBindPlatformCapacityDefaults() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            ModelRuntimeProperties properties = context.getBean(ModelRuntimeProperties.class);

            assertThat(properties.getAssistant().getMaxActiveTurns()).isEqualTo(50);
            assertThat(properties.getInvocation().getGlobalLimit()).isEqualTo(50);
            assertThat(properties.getInvocation().getPerUserLimit()).isEqualTo(2);
            assertThat(properties.getInvocation().getDefaultConnectionLimit()).isEqualTo(50);
            assertThat(properties.getStream().getExecutorThreads()).isEqualTo(50);
            assertThat(properties.getStream().getQueueCapacity()).isZero();
            assertThat(properties.getTimeout().getFirstToken()).isEqualTo(Duration.ofSeconds(60));
            assertThat(properties.getTimeout().getIdle()).isEqualTo(Duration.ofSeconds(45));
            assertThat(properties.getTimeout().getBusinessTotal()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.getTimeout().getTransportHard()).isEqualTo(Duration.ofMinutes(6));
        });
    }

    @Test
    void shouldBindExplicitValues() {
        contextRunner
                .withPropertyValues(
                        "ddrag.model-runtime.assistant.max-active-turns=60",
                        "ddrag.model-runtime.invocation.global-limit=61",
                        "ddrag.model-runtime.invocation.per-user-limit=3",
                        "ddrag.model-runtime.invocation.default-connection-limit=62",
                        "ddrag.model-runtime.stream.executor-threads=63",
                        "ddrag.model-runtime.stream.queue-capacity=0",
                        "ddrag.model-runtime.timeout.first-token=70s",
                        "ddrag.model-runtime.timeout.idle=55s",
                        "ddrag.model-runtime.timeout.business-total=7m",
                        "ddrag.model-runtime.timeout.transport-hard=8m"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ModelRuntimeProperties properties = context.getBean(ModelRuntimeProperties.class);
                    assertThat(properties.getAssistant().getMaxActiveTurns()).isEqualTo(60);
                    assertThat(properties.getInvocation().getGlobalLimit()).isEqualTo(61);
                    assertThat(properties.getInvocation().getPerUserLimit()).isEqualTo(3);
                    assertThat(properties.getInvocation().getDefaultConnectionLimit()).isEqualTo(62);
                    assertThat(properties.getStream().getExecutorThreads()).isEqualTo(63);
                    assertThat(properties.getTimeout().getTransportHard()).isEqualTo(Duration.ofMinutes(8));
                });
    }

    @Test
    void shouldRejectNonPositiveLimitsAndNegativeQueueCapacity() {
        contextRunner
                .withPropertyValues(
                        "ddrag.model-runtime.assistant.max-active-turns=0",
                        "ddrag.model-runtime.invocation.global-limit=0",
                        "ddrag.model-runtime.invocation.per-user-limit=-1",
                        "ddrag.model-runtime.invocation.default-connection-limit=0",
                        "ddrag.model-runtime.stream.executor-threads=0",
                        "ddrag.model-runtime.stream.queue-capacity=-1"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectNonPositiveTimeouts() {
        contextRunner
                .withPropertyValues(
                        "ddrag.model-runtime.timeout.first-token=0s",
                        "ddrag.model-runtime.timeout.idle=0s",
                        "ddrag.model-runtime.timeout.business-total=0s",
                        "ddrag.model-runtime.timeout.transport-hard=0s"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectTransportHardTimeoutNotGreaterThanBusinessTotal() {
        contextRunner
                .withPropertyValues(
                        "ddrag.model-runtime.timeout.business-total=5m",
                        "ddrag.model-runtime.timeout.transport-hard=5m"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ModelRuntimeProperties.class)
    static class TestConfiguration {
    }
}
