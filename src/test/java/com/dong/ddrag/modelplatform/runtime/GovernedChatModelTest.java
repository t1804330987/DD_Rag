package com.dong.ddrag.modelplatform.runtime;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dong.ddrag.modelplatform.model.enums.ConnectionOwnerType;
import com.dong.ddrag.modelplatform.model.enums.ModelScenario;
import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class GovernedChatModelTest {
    @Test
    void delegatesEveryCallAndStreamToDispatcher() {
        ModelInvocationDispatcher dispatcher = org.mockito.Mockito.mock(ModelInvocationDispatcher.class);
        ModelInvocationContext context = new ModelInvocationContext(7L, ModelScenario.ASSISTANT_CHAT,
                11L, 21L, 3L, ProviderType.DASHSCOPE, "qwen", "primary",
                ConnectionOwnerType.PLATFORM, 31L, null, null, "turn", "request");
        Prompt prompt = new Prompt("hello");
        ChatResponse response = org.mockito.Mockito.mock(ChatResponse.class);
        Flux<ChatResponse> stream = Flux.just(response);
        when(dispatcher.call(context, prompt)).thenReturn(response);
        when(dispatcher.stream(context, prompt)).thenReturn(stream);

        GovernedChatModel model = new GovernedChatModel(context, dispatcher);

        assertSame(response, model.call(prompt));
        assertSame(stream, model.stream(prompt));
        verify(dispatcher).call(context, prompt);
        verify(dispatcher).stream(context, prompt);
    }
}
