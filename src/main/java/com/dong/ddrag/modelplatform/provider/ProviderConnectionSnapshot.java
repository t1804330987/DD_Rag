package com.dong.ddrag.modelplatform.provider;

import java.util.Map;

public record ProviderConnectionSnapshot(String baseUrl, String apiKey, Map<String, Object> options) {
    public ProviderConnectionSnapshot {
        options = options == null ? Map.of() : Map.copyOf(options);
    }

    @Override
    public String toString() {
        return "ProviderConnectionSnapshot{baseUrl='" + baseUrl + "', optionKeys=" + options.keySet() + "}";
    }
}
