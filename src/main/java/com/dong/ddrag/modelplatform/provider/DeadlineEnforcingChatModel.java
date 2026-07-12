package com.dong.ddrag.modelplatform.provider;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

final class DeadlineEnforcingChatModel implements ChatModel, AutoCloseable {
    private static final ExecutorService BLOCKING_CALLS = Executors.newVirtualThreadPerTaskExecutor();

    private final ChatModel delegate;
    private final Duration deadline;

    DeadlineEnforcingChatModel(ChatModel delegate, Duration deadline) {
        this.delegate = delegate;
        this.deadline = deadline;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        Future<ChatResponse> future = BLOCKING_CALLS.submit(() -> delegate.call(prompt));
        try {
            return future.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException exception) {
            future.cancel(true);
            throw new ProviderAdapterException(ProviderErrorCode.HARD_TIMEOUT, exception);
        }
        catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ProviderAdapterException(ProviderErrorCode.NETWORK_ERROR, exception);
        }
        catch (ExecutionException exception) {
            throw ProviderExceptionClassifier.classify(exception.getCause());
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.create(sink -> {
            Disposable.Composite subscriptions = Disposables.composite();
            subscriptions.add(delegate.stream(prompt).subscribe(sink::next,
                    error -> sink.error(ProviderExceptionClassifier.classify(error)), sink::complete));
            subscriptions.add(Schedulers.parallel().schedule(() -> {
                subscriptions.dispose();
                sink.error(new ProviderAdapterException(ProviderErrorCode.HARD_TIMEOUT));
            }, deadline.toMillis(), TimeUnit.MILLISECONDS));
            sink.onDispose(subscriptions);
        });
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return delegate.getDefaultOptions();
    }

    @Override
    public void close() throws Exception {
        if (delegate instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }
}
