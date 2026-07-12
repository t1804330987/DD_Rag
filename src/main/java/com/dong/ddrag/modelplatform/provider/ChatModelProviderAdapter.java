package com.dong.ddrag.modelplatform.provider;

import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ChatModel;

public interface ChatModelProviderAdapter {
    ProviderType providerType();

    ProviderConnectionSchema connectionSchema();

    void validateConnectionOptions(Map<String, Object> options);

    ChatModel createChatModel(ProviderConnectionSnapshot connection, String modelName);

    List<DiscoveredModel> discoverModels(ProviderConnectionSnapshot connection);

    ConnectionProbeResult probeConnection(ProviderConnectionSnapshot connection);

    ModelCapabilities describeCapabilities(ProviderConnectionSnapshot connection, DiscoveredModel model);
}
