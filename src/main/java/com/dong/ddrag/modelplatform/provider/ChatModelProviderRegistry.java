package com.dong.ddrag.modelplatform.provider;

import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class ChatModelProviderRegistry {
    private final Map<ProviderType, ChatModelProviderAdapter> adapters;

    public ChatModelProviderRegistry(List<ChatModelProviderAdapter> adapters) {
        EnumMap<ProviderType, ChatModelProviderAdapter> byType = new EnumMap<>(ProviderType.class);
        for (ChatModelProviderAdapter adapter : adapters) {
            if (byType.putIfAbsent(adapter.providerType(), adapter) != null) {
                throw new IllegalArgumentException("Duplicate model provider adapter: " + adapter.providerType());
            }
        }
        this.adapters = Map.copyOf(byType);
    }

    public ChatModelProviderAdapter require(ProviderType providerType) {
        ChatModelProviderAdapter adapter = adapters.get(providerType);
        if (adapter == null) {
            throw new IllegalArgumentException("Unsupported model provider: " + providerType);
        }
        return adapter;
    }

    /** Returns presentation-safe provider metadata; credentials are not part of schemas. */
    public List<ProviderConnectionSchema> connectionSchemas() {
        return adapters.values().stream()
                .map(ChatModelProviderAdapter::connectionSchema)
                .sorted(java.util.Comparator.comparing(schema -> schema.providerType().name()))
                .toList();
    }
}
