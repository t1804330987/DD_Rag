package com.dong.ddrag.modelplatform.provider;

import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class DashScopeChatModelProviderAdapter extends AbstractChatModelProviderAdapter {
    public DashScopeChatModelProviderAdapter() {
        this(new JdkProviderHttpTransport(Duration.ofSeconds(10)), SpringAiProviderModels::dashScope,
                new ObjectMapper(), DEFAULT_HARD_DEADLINE);
    }

    DashScopeChatModelProviderAdapter(ProviderHttpTransport transport, ProviderChatModelFactory factory,
            ObjectMapper objectMapper, Duration deadline) {
        super(schema(), transport, factory, objectMapper, deadline);
    }

    @Override
    protected URI discoveryUri(String baseUrl, ProviderConnectionSnapshot connection) {
        String hostRoot = baseUrl.replaceFirst("/compatible-mode/v1$", "");
        return URI.create(hostRoot + "/compatible-mode/v1/models");
    }

    @Override
    protected Map<String, String> discoveryHeaders(ProviderConnectionSnapshot connection) {
        return Map.of("Authorization", "Bearer " + connection.apiKey());
    }

    @Override
    public ModelCapabilities describeCapabilities(ProviderConnectionSnapshot connection, DiscoveredModel model) {
        String name = model.name().toLowerCase();
        return new ModelCapabilities(true, true,
                name.contains("vl") || name.contains("qwen3") || name.contains("qwen-max"));
    }

    private static ProviderConnectionSchema schema() {
        return new ProviderConnectionSchema(ProviderType.DASHSCOPE, "https://dashscope.aliyuncs.com", List.of(
                new ProviderFieldSchema("baseUrl", "url", false, false, "https://dashscope.aliyuncs.com"),
                new ProviderFieldSchema("apiKey", "password", true, true, null),
                new ProviderFieldSchema("workspaceId", "text", false, false, null)));
    }
}
