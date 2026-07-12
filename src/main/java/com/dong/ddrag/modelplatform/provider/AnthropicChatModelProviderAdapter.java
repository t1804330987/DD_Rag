package com.dong.ddrag.modelplatform.provider;

import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class AnthropicChatModelProviderAdapter extends AbstractChatModelProviderAdapter {
    public AnthropicChatModelProviderAdapter() {
        this(new JdkProviderHttpTransport(Duration.ofSeconds(10)), SpringAiProviderModels::anthropic,
                new ObjectMapper(), DEFAULT_HARD_DEADLINE);
    }

    AnthropicChatModelProviderAdapter(ProviderHttpTransport transport, ProviderChatModelFactory factory,
            ObjectMapper objectMapper, Duration deadline) {
        super(schema(), transport, factory, objectMapper, deadline);
    }

    @Override
    protected URI discoveryUri(String baseUrl, ProviderConnectionSnapshot connection) {
        // Align with OpenAI: accept either provider root or root already ending with /v1.
        return URI.create(baseUrl + (baseUrl.endsWith("/v1") ? "/models" : "/v1/models"));
    }

    @Override
    protected Map<String, String> discoveryHeaders(ProviderConnectionSnapshot connection) {
        String version = connection.options().getOrDefault("anthropicVersion", "2023-06-01").toString();
        return Map.of("x-api-key", connection.apiKey(), "anthropic-version", version);
    }

    @Override
    public ModelCapabilities describeCapabilities(ProviderConnectionSnapshot connection, DiscoveredModel model) {
        return new ModelCapabilities(true, true, model.name().toLowerCase().contains("claude-3"));
    }

    private static ProviderConnectionSchema schema() {
        return new ProviderConnectionSchema(ProviderType.ANTHROPIC, "https://api.anthropic.com", List.of(
                new ProviderFieldSchema("baseUrl", "url", false, false, "https://api.anthropic.com"),
                new ProviderFieldSchema("apiKey", "password", true, true, null),
                new ProviderFieldSchema("anthropicVersion", "text", false, false, "2023-06-01")));
    }
}
