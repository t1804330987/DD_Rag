package com.dong.ddrag.modelplatform.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DashScopeProviderAdapterTest {
    @Test
    void shouldBuildDiscoverAndEnforceDeadline() {
        var fixture = ProviderAdapterTestSupport.fixture(DashScopeChatModelProviderAdapter::new,
                "{\"data\":[{\"id\":\"qwen-max\"}]}");
        var connection = new ProviderConnectionSnapshot("https://dashscope.example", "test-key",
                Map.of("workspaceId", "workspace"));

        ProviderAdapterTestSupport.verifyCommonBehavior(fixture, connection, "qwen-max");

        assertEquals("https://dashscope.example/compatible-mode/v1/models",
                fixture.request().get().uri().toString());
        assertEquals("Bearer test-key", fixture.request().get().headers().get("Authorization"));
    }
}
