package com.dong.ddrag.modelplatform.provider;

import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import java.util.List;

public record ProviderConnectionSchema(
        ProviderType providerType, String defaultBaseUrl, List<ProviderFieldSchema> fields) {
    public ProviderConnectionSchema {
        fields = List.copyOf(fields);
    }
}
