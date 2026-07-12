package com.dong.ddrag.modelplatform.provider;

import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import java.time.Duration;
import java.util.Map;

record ProviderChatModelSettings(
        ProviderType providerType,
        String baseUrl,
        String apiKey,
        String modelName,
        Map<String, Object> options,
        Duration transportDeadline) {
    @Override
    public String toString() {
        return "ProviderChatModelSettings{providerType=" + providerType + ", baseUrl='" + baseUrl
                + "', modelName='" + modelName + "', optionKeys=" + options.keySet()
                + ", transportDeadline=" + transportDeadline + "}";
    }
}
