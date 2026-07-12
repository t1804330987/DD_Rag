package com.dong.ddrag.modelplatform.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AnthropicProviderAdapterTest {
    @Test
    void shouldBuildDiscoverAndEnforceDeadline() {
        var fixture = ProviderAdapterTestSupport.fixture(AnthropicChatModelProviderAdapter::new,
                "{\"data\":[{\"id\":\"claude-3-7-sonnet\",\"display_name\":\"Claude\"}]}");
        var connection = new ProviderConnectionSnapshot("https://anthropic.example", "test-key",
                Map.of("anthropicVersion", "2023-06-01"));

        ProviderAdapterTestSupport.verifyCommonBehavior(fixture, connection, "claude-3-7-sonnet");

        assertEquals("https://anthropic.example/v1/models", fixture.request().get().uri().toString());
        assertEquals("test-key", fixture.request().get().headers().get("x-api-key"));
    }

    @Test
    void shouldNotDoubleAppendV1WhenBaseUrlAlreadyIncludesVersion() {
        var fixture = ProviderAdapterTestSupport.fixture(AnthropicChatModelProviderAdapter::new,
                "{\"data\":[{\"id\":\"claude-haiku-4-5-20251001\"}]}");

        fixture.adapter().discoverModels(
                new ProviderConnectionSnapshot("https://anthropic.example/v1", "test-key", Map.of()));

        assertEquals("https://anthropic.example/v1/models", fixture.request().get().uri().toString());
    }
}
