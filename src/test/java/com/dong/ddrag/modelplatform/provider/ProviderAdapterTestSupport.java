package com.dong.ddrag.modelplatform.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

final class ProviderAdapterTestSupport {
    private ProviderAdapterTestSupport() {
    }

    static Fixture fixture(AdapterConstructor constructor, String responseBody) {
        AtomicReference<ProviderHttpRequest> request = new AtomicReference<>();
        AtomicReference<ProviderChatModelSettings> settings = new AtomicReference<>();
        ProviderHttpTransport transport = value -> {
            request.set(value);
            return new ProviderHttpResponse(200, responseBody);
        };
        ProviderChatModelFactory factory = value -> {
            settings.set(value);
            return new NeverReturningChatModel();
        };
        ChatModelProviderAdapter adapter = constructor.create(
                transport, factory, new ObjectMapper(), Duration.ofMillis(40));
        return new Fixture(adapter, request, settings);
    }

    static void verifyCommonBehavior(Fixture fixture, ProviderConnectionSnapshot connection, String modelName) {
        fixture.adapter().probeConnection(connection);
        assertTrue(fixture.request().get().deadline().compareTo(Duration.ofMillis(40)) == 0);

        ChatModel model = fixture.adapter().createChatModel(connection, modelName);
        ProviderAdapterException timeout = assertThrows(ProviderAdapterException.class,
                () -> model.call(new Prompt("test")));
        assertEquals(ProviderErrorCode.HARD_TIMEOUT, timeout.code());
        ProviderAdapterException streamTimeout = assertThrows(ProviderAdapterException.class,
                () -> model.stream(new Prompt("test")).blockLast(Duration.ofSeconds(1)));
        assertEquals(ProviderErrorCode.HARD_TIMEOUT, streamTimeout.code());
        assertEquals(modelName, fixture.settings().get().modelName());
        assertEquals(connection.apiKey(), fixture.settings().get().apiKey());
        assertEquals(connection.baseUrl(), fixture.settings().get().baseUrl());
        assertFalse(fixture.settings().get().toString().contains(connection.apiKey()));
        assertFalse(fixture.request().get().toString().contains(connection.apiKey()));
        assertTrue(NeverReturningChatModel.awaitCancelled(), "deadline must cancel the upstream stream");

        String secret = "secret-that-must-not-leak";
        ProviderAdapterException invalid = assertThrows(ProviderAdapterException.class,
                () -> fixture.adapter().createChatModel(
                        new ProviderConnectionSnapshot("not a url", secret, Map.of()), modelName));
        assertFalse(invalid.getMessage().contains(secret));
        assertFalse(invalid.toString().contains(secret));
    }

    record Fixture(ChatModelProviderAdapter adapter, AtomicReference<ProviderHttpRequest> request,
            AtomicReference<ProviderChatModelSettings> settings) {
    }

    @FunctionalInterface
    interface AdapterConstructor {
        ChatModelProviderAdapter create(ProviderHttpTransport transport, ProviderChatModelFactory factory,
                ObjectMapper mapper, Duration deadline);
    }

    private static final class NeverReturningChatModel implements ChatModel {
        private static final AtomicReference<CountDownLatch> STREAM_CANCELLED =
                new AtomicReference<>(new CountDownLatch(1));

        @Override
        public ChatResponse call(Prompt prompt) {
            try {
                new CountDownLatch(1).await();
                throw new IllegalStateException("unreachable");
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("cancelled");
            }
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            STREAM_CANCELLED.set(new CountDownLatch(1));
            return Flux.<ChatResponse>never().doOnCancel(() -> STREAM_CANCELLED.get().countDown());
        }

        private static boolean awaitCancelled() {
            try {
                return STREAM_CANCELLED.get().await(1, TimeUnit.SECONDS);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
