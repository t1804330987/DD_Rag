package com.dong.ddrag.modelplatform.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.modelplatform.config.ModelRuntimeProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelInvocationConcurrencyGuardTest {
    @Test
    void acquiresGlobalUserAndConnectionPermitsAndReleaseIsIdempotent() {
        ModelInvocationConcurrencyGuard guard = guard(2, 1, 1);

        ModelInvocationPermit permit = guard.acquire(7L, 11L, null);

        assertThat(guard.globalInFlight()).isEqualTo(1);
        assertThat(guard.userInFlight(7L)).isEqualTo(1);
        assertThat(guard.connectionInFlight(11L)).isEqualTo(1);
        permit.close();
        permit.close();
        assertThat(guard.globalInFlight()).isZero();
        assertThat(guard.userInFlight(7L)).isZero();
        assertThat(guard.connectionInFlight(11L)).isZero();
    }

    @Test
    void releasesEarlierPermitsWhenConnectionAdmissionFails() {
        ModelInvocationConcurrencyGuard guard = guard(2, 2, 1);
        ModelInvocationPermit existing = guard.acquire(8L, 11L, null);

        assertThatThrownBy(() -> guard.acquire(7L, 11L, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getMessage()).isEqualTo("CONNECTION_BUSY"));
        assertThat(guard.globalInFlight()).isEqualTo(1);
        assertThat(guard.userInFlight(7L)).isZero();
        existing.close();
    }

    @Test
    void usesConnectionOverrideBeforeDefaultLimit() {
        ModelInvocationConcurrencyGuard guard = guard(3, 3, 3);
        ModelInvocationPermit existing = guard.acquire(7L, 11L, 1);

        assertThatThrownBy(() -> guard.acquire(8L, 11L, 1))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getMessage()).isEqualTo("CONNECTION_BUSY"));
        existing.close();
    }

    @Test
    void keepsTheFirstFiftyInvocationsAdmittedAndRejectsTheRemainingFiftyWithoutLeakingPermits() {
        ModelInvocationConcurrencyGuard guard = guard(50, 100, 100);
        List<ModelInvocationPermit> permits = new ArrayList<>();

        for (long userId = 1; userId <= 100; userId++) {
            try {
                permits.add(guard.acquire(userId, userId, null));
            } catch (BusinessException error) {
                assertThat(error.getMessage()).isEqualTo("GLOBAL_BUSY");
            }
        }

        assertThat(permits).hasSize(50);
        assertThat(guard.globalInFlight()).isEqualTo(50);
        permits.forEach(ModelInvocationPermit::close);
        assertThat(guard.globalInFlight()).isZero();
    }

    private static ModelInvocationConcurrencyGuard guard(int global, int perUser, int connection) {
        ModelRuntimeProperties properties = new ModelRuntimeProperties();
        properties.getInvocation().setGlobalLimit(global);
        properties.getInvocation().setPerUserLimit(perUser);
        properties.getInvocation().setDefaultConnectionLimit(connection);
        return new ModelInvocationConcurrencyGuard(properties);
    }
}
