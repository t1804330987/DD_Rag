package com.dong.ddrag.assistant.support.config;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import org.springframework.stereotype.Component;

@Component
public class AssistantRunnableConfigFactory {

    public RunnableConfig create(
            Long userId,
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId
    ) {
        String threadId = "user:%d:session:%d".formatted(userId, sessionId);
        RunnableConfig.Builder builder = RunnableConfig.builder()
                .threadId(threadId)
                .addMetadata("userId", userId)
                .addMetadata("sessionId", sessionId)
                .addMetadata("toolMode", toolMode == null ? null : toolMode.name());
        if (groupId != null) {
            builder.addMetadata("groupId", groupId);
        }
        return builder.build();
    }
}
