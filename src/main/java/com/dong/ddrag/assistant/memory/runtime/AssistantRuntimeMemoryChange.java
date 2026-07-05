package com.dong.ddrag.assistant.memory.runtime;

public record AssistantRuntimeMemoryChange(
        AssistantRuntimeMemoryAction action,
        String targetKeyId,
        String keyLabel,
        String value,
        String reason,
        String confirmationQuestion
) {
}
