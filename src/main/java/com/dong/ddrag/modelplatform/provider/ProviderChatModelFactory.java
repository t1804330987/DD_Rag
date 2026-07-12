package com.dong.ddrag.modelplatform.provider;

import org.springframework.ai.chat.model.ChatModel;

@FunctionalInterface
interface ProviderChatModelFactory {
    ChatModel create(ProviderChatModelSettings settings);
}
