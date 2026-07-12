package com.dong.ddrag.modelplatform.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dong.ddrag.modelplatform.model.entity.ModelConnectionEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionModelEntity;
import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import com.dong.ddrag.modelplatform.provider.ChatModelProviderAdapter;
import com.dong.ddrag.modelplatform.provider.ChatModelProviderRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class ChatModelCacheTest {
    @Test
    void createsSameKeyOnlyOnceUnderConcurrency() throws Exception {
        AtomicInteger creations = new AtomicInteger();
        ChatModel model = mock(ChatModel.class);
        ChatModelFactory factory = factory(Clock.systemUTC(), 128, Duration.ofMinutes(15), creations, model);
        var executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var futures = java.util.stream.IntStream.range(0, 16)
                    .mapToObj(ignored -> executor.submit(() -> {
                        start.await();
                        try (ChatModelFactory.ChatModelLease lease = factory.acquire(connection(1L, 1L), model(2L, 1L))) {
                            return lease.model();
                        }
                    })).toList();
            start.countDown();
            for (var future : futures) assertSame(model, future.get());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, creations.get());
    }

    @Test
    void expiresIdleEntriesAndCreatesReplacement() {
        MutableClock clock = new MutableClock();
        AtomicInteger creations = new AtomicInteger();
        ChatModelFactory factory = factory(clock, 128, Duration.ofMinutes(15), creations, null);
        ChatModel first;
        try (ChatModelFactory.ChatModelLease lease = factory.acquire(connection(1L, 1L), model(2L, 1L))) {
            first = lease.model();
        }
        clock.advance(Duration.ofMinutes(16));
        factory.cleanUp();
        try (ChatModelFactory.ChatModelLease lease = factory.acquire(connection(1L, 1L), model(2L, 1L))) {
            assertNotSame(first, lease.model());
        }
        assertEquals(2, creations.get());
    }

    @Test
    void invalidationBlocksNewLeaseButPreservesExistingLease() {
        AtomicInteger creations = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        ChatModelFactory factory = factoryFrom(Clock.systemUTC(), 128, Duration.ofMinutes(15), creations,
                () -> new CloseableChatModel(closes));
        ChatModelFactory.ChatModelLease inFlight = factory.acquire(connection(1L, 1L), model(2L, 1L));
        ChatModel first = inFlight.model();

        factory.invalidateConnection(1L);
        assertEquals(0, closes.get());
        try (ChatModelFactory.ChatModelLease replacement = factory.acquire(connection(1L, 2L), model(2L, 1L))) {
            assertNotSame(first, replacement.model());
        }
        assertSame(first, inFlight.model());
        inFlight.close();
        assertEquals(1, closes.get());
    }

    @Test
    void enforcesMaximumSizeUsingIdleEntries() {
        ChatModelFactory factory = factory(Clock.systemUTC(), 1, Duration.ofMinutes(15), new AtomicInteger(), null);
        try (var ignored = factory.acquire(connection(1L, 1L), model(2L, 1L))) { }
        try (var ignored = factory.acquire(connection(2L, 1L), model(3L, 2L))) { }

        assertEquals(1, factory.size());
    }

    @Test
    void fullCacheDoesNotEvictInFlightEntry() {
        AtomicInteger closes = new AtomicInteger();
        ChatModelFactory factory = factoryFrom(Clock.systemUTC(), 1, Duration.ofMinutes(15),
                new AtomicInteger(), () -> new CloseableChatModel(closes));
        ChatModelFactory.ChatModelLease inFlight = factory.acquire(connection(1L, 1L), model(2L, 1L));
        ChatModel first = inFlight.model();

        try (var transientLease = factory.acquire(connection(2L, 1L), model(3L, 2L))) {
            assertEquals(1, factory.size());
            assertEquals(0, closes.get());
        }
        assertEquals(1, closes.get());
        try (var sameKey = factory.acquire(connection(1L, 1L), model(2L, 1L))) {
            assertSame(first, sameKey.model());
        }
        inFlight.close();
    }

    @Test
    void concurrentDoubleCloseReleasesLeaseOnlyOnce() throws Exception {
        AtomicInteger closes = new AtomicInteger();
        ChatModelFactory factory = factoryFrom(Clock.systemUTC(), 1, Duration.ofMinutes(15),
                new AtomicInteger(), () -> new CloseableChatModel(closes));
        ChatModelFactory.ChatModelLease first = factory.acquire(connection(1L, 1L), model(2L, 1L));
        ChatModelFactory.ChatModelLease second = factory.acquire(connection(1L, 1L), model(2L, 1L));
        factory.invalidateConnection(1L);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread a = Thread.ofPlatform().start(() -> closeAfter(start, first, failure));
        Thread b = Thread.ofPlatform().start(() -> closeAfter(start, first, failure));
        start.countDown();
        a.join();
        b.join();

        assertEquals(null, failure.get());
        assertEquals(0, closes.get());
        assertSame(second.model(), second.model());
        second.close();
        assertEquals(1, closes.get());
    }

    @Test
    void rejectsModelFromDifferentConnection() {
        ChatModelFactory factory = factory(Clock.systemUTC(), 128, Duration.ofMinutes(15), new AtomicInteger(), null);
        assertThrows(IllegalArgumentException.class,
                () -> factory.acquire(connection(1L, 1L), model(2L, 9L)));
    }

    private static ChatModelFactory factory(Clock clock, int maxSize, Duration idle,
                                            AtomicInteger creations, ChatModel fixedModel) {
        return factoryFrom(clock, maxSize, idle, creations,
                () -> fixedModel == null ? mock(ChatModel.class) : fixedModel);
    }

    private static ChatModelFactory factoryFrom(Clock clock, int maxSize, Duration idle,
                                                AtomicInteger creations, Supplier<ChatModel> models) {
        ChatModelProviderAdapter adapter = mock(ChatModelProviderAdapter.class);
        when(adapter.providerType()).thenReturn(ProviderType.DASHSCOPE);
        when(adapter.createChatModel(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(ignored -> {
                    creations.incrementAndGet();
                    return models.get();
                });
        return new ChatModelFactory(new ChatModelProviderRegistry(List.of(adapter)), clock, maxSize, idle);
    }

    private static ModelConnectionEntity connection(Long id, Long version) {
        ModelConnectionEntity entity = new ModelConnectionEntity();
        entity.setId(id);
        entity.setConfigVersion(version);
        entity.setProviderType(ProviderType.DASHSCOPE.name());
        entity.setBaseUrl("https://example.test");
        entity.setApiKeyPlaintext("secret");
        entity.setProviderOptionsJson("{}");
        return entity;
    }

    private static ModelConnectionModelEntity model(Long id, Long connectionId) {
        ModelConnectionModelEntity entity = new ModelConnectionModelEntity();
        entity.setId(id);
        entity.setConnectionId(connectionId);
        entity.setModelName("model-" + id);
        return entity;
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
    }

    private static final class CloseableChatModel implements ChatModel, AutoCloseable {
        private final AtomicInteger closes;

        private CloseableChatModel(AtomicInteger closes) {
            this.closes = closes;
        }

        @Override public ChatResponse call(Prompt prompt) { return null; }
        @Override public Flux<ChatResponse> stream(Prompt prompt) { return Flux.empty(); }
        @Override public void close() { closes.incrementAndGet(); }
    }

    private static void closeAfter(CountDownLatch start, ChatModelFactory.ChatModelLease lease,
                                   AtomicReference<Throwable> failure) {
        try {
            start.await();
            lease.close();
        } catch (Throwable throwable) {
            failure.compareAndSet(null, throwable);
        }
    }
}
