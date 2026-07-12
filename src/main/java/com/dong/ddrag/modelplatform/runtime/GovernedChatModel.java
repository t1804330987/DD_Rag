package com.dong.ddrag.modelplatform.runtime;

import java.util.Objects;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public final class GovernedChatModel implements ChatModel {
    private final ModelInvocationContext context;
    private final ModelInvocationDispatcher dispatcher;
    private final ModelCallCancellation cancellation;

    public GovernedChatModel(ModelInvocationContext context, ModelInvocationDispatcher dispatcher) {
        this(context, dispatcher, null);
    }

    public GovernedChatModel(ModelInvocationContext context, ModelInvocationDispatcher dispatcher,
                             ModelCallCancellation cancellation) {
        this.context = Objects.requireNonNull(context, "context");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.cancellation = cancellation;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return dispatcher.call(context, prompt);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return cancellation == null ? dispatcher.stream(context, prompt) : dispatcher.stream(context, prompt, cancellation);
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return dispatcher.defaultOptions(context);
    }
}
