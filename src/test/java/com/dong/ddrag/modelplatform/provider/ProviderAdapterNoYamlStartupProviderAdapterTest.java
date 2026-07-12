package com.dong.ddrag.modelplatform.provider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderAdapterNoYamlStartupProviderAdapterTest {
    @Test
    void shouldConstructRegistryWithoutYamlCredentialsOrNetworkCalls() {
        assertDoesNotThrow(() -> new ChatModelProviderRegistry(List.of(
                new DashScopeChatModelProviderAdapter(),
                new OpenAiChatModelProviderAdapter(),
                new GeminiChatModelProviderAdapter(),
                new AnthropicChatModelProviderAdapter())));
    }
}
