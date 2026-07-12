package com.dong.ddrag.modelplatform.provider;

import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class OpenAiChatModelProviderAdapter extends AbstractChatModelProviderAdapter {
    public OpenAiChatModelProviderAdapter() {
        this(new JdkProviderHttpTransport(Duration.ofSeconds(10)), SpringAiProviderModels::openAi,
                new ObjectMapper(), DEFAULT_HARD_DEADLINE);
    }

    OpenAiChatModelProviderAdapter(ProviderHttpTransport transport, ProviderChatModelFactory factory,
            ObjectMapper objectMapper, Duration deadline) {
        super(schema(), transport, factory, objectMapper, deadline);
    }

    @Override
    protected URI discoveryUri(String baseUrl, ProviderConnectionSnapshot connection) {
        return URI.create(baseUrl + (baseUrl.endsWith("/v1") ? "/models" : "/v1/models"));
    }

    @Override
    protected Map<String, String> discoveryHeaders(ProviderConnectionSnapshot connection) {
        return Map.of("Authorization", "Bearer " + connection.apiKey());
    }

    @Override
    public ModelCapabilities describeCapabilities(ProviderConnectionSnapshot connection, DiscoveredModel model) {
        String name = model.name().toLowerCase();
        return new ModelCapabilities(true, true, name.contains("gpt-4") || name.contains("gpt-5"));
    }

    private static ProviderConnectionSchema schema() {
        return new ProviderConnectionSchema(ProviderType.OPENAI, "https://api.openai.com/v1", List.of(
                new ProviderFieldSchema("baseUrl", "url", false, false, "https://api.openai.com/v1"),
                new ProviderFieldSchema("apiKey", "password", true, true, null)));
    }
}
