package com.dong.ddrag.modelplatform.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;

class ModelCallCancellationTest {
    @Test
    void cancelsRegisteredSubscriptionAndImmediatelyCancelsLateRegistration() {
        ModelCallCancellation cancellation = new ModelCallCancellation();
        RecordingSubscription first = new RecordingSubscription();
        RecordingSubscription late = new RecordingSubscription();

        cancellation.register(first);
        cancellation.request();
        cancellation.register(late);

        assertThat(cancellation.isRequested()).isTrue();
        assertThat(first.cancelled.get()).isTrue();
        assertThat(late.cancelled.get()).isTrue();
    }

    @Test
    void notifiesListenerRegisteredBeforeOrAfterCancellation() {
        ModelCallCancellation cancellation = new ModelCallCancellation();
        AtomicBoolean before = new AtomicBoolean();
        AtomicBoolean after = new AtomicBoolean();

        cancellation.onRequest(() -> before.set(true));
        cancellation.requestBusinessTimeout();
        cancellation.onRequest(() -> after.set(true));

        assertThat(cancellation.isBusinessTimedOut()).isTrue();
        assertThat(before.get()).isTrue();
        assertThat(after.get()).isTrue();
    }

    private static final class RecordingSubscription implements Subscription {
        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void request(long count) {
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }
    }
}
