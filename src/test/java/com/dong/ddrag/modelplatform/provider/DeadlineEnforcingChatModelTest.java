package com.dong.ddrag.modelplatform.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class DeadlineEnforcingChatModelTest {
    @Test
    void closeDelegatesToCloseableProviderModel() throws Exception {
        AtomicInteger closes = new AtomicInteger();
        DeadlineEnforcingChatModel model = new DeadlineEnforcingChatModel(
                new CloseableChatModel(closes), Duration.ofMinutes(1));

        model.close();

        assertEquals(1, closes.get());
    }

    private record CloseableChatModel(AtomicInteger closes) implements ChatModel, AutoCloseable {
        @Override public ChatResponse call(Prompt prompt) { return null; }
        @Override public Flux<ChatResponse> stream(Prompt prompt) { return Flux.empty(); }
        @Override public void close() { closes.incrementAndGet(); }
    }
}
