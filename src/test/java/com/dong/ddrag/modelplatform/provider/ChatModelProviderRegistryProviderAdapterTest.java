package com.dong.ddrag.modelplatform.provider;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatModelProviderRegistryProviderAdapterTest {
    @Test
    void shouldResolveRegisteredAdapter() {
        ChatModelProviderAdapter adapter = mock(ChatModelProviderAdapter.class);
        when(adapter.providerType()).thenReturn(ProviderType.OPENAI);

        ChatModelProviderRegistry registry = new ChatModelProviderRegistry(List.of(adapter));

        assertSame(adapter, registry.require(ProviderType.OPENAI));
    }

    @Test
    void shouldRejectDuplicateProvider() {
        ChatModelProviderAdapter first = mock(ChatModelProviderAdapter.class);
        ChatModelProviderAdapter second = mock(ChatModelProviderAdapter.class);
        when(first.providerType()).thenReturn(ProviderType.OPENAI);
        when(second.providerType()).thenReturn(ProviderType.OPENAI);

        assertThrows(IllegalArgumentException.class,
                () -> new ChatModelProviderRegistry(List.of(first, second)));
    }

    @Test
    void shouldRejectUnknownProvider() {
        ChatModelProviderRegistry registry = new ChatModelProviderRegistry(List.of());

        assertThrows(IllegalArgumentException.class, () -> registry.require(ProviderType.GEMINI));
    }
}
