package com.dong.ddrag.modelplatform.concurrency;

import java.util.concurrent.atomic.AtomicBoolean;

/** A single real Provider invocation's three-level admission lease. */
public final class ModelInvocationPermit implements AutoCloseable {
    private final ModelInvocationConcurrencyGuard guard;
    private final Long userId;
    private final Long connectionId;
    private final AtomicBoolean closed = new AtomicBoolean();

    ModelInvocationPermit(ModelInvocationConcurrencyGuard guard, Long userId, Long connectionId) {
        this.guard = guard;
        this.userId = userId;
        this.connectionId = connectionId;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            guard.release(userId, connectionId);
        }
    }
}
