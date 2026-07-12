package com.dong.ddrag.modelplatform.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dong.ddrag.modelplatform.concurrency.ModelInvocationConcurrencyGuard;
import com.dong.ddrag.modelplatform.concurrency.ModelInvocationPermit;
import com.dong.ddrag.modelplatform.config.ModelRuntimeProperties;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionEntity;
import com.dong.ddrag.modelplatform.model.entity.ModelConnectionModelEntity;
import com.dong.ddrag.modelplatform.model.enums.ConnectionOwnerType;
import com.dong.ddrag.modelplatform.model.enums.ModelScenario;
import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import com.dong.ddrag.modelplatform.service.ModelCallLedgerService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class ModelInvocationExecutorTest {
    @Test
    void revalidatesStartsLedgerBeforeProviderAndReleasesPermitAfterCompletion() {
        ModelRuntimeService runtime = mock(ModelRuntimeService.class);
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ModelCallLedgerService ledger = mock(ModelCallLedgerService.class);
        ModelInvocationConcurrencyGuard concurrency = new ModelInvocationConcurrencyGuard(properties());
        ModelInvocationContext context = context();
        ModelConnectionEntity connection = connection();
        ModelConnectionModelEntity model = model();
        when(runtime.requireCurrent(context)).thenReturn(new ModelRuntimeService.ResolvedModel(context, connection, model));
        ChatModelFactory.ChatModelLease lease = mock(ChatModelFactory.ChatModelLease.class);
        ChatModel provider = mock(ChatModel.class);
        ChatResponse response = mock(ChatResponse.class);
        when(factory.acquire(connection, model)).thenReturn(lease);
        when(lease.model()).thenReturn(provider);
        when(provider.call(any(Prompt.class))).thenReturn(response);

        ModelInvocationExecutor executor = new ModelInvocationExecutor(runtime, factory, ledger, concurrency,
                Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC));

        assertThat(executor.call(context, new Prompt("hello"))).isSameAs(response);

        InOrder ordered = inOrder(runtime, ledger, factory, provider);
        ordered.verify(runtime).requireCurrent(context);
        ordered.verify(ledger).start(anyString(), any(ModelInvocationContext.class), any(), any(), any());
        ordered.verify(factory).acquire(connection, model);
        ordered.verify(provider).call(any(Prompt.class));
        verify(ledger).succeed(anyString(), anyLong(), anyLong(), anyLong(), anyLong(), any());
        concurrency.acquire(7L, 11L, 1).close();
        verify(lease).close();
    }

    @Test
    void closesStartedLedgerWhenProviderClientCannotBeAcquired() {
        ModelRuntimeService runtime = mock(ModelRuntimeService.class);
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ModelCallLedgerService ledger = mock(ModelCallLedgerService.class);
        ModelInvocationConcurrencyGuard concurrency = new ModelInvocationConcurrencyGuard(properties());
        ModelInvocationContext context = context();
        ModelConnectionEntity connection = connection();
        ModelConnectionModelEntity model = model();
        when(runtime.requireCurrent(context)).thenReturn(new ModelRuntimeService.ResolvedModel(context, connection, model));
        doThrow(new IllegalStateException("client unavailable")).when(factory).acquire(connection, model);
        ModelInvocationExecutor executor = new ModelInvocationExecutor(runtime, factory, ledger, concurrency,
                Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> executor.call(context, new Prompt("hello")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("PROVIDER_ERROR");

        verify(ledger).start(anyString(), any(ModelInvocationContext.class), any(), any(), any());
        verify(ledger).fail(anyString(), org.mockito.ArgumentMatchers.eq("PROVIDER_ERROR"),
                org.mockito.ArgumentMatchers.isNull(), anyLong());
        concurrency.acquire(7L, 11L, 1).close();
    }

    @Test
    void marksActiveTurnCancellationWithoutReleasingPhysicalInvocationPermit() throws Exception {
        ModelRuntimeService runtime = mock(ModelRuntimeService.class);
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ModelCallLedgerService ledger = mock(ModelCallLedgerService.class);
        ModelInvocationConcurrencyGuard concurrency = new ModelInvocationConcurrencyGuard(properties());
        ModelInvocationContext context = context();
        ModelConnectionEntity connection = connection();
        ModelConnectionModelEntity model = model();
        when(runtime.requireCurrent(context)).thenReturn(new ModelRuntimeService.ResolvedModel(context, connection, model));
        ChatModelFactory.ChatModelLease lease = mock(ChatModelFactory.ChatModelLease.class);
        ChatModel provider = mock(ChatModel.class);
        CountDownLatch enteredProvider = new CountDownLatch(1);
        CountDownLatch allowProviderExit = new CountDownLatch(1);
        when(factory.acquire(connection, model)).thenReturn(lease);
        when(lease.model()).thenReturn(provider);
        when(provider.call(any(Prompt.class))).thenAnswer(invocation -> {
            enteredProvider.countDown();
            allowProviderExit.await(5, TimeUnit.SECONDS);
            return mock(ChatResponse.class);
        });
        when(ledger.requestCancellation(anyString(), anyLong())).thenReturn(true);
        ModelInvocationExecutor executor = new ModelInvocationExecutor(runtime, factory, ledger, concurrency,
                Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC));

        Thread.ofVirtual().start(() -> executor.call(context, new Prompt("hello")));
        assertThat(enteredProvider.await(2, TimeUnit.SECONDS)).isTrue();

        assertThat(executor.requestCancellationForTurn("turn-1")).isEqualTo(1);
        verify(ledger).requestCancellation(anyString(), anyLong());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> concurrency.acquire(7L, 11L, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("GLOBAL_BUSY");
        allowProviderExit.countDown();
    }

    @Test
    void keepsPermitAfterClientCancellationUntilHardDeadline() throws Exception {
        ModelRuntimeService runtime = mock(ModelRuntimeService.class);
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ModelCallLedgerService ledger = mock(ModelCallLedgerService.class);
        ModelInvocationConcurrencyGuard concurrency = new ModelInvocationConcurrencyGuard(properties());
        ModelInvocationContext context = context();
        ModelConnectionEntity connection = connection();
        ModelConnectionModelEntity model = model();
        when(runtime.requireCurrent(context)).thenReturn(new ModelRuntimeService.ResolvedModel(context, connection, model));
        ChatModelFactory.ChatModelLease lease = mock(ChatModelFactory.ChatModelLease.class);
        ChatModel provider = mock(ChatModel.class);
        when(factory.acquire(connection, model)).thenReturn(lease);
        when(lease.model()).thenReturn(provider);
        when(provider.stream(any(Prompt.class))).thenReturn(Flux.never());
        ModelRuntimeProperties properties = properties();
        properties.getTimeout().setTransportHard(java.time.Duration.ofMillis(120));
        ModelInvocationExecutor executor = new ModelInvocationExecutor(runtime, factory, ledger, concurrency,
                properties, Clock.systemUTC());
        ModelCallCancellation cancellation = new ModelCallCancellation();
        AtomicReference<org.reactivestreams.Subscription> subscription = new AtomicReference<>();

        cancellation.bind(() -> executor.stream(context, new Prompt("hello"))
                .doOnSubscribe(subscription::set)
                .subscribe());
        await(() -> subscription.get() != null, 1000);

        cancellation.request();
        verify(ledger).requestCancellation(anyString(), anyLong());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> concurrency.acquire(7L, 11L, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("GLOBAL_BUSY");

        await(() -> {
            try (var ignored = concurrency.acquire(7L, 11L, 1)) {
                return true;
            } catch (BusinessException ignored) {
                return false;
            }
        }, 2000);
        verify(ledger).hardTimeout(anyString(), anyLong());
        verify(lease).close();
    }

    @Test
    void keepsEveryPermitDuringBatchClientDisconnectUntilEachProviderCallHitsHardDeadline() throws Exception {
        ModelRuntimeService runtime = mock(ModelRuntimeService.class);
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ModelCallLedgerService ledger = mock(ModelCallLedgerService.class);
        ModelRuntimeProperties properties = properties();
        properties.getInvocation().setGlobalLimit(2);
        properties.getInvocation().setPerUserLimit(2);
        properties.getInvocation().setDefaultConnectionLimit(2);
        properties.getTimeout().setTransportHard(java.time.Duration.ofMillis(120));
        ModelInvocationConcurrencyGuard concurrency = new ModelInvocationConcurrencyGuard(properties);
        ModelInvocationContext firstContext = context(7L, 11L, "turn-1");
        ModelInvocationContext secondContext = context(8L, 12L, "turn-2");
        ModelConnectionEntity firstConnection = connection(11L, 2);
        ModelConnectionEntity secondConnection = connection(12L, 2);
        ModelConnectionModelEntity firstModel = model(21L, 11L);
        ModelConnectionModelEntity secondModel = model(22L, 12L);
        when(runtime.requireCurrent(firstContext)).thenReturn(new ModelRuntimeService.ResolvedModel(
                firstContext, firstConnection, firstModel));
        when(runtime.requireCurrent(secondContext)).thenReturn(new ModelRuntimeService.ResolvedModel(
                secondContext, secondConnection, secondModel));
        ChatModelFactory.ChatModelLease firstLease = mock(ChatModelFactory.ChatModelLease.class);
        ChatModelFactory.ChatModelLease secondLease = mock(ChatModelFactory.ChatModelLease.class);
        ChatModel provider = mock(ChatModel.class);
        when(factory.acquire(firstConnection, firstModel)).thenReturn(firstLease);
        when(factory.acquire(secondConnection, secondModel)).thenReturn(secondLease);
        when(firstLease.model()).thenReturn(provider);
        when(secondLease.model()).thenReturn(provider);
        when(provider.stream(any(Prompt.class))).thenReturn(Flux.never());
        ModelInvocationExecutor executor = new ModelInvocationExecutor(runtime, factory, ledger, concurrency,
                properties, Clock.systemUTC());
        ModelCallCancellation firstCancellation = new ModelCallCancellation();
        ModelCallCancellation secondCancellation = new ModelCallCancellation();

        executor.stream(firstContext, new Prompt("first"), firstCancellation).subscribe();
        executor.stream(secondContext, new Prompt("second"), secondCancellation).subscribe();
        verify(provider, org.mockito.Mockito.timeout(1000).times(2)).stream(any(Prompt.class));

        firstCancellation.request();
        secondCancellation.request();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> concurrency.acquire(9L, 13L, 2))
                .isInstanceOf(BusinessException.class)
                .hasMessage("GLOBAL_BUSY");

        verify(ledger, org.mockito.Mockito.timeout(2000).times(2)).hardTimeout(anyString(), anyLong());
        assertThat(canAcquire(concurrency, 9L, 13L)).isTrue();
        verify(firstLease).close();
        verify(secondLease).close();
    }

    private static void await(java.util.function.BooleanSupplier condition, long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("condition was not met before timeout");
            }
            Thread.sleep(10);
        }
    }

    private static boolean canAcquire(ModelInvocationConcurrencyGuard concurrency, long userId, long connectionId) {
        try (ModelInvocationPermit ignored = concurrency.acquire(userId, connectionId, 2)) {
            return true;
        } catch (BusinessException ignored) {
            return false;
        }
    }

    private static ModelRuntimeProperties properties() {
        ModelRuntimeProperties properties = new ModelRuntimeProperties();
        properties.getInvocation().setGlobalLimit(1);
        properties.getInvocation().setPerUserLimit(1);
        properties.getInvocation().setDefaultConnectionLimit(1);
        return properties;
    }

    private static ModelInvocationContext context() {
        return context(7L, 11L, "turn-1");
    }

    private static ModelInvocationContext context(long userId, long connectionId, String turnId) {
        return new ModelInvocationContext(userId, ModelScenario.ASSISTANT_CHAT, connectionId, 21L, 1L,
                ProviderType.OPENAI, "gpt-test", "connection", ConnectionOwnerType.PLATFORM,
                31L, null, null, turnId, "request-1");
    }

    private static ModelConnectionEntity connection() {
        return connection(11L, 1);
    }

    private static ModelConnectionEntity connection(long connectionId, int maxConcurrency) {
        ModelConnectionEntity connection = new ModelConnectionEntity();
        connection.setId(connectionId);
        connection.setMaxConcurrency(maxConcurrency);
        return connection;
    }

    private static ModelConnectionModelEntity model() {
        return model(21L, 11L);
    }

    private static ModelConnectionModelEntity model(long modelId, long connectionId) {
        ModelConnectionModelEntity model = new ModelConnectionModelEntity();
        model.setId(modelId);
        model.setConnectionId(connectionId);
        model.setModelName("gpt-test");
        return model;
    }
}
