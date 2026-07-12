package com.dong.ddrag.modelplatform.runtime;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.reactivestreams.Subscription;

/**
 * Request-scoped cancellation bridge between the servlet SSE lifecycle and a
 * Reactor Provider subscription. A cancellation request is deliberately not a
 * physical-termination acknowledgement.
 */
public final class ModelCallCancellation {
    private static final ThreadLocal<ModelCallCancellation> CURRENT = new ThreadLocal<>();

    private final AtomicBoolean requested = new AtomicBoolean();
    private final AtomicBoolean businessTimedOut = new AtomicBoolean();
    private final AtomicBoolean hardTimedOut = new AtomicBoolean();
    private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();
    private final Set<Runnable> requestListeners = ConcurrentHashMap.newKeySet();

    public static ModelCallCancellation current() {
        return CURRENT.get();
    }

    public boolean isRequested() {
        return requested.get();
    }

    public boolean isHardTimedOut() {
        return hardTimedOut.get();
    }

    public boolean isBusinessTimedOut() {
        return businessTimedOut.get();
    }

    public void request() {
        if (requested.compareAndSet(false, true)) {
            requestListeners.forEach(Runnable::run);
            subscriptions.forEach(Subscription::cancel);
            subscriptions.clear();
        }
    }

    public void requestBusinessTimeout() {
        businessTimedOut.set(true);
        request();
    }

    public void requestHardTimeout() {
        hardTimedOut.set(true);
        request();
    }

    public void register(Subscription subscription) {
        if (subscription == null) {
            return;
        }
        if (requested.get()) {
            subscription.cancel();
            return;
        }
        subscriptions.add(subscription);
        if (requested.get() && subscriptions.remove(subscription)) {
            subscription.cancel();
        }
    }

    public void unregister(Subscription subscription) {
        if (subscription != null) {
            subscriptions.remove(subscription);
        }
    }

    /** Registers a lifecycle listener and invokes it immediately if cancellation already won the race. */
    public void onRequest(Runnable listener) {
        if (listener == null) {
            return;
        }
        if (requested.get()) {
            listener.run();
            return;
        }
        requestListeners.add(listener);
        if (requested.get() && requestListeners.remove(listener)) {
            listener.run();
        }
    }

    public void removeOnRequest(Runnable listener) {
        if (listener != null) {
            requestListeners.remove(listener);
        }
    }

    public <T> T bind(CheckedSupplier<T> operation) {
        ModelCallCancellation previous = CURRENT.get();
        CURRENT.set(this);
        try {
            return operation.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get();
    }
}
