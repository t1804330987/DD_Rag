package com.dong.ddrag.modelplatform.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GeminiProviderAdapterTest {
    @Test
    void shouldBuildDiscoverAndEnforceDeadline() {
        var fixture = ProviderAdapterTestSupport.fixture(GeminiChatModelProviderAdapter::new,
                "{\"models\":[{\"name\":\"models/gemini-2.5-flash\",\"displayName\":\"Gemini 2.5 Flash\"}]}");
        var connection = new ProviderConnectionSnapshot("https://gemini.example", "test key",
                Map.of("apiVersion", "v1beta"));

        ProviderAdapterTestSupport.verifyCommonBehavior(fixture, connection, "gemini-2.5-flash");

        assertEquals("https://gemini.example/v1beta/models", fixture.request().get().uri().toString());
        assertEquals("test key", fixture.request().get().headers().get("x-goog-api-key"));
        assertTrue(!fixture.request().get().uri().toString().contains("test"));
    }

    @Test
    void shouldNotDoubleAppendApiVersionWhenBaseUrlAlreadyIncludesIt() {
        var fixture = ProviderAdapterTestSupport.fixture(GeminiChatModelProviderAdapter::new,
                "{\"models\":[{\"name\":\"models/gemini-2.5-flash\"}]}");

        fixture.adapter().discoverModels(new ProviderConnectionSnapshot(
                "https://gemini.example/v1beta", "test key", Map.of("apiVersion", "v1beta")));

        assertEquals("https://gemini.example/v1beta/models", fixture.request().get().uri().toString());
    }
}
