package com.dong.ddrag.assistant.config;

import com.dong.ddrag.modelplatform.config.ModelRuntimeProperties;
import com.dong.ddrag.modelplatform.runtime.ModelCallCancellation;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** Bounded, queue-free executor for servlet-side Assistant stream orchestration. */
@Component
public final class AssistantStreamExecutor {
    private static final long SHUTDOWN_WAIT_SECONDS = 30;

    private final ThreadPoolExecutor executor;
    private final Set<ModelCallCancellation> activeCancellations = ConcurrentHashMap.newKeySet();

    public AssistantStreamExecutor(ModelRuntimeProperties properties) {
        int threads = properties.getStream().getExecutorThreads();
        executor = new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                threadFactory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public void submit(ModelCallCancellation cancellation, Runnable operation) {
        activeCancellations.add(cancellation);
        try {
            executor.execute(() -> {
                try {
                    operation.run();
                } finally {
                    activeCancellations.remove(cancellation);
                }
            });
        } catch (RejectedExecutionException exception) {
            activeCancellations.remove(cancellation);
            throw exception;
        }
    }

    int activeCount() {
        return activeCancellations.size();
    }

    @PreDestroy
    void stop() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                activeCancellations.forEach(ModelCallCancellation::request);
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            activeCancellations.forEach(ModelCallCancellation::request);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory threadFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "assistant-stream-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
