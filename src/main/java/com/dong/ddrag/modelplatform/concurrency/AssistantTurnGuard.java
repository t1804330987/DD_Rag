package com.dong.ddrag.modelplatform.concurrency;

import com.dong.ddrag.common.exception.BusinessException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/** Keeps one active Assistant turn per persisted session. */
@Component
public final class AssistantTurnGuard {
    private final ConcurrentHashMap<Long, Boolean> activeSessions = new ConcurrentHashMap<>();

    public TurnPermit acquire(Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            throw new IllegalArgumentException("sessionId is required");
        }
        if (activeSessions.putIfAbsent(sessionId, Boolean.TRUE) != null) {
            throw new BusinessException("SESSION_BUSY");
        }
        return new TurnPermit(this, sessionId);
    }

    public static TurnPermit noOp() {
        return new TurnPermit(null, null);
    }

    private void release(Long sessionId) {
        activeSessions.remove(sessionId);
    }

    public static final class TurnPermit implements AutoCloseable {
        private final AssistantTurnGuard guard;
        private final Long sessionId;
        private final AtomicBoolean closed = new AtomicBoolean();

        private TurnPermit(AssistantTurnGuard guard, Long sessionId) {
            this.guard = guard;
            this.sessionId = sessionId;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                if (guard != null) {
                    guard.release(sessionId);
                }
            }
        }
    }
}
