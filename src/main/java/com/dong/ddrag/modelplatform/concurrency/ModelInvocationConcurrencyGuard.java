package com.dong.ddrag.modelplatform.concurrency;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.modelplatform.config.ModelRuntimeProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Admission for actual Provider calls. State is deliberately JVM-local in V6.1;
 * connection and user identifiers are never emitted as metric tags.
 */
@Component
public final class ModelInvocationConcurrencyGuard {
    private final int globalLimit;
    private final int userLimit;
    private final int defaultConnectionLimit;
    private final MeterRegistry meterRegistry;
    private final Map<Long, Integer> userInFlight = new HashMap<>();
    private final Map<Long, Integer> connectionInFlight = new HashMap<>();
    private int globalInFlight;

    @Autowired
    public ModelInvocationConcurrencyGuard(ModelRuntimeProperties properties, MeterRegistry meterRegistry) {
        Objects.requireNonNull(properties, "properties");
        globalLimit = properties.getInvocation().getGlobalLimit();
        userLimit = properties.getInvocation().getPerUserLimit();
        defaultConnectionLimit = properties.getInvocation().getDefaultConnectionLimit();
        this.meterRegistry = meterRegistry;
    }

    public ModelInvocationConcurrencyGuard(ModelRuntimeProperties properties) {
        this(properties, null);
    }

    public synchronized ModelInvocationPermit acquire(Long userId, Long connectionId, Integer connectionLimitOverride) {
        if (userId == null || connectionId == null) {
            throw new IllegalArgumentException("userId and connectionId are required");
        }
        int connectionLimit = connectionLimitOverride == null ? defaultConnectionLimit : connectionLimitOverride;
        if (connectionLimit <= 0) {
            throw new IllegalArgumentException("connection concurrency limit must be positive");
        }

        boolean globalAcquired = false;
        boolean userAcquired = false;
        try {
            if (globalInFlight >= globalLimit) {
                reject("GLOBAL_BUSY", "global");
            }
            globalInFlight++;
            globalAcquired = true;

            int currentUser = userInFlight.getOrDefault(userId, 0);
            if (currentUser >= userLimit) {
                reject("USER_BUSY", "user");
            }
            userInFlight.put(userId, currentUser + 1);
            userAcquired = true;

            int currentConnection = connectionInFlight.getOrDefault(connectionId, 0);
            if (currentConnection >= connectionLimit) {
                reject("CONNECTION_BUSY", "connection");
            }
            connectionInFlight.put(connectionId, currentConnection + 1);
            recordOccupancy();
            return new ModelInvocationPermit(this, userId, connectionId);
        } catch (RuntimeException exception) {
            if (userAcquired) decrement(userInFlight, userId);
            if (globalAcquired) globalInFlight--;
            recordOccupancy();
            throw exception;
        }
    }

    synchronized void release(Long userId, Long connectionId) {
        decrement(connectionInFlight, connectionId);
        decrement(userInFlight, userId);
        if (globalInFlight > 0) globalInFlight--;
        recordOccupancy();
    }

    synchronized int globalInFlight() { return globalInFlight; }
    synchronized int userInFlight(Long userId) { return userInFlight.getOrDefault(userId, 0); }
    synchronized int connectionInFlight(Long connectionId) { return connectionInFlight.getOrDefault(connectionId, 0); }

    private void reject(String code, String layer) {
        if (meterRegistry != null) {
            Counter.builder("ddrag.model.invocation.rejected").tag("layer", layer).register(meterRegistry).increment();
        }
        throw new BusinessException(code);
    }

    private void recordOccupancy() {
        if (meterRegistry != null) {
            meterRegistry.gauge("ddrag.model.invocation.inflight", this, guard -> guard.globalInFlight());
        }
    }

    private static void decrement(Map<Long, Integer> counters, Long key) {
        Integer value = counters.get(key);
        if (value == null || value <= 1) counters.remove(key);
        else counters.put(key, value - 1);
    }
}
