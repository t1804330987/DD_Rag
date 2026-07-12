package com.dong.ddrag.modelplatform.runtime;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.modelplatform.config.ModelRuntimeProperties;
import com.dong.ddrag.modelplatform.concurrency.ModelInvocationConcurrencyGuard;
import com.dong.ddrag.modelplatform.concurrency.ModelInvocationPermit;
import com.dong.ddrag.modelplatform.provider.ProviderAdapterException;
import com.dong.ddrag.modelplatform.provider.ProviderErrorCode;
import com.dong.ddrag.modelplatform.service.ModelCallLedgerService;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;
import org.reactivestreams.Subscription;

/** The mandatory governance boundary for each physical ChatModel call or stream. */
@Service
public final class ModelInvocationExecutor implements ModelInvocationDispatcher {
    private final ModelRuntimeService runtimeService;
    private final ChatModelFactory chatModelFactory;
    private final ModelCallLedgerService ledgerService;
    private final ModelInvocationConcurrencyGuard concurrencyGuard;
    private final ModelRuntimeProperties properties;
    private final Clock clock;
    private final Map<String, InvocationResources> activeInvocations = new ConcurrentHashMap<>();

    @Autowired
    public ModelInvocationExecutor(ModelRuntimeService runtimeService, ChatModelFactory chatModelFactory,
                                   ModelCallLedgerService ledgerService,
                                   ModelInvocationConcurrencyGuard concurrencyGuard,
                                   ModelRuntimeProperties properties) {
        this(runtimeService, chatModelFactory, ledgerService, concurrencyGuard, properties, Clock.systemUTC());
    }

    ModelInvocationExecutor(ModelRuntimeService runtimeService, ChatModelFactory chatModelFactory,
                            ModelCallLedgerService ledgerService,
                            ModelInvocationConcurrencyGuard concurrencyGuard, Clock clock) {
        this(runtimeService, chatModelFactory, ledgerService, concurrencyGuard,
                new ModelRuntimeProperties(), clock);
    }

    ModelInvocationExecutor(ModelRuntimeService runtimeService, ChatModelFactory chatModelFactory,
                            ModelCallLedgerService ledgerService,
                            ModelInvocationConcurrencyGuard concurrencyGuard,
                            ModelRuntimeProperties properties, Clock clock) {
        this.runtimeService = runtimeService;
        this.chatModelFactory = chatModelFactory;
        this.ledgerService = ledgerService;
        this.concurrencyGuard = concurrencyGuard;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public ChatResponse call(ModelInvocationContext context, Prompt prompt) {
        InvocationResources resources = begin(context, true);
        try {
            ChatResponse response = resources.lease.model().call(prompt);
            succeed(resources);
            return response;
        } catch (RuntimeException exception) {
            fail(resources, exception);
            throw asBusinessException(exception);
        } finally {
            resources.close();
        }
    }

    @Override
    public Flux<ChatResponse> stream(ModelInvocationContext context, Prompt prompt) {
        return stream(context, prompt, ModelCallCancellation.current());
    }

    @Override
    public Flux<ChatResponse> stream(ModelInvocationContext context, Prompt prompt,
                                     ModelCallCancellation cancellation) {
        return Flux.defer(() -> {
            InvocationResources resources = begin(context, true);
            AtomicBoolean physicallyTerminated = new AtomicBoolean();
            AtomicBoolean receivedToken = new AtomicBoolean();
            AtomicReference<Subscription> subscription = new AtomicReference<>();
            Runnable cancellationListener = () -> markDetachedCancellation(resources, cancellation);
            if (cancellation != null) {
                cancellation.onRequest(cancellationListener);
            }
            Disposable hardDeadline = Schedulers.parallel().schedule(() -> {
                if (physicallyTerminated.compareAndSet(false, true)) {
                    ledgerService.hardTimeout(resources.invocationId, elapsed(resources));
                    if (cancellation != null) {
                        cancellation.requestHardTimeout();
                        cancellation.removeOnRequest(cancellationListener);
                    }
                    Subscription current = subscription.get();
                    if (current != null) {
                        current.cancel();
                    }
                    resources.close();
                }
            }, properties.getTimeout().getTransportHard().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            return resources.lease.model().stream(prompt)
                    .doOnSubscribe(current -> {
                        subscription.set(current);
                        if (cancellation != null) {
                            cancellation.register(current);
                        }
                    })
                    .timeout(properties.getTimeout().getFirstToken(),
                            ignored -> Mono.delay(properties.getTimeout().getIdle()))
                    .doOnNext(response -> receivedToken.set(true))
                    .doOnComplete(() -> {
                        if (physicallyTerminated.compareAndSet(false, true)) {
                            if (cancellation != null && cancellation.isBusinessTimedOut()) {
                                ledgerService.terminateBusinessTimeout(resources.invocationId, elapsed(resources));
                            } else if (cancellation != null && cancellation.isRequested()) {
                                ledgerService.cancelled(resources.invocationId, elapsed(resources));
                            } else {
                                succeed(resources);
                            }
                        }
                    })
                    .doOnError(error -> {
                        if (isLocalTimeout(error)) {
                            markDetachedTimeout(resources, cancellation, receivedToken.get());
                        } else if (physicallyTerminated.compareAndSet(false, true)) {
                            if (cancellation != null && cancellation.isBusinessTimedOut()) {
                                ledgerService.terminateBusinessTimeout(resources.invocationId, elapsed(resources));
                            } else if (cancellation != null && cancellation.isRequested()) {
                                ledgerService.cancelled(resources.invocationId, elapsed(resources));
                            } else {
                                failStream(resources, error, receivedToken.get());
                            }
                        }
                    })
                    .doOnCancel(() -> {
                        if (cancellation != null) {
                            markDetachedCancellation(resources, cancellation);
                        }
                    })
                    .doFinally(signal -> {
                        if (cancellation != null) {
                            cancellation.unregister(subscription.get());
                        }
                        // A cancelled Reactor subscription is only a cancellation request. Do not release a
                        // physical invocation permit until the Provider confirms a terminal signal.
                        if (physicallyTerminated.get()) {
                            hardDeadline.dispose();
                            if (cancellation != null) {
                                cancellation.removeOnRequest(cancellationListener);
                            }
                            resources.close();
                        }
                    })
                    .onErrorMap(error -> streamError(error, receivedToken.get()));
        });
    }

    @Override
    public ChatOptions defaultOptions(ModelInvocationContext context) {
        ModelRuntimeService.ResolvedModel resolved = runtimeService.requireCurrent(context);
        try (ChatModelFactory.ChatModelLease lease = chatModelFactory.acquire(resolved.connection(), resolved.model())) {
            return lease.model().getDefaultOptions();
        }
    }

    /**
     * Records a logical cancellation for every current invocation in a turn. Task 10 owns
     * propagating this request to the Reactor/Provider subscription and may only release the
     * physical permit after that upstream termination is confirmed.
     */
    public int requestCancellationForTurn(String turnId) {
        if (turnId == null || turnId.isBlank()) return 0;
        return activeInvocations.values().stream()
                .filter(resources -> turnId.equals(resources.context.turnId()))
                .mapToInt(resources -> ledgerService.requestCancellation(resources.invocationId,
                        elapsed(resources)) ? 1 : 0)
                .sum();
    }

    /** Executes the non-ChatModel connection discovery probe under the same admission and ledger rules. */
    public <T> T executeConnectionTest(ModelInvocationContext context, Supplier<T> operation) {
        if (context.scenario() != com.dong.ddrag.modelplatform.model.enums.ModelScenario.CONNECTION_TEST) {
            throw new IllegalArgumentException("Connection test scenario is required");
        }
        InvocationResources resources = begin(context, false);
        try {
            T result = operation.get();
            succeed(resources);
            return result;
        } catch (RuntimeException exception) {
            fail(resources, exception);
            throw asBusinessException(exception);
        } finally {
            resources.close();
        }
    }

    private InvocationResources begin(ModelInvocationContext context, boolean acquireChatModel) {
        ModelRuntimeService.ResolvedModel resolved = runtimeService.requireCurrent(context);
        ModelInvocationPermit permit = concurrencyGuard.acquire(context.userId(), context.connectionId(),
                resolved.connection().getMaxConcurrency());
        String invocationId = UUID.randomUUID().toString();
        long startedAt = clock.millis();
        try {
            // A failed start transaction must stop before a Provider client is obtained or invoked.
            ledgerService.start(invocationId, context, context.instructionProfileId(),
                    context.instructionVersionId(), context.instructionVersionSnapshot());
        } catch (RuntimeException exception) {
            permit.close();
            throw exception;
        }
        try {
            ChatModelFactory.ChatModelLease lease = acquireChatModel
                    ? chatModelFactory.acquire(resolved.connection(), resolved.model()) : null;
            InvocationResources resources = new InvocationResources(invocationId, context, startedAt, permit, lease);
            activeInvocations.put(invocationId, resources);
            return resources;
        } catch (RuntimeException exception) {
            try {
                ledgerService.fail(invocationId, errorCode(exception), null,
                        Math.max(0, clock.millis() - startedAt));
            } finally {
                permit.close();
            }
            throw asBusinessException(exception);
        }
    }

    private void succeed(InvocationResources resources) {
        ledgerService.succeed(resources.invocationId, 0, 0, 0, elapsed(resources), null);
    }

    private void fail(InvocationResources resources, Throwable error) {
        String code = errorCode(error);
        ledgerService.fail(resources.invocationId, code, null, elapsed(resources));
    }

    private void failStream(InvocationResources resources, Throwable error, boolean receivedToken) {
        if (error instanceof java.util.concurrent.TimeoutException) {
            ledgerService.timeout(resources.invocationId,
                    receivedToken ? "STREAM_IDLE_TIMEOUT" : "FIRST_TOKEN_TIMEOUT", elapsed(resources));
            return;
        }
        if (error instanceof BusinessException businessException
                && "CALL_TIMEOUT".equals(businessException.getMessage())) {
            ledgerService.timeout(resources.invocationId, "CALL_TIMEOUT", elapsed(resources));
            return;
        }
        fail(resources, error);
    }

    private void markDetachedCancellation(InvocationResources resources, ModelCallCancellation cancellation) {
        if (cancellation != null && cancellation.isBusinessTimedOut()) {
            ledgerService.detachBusinessTimeout(resources.invocationId, "CALL_TIMEOUT", elapsed(resources));
            return;
        }
        ledgerService.requestCancellation(resources.invocationId, elapsed(resources));
    }

    private void markDetachedTimeout(InvocationResources resources, ModelCallCancellation cancellation,
                                     boolean receivedToken) {
        if (cancellation != null) {
            cancellation.requestBusinessTimeout();
        }
        ledgerService.detachBusinessTimeout(resources.invocationId,
                receivedToken ? "STREAM_IDLE_TIMEOUT" : "FIRST_TOKEN_TIMEOUT", elapsed(resources));
    }

    private static boolean isLocalTimeout(Throwable error) {
        return error instanceof java.util.concurrent.TimeoutException;
    }

    private RuntimeException streamError(Throwable error, boolean receivedToken) {
        if (error instanceof BusinessException businessException) {
            return businessException;
        }
        if (error instanceof java.util.concurrent.TimeoutException) {
            return new BusinessException(receivedToken ? "STREAM_IDLE_TIMEOUT" : "FIRST_TOKEN_TIMEOUT");
        }
        return error instanceof RuntimeException runtimeException
                ? asBusinessException(runtimeException)
                : new BusinessException(errorCode(error));
    }

    private long elapsed(InvocationResources resources) {
        return Math.max(0, clock.millis() - resources.startedAt);
    }

    private static BusinessException asBusinessException(RuntimeException error) {
        if (error instanceof BusinessException businessException) return businessException;
        return new BusinessException(errorCode(error));
    }

    private static String errorCode(Throwable error) {
        if (error instanceof ProviderAdapterException providerException) {
            return switch (providerException.code()) {
                case RATE_LIMITED -> "PROVIDER_RATE_LIMITED";
                case HARD_TIMEOUT -> "CALL_TIMEOUT";
                default -> "PROVIDER_ERROR";
            };
        }
        return "PROVIDER_ERROR";
    }

    private final class InvocationResources implements AutoCloseable {
        private final String invocationId;
        private final ModelInvocationContext context;
        private final long startedAt;
        private final ModelInvocationPermit permit;
        private final ChatModelFactory.ChatModelLease lease;
        private final AtomicBoolean closed = new AtomicBoolean();

        private InvocationResources(String invocationId, ModelInvocationContext context, long startedAt, ModelInvocationPermit permit,
                                    ChatModelFactory.ChatModelLease lease) {
            this.invocationId = invocationId;
            this.context = context;
            this.startedAt = startedAt;
            this.permit = permit;
            this.lease = lease;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                try {
                    if (lease != null) lease.close();
                } finally {
                    try {
                        permit.close();
                    } finally {
                        activeInvocations.remove(invocationId, this);
                    }
                }
            }
        }
    }
}
