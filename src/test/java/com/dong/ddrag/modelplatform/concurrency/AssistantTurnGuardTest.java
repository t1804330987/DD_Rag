package com.dong.ddrag.modelplatform.concurrency;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dong.ddrag.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class AssistantTurnGuardTest {
    @Test
    void rejectsOnlyAnotherActiveTurnForTheSameSession() {
        AssistantTurnGuard guard = new AssistantTurnGuard();

        AssistantTurnGuard.TurnPermit first = guard.acquire(11L);
        AssistantTurnGuard.TurnPermit otherSession = guard.acquire(12L);

        assertThatThrownBy(() -> guard.acquire(11L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getMessage()).isEqualTo("SESSION_BUSY"));
        first.close();
        guard.acquire(11L).close();
        otherSession.close();
    }
}
