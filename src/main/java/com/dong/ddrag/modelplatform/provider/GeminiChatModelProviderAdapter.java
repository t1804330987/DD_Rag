package com.dong.ddrag.modelplatform.provider;

import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class GeminiChatModelProviderAdapter extends AbstractChatModelProviderAdapter {
    public GeminiChatModelProviderAdapter() {
        this(new JdkProviderHttpTransport(Duration.ofSeconds(10)), SpringAiProviderModels::gemini,
                new ObjectMapper(), DEFAULT_HARD_DEADLINE);
    }

    GeminiChatModelProviderAdapter(ProviderHttpTransport transport, ProviderChatModelFactory factory,
            ObjectMapper objectMapper, Duration deadline) {
        super(schema(), transport, factory, objectMapper, deadline);
    }

    @Override
    protected URI discoveryUri(String baseUrl, ProviderConnectionSnapshot connection) {
        String version = connection.options().getOrDefault("apiVersion", "v1beta").toString()
                .replaceAll("^/+|/+$", "");
        String root = RelayCompatibleGeminiChatModel.stripApiVersionSuffix(baseUrl);
        return URI.create(root + "/" + version + "/models");
    }

    @Override
    protected Map<String, String> discoveryHeaders(ProviderConnectionSnapshot connection) {
        return Map.of("x-goog-api-key", connection.apiKey());
    }

    @Override
    public ModelCapabilities describeCapabilities(ProviderConnectionSnapshot connection, DiscoveredModel model) {
        String name = model.name().toLowerCase();
        return new ModelCapabilities(true, true, name.contains("gemini"));
    }

    private static ProviderConnectionSchema schema() {
        return new ProviderConnectionSchema(ProviderType.GEMINI, "https://generativelanguage.googleapis.com", List.of(
                new ProviderFieldSchema("baseUrl", "url", false, false,
                        "https://generativelanguage.googleapis.com"),
                new ProviderFieldSchema("apiKey", "password", true, true, null),
                new ProviderFieldSchema("apiVersion", "text", false, false, "v1beta")));
    }
}
