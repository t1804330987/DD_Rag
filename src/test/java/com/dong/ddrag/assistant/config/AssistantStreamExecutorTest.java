package com.dong.ddrag.assistant.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dong.ddrag.modelplatform.config.ModelRuntimeProperties;
import com.dong.ddrag.modelplatform.runtime.ModelCallCancellation;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AssistantStreamExecutorTest {
    @Test
    void rejectsImmediatelyWhenAllConfiguredWorkersAreBusy() throws Exception {
        ModelRuntimeProperties properties = new ModelRuntimeProperties();
        properties.getStream().setExecutorThreads(1);
        AssistantStreamExecutor executor = new AssistantStreamExecutor(properties);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.submit(new ModelCallCancellation(), () -> {
                entered.countDown();
                await(release);
            });
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> executor.submit(new ModelCallCancellation(), () -> { }))
                    .isInstanceOf(RejectedExecutionException.class);
            assertThat(executor.activeCount()).isEqualTo(1);
        } finally {
            release.countDown();
            executor.stop();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
