package com.dong.ddrag.modelplatform.runtime;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public interface ModelInvocationDispatcher {
    ChatResponse call(ModelInvocationContext context, Prompt prompt);

    Flux<ChatResponse> stream(ModelInvocationContext context, Prompt prompt);

    default Flux<ChatResponse> stream(ModelInvocationContext context, Prompt prompt,
                                      ModelCallCancellation cancellation) {
        return stream(context, prompt);
    }

    default ChatOptions defaultOptions(ModelInvocationContext context) {
        return null;
    }
}
