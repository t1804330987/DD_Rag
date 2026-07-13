package com.dong.ddrag.modelplatform.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;

class OpenAiProviderAdapterTest {
    @Test
    void shouldRequestUsageMetadataForStreamingCalls() {
        var model = SpringAiProviderModels.openAi(new ProviderChatModelSettings(ProviderType.OPENAI,
                "https://openai.example/v1", "test-key", "gpt-test", Map.of(), Duration.ofSeconds(5)));

        assertTrue(((OpenAiChatOptions) model.getDefaultOptions()).getStreamUsage());
    }

    @Test
    void shouldAppendOpenAiV1PathWhenCustomBaseUrlIsProviderRoot() {
        var fixture = ProviderAdapterTestSupport.fixture(OpenAiChatModelProviderAdapter::new,
                "{\"data\":[{\"id\":\"gpt-4o\"}]}");

        fixture.adapter().discoverModels(
                new ProviderConnectionSnapshot("https://openai.example", "test-key", Map.of()));

        assertEquals("https://openai.example/v1/models", fixture.request().get().uri().toString());
    }

    @Test
    void shouldBuildDiscoverAndEnforceDeadline() {
        var fixture = ProviderAdapterTestSupport.fixture(OpenAiChatModelProviderAdapter::new,
                "{\"data\":[{\"id\":\"gpt-4o\"}]}");
        var connection = new ProviderConnectionSnapshot("https://openai.example/v1", "test-key", Map.of());

        ProviderAdapterTestSupport.verifyCommonBehavior(fixture, connection, "gpt-4o");

        assertEquals("https://openai.example/v1/models", fixture.request().get().uri().toString());
        assertEquals("Bearer test-key", fixture.request().get().headers().get("Authorization"));
        assertTrue(fixture.adapter().connectionSchema().fields().stream()
                .anyMatch(field -> field.name().equals("apiKey") && field.sensitive()));
    }
}
