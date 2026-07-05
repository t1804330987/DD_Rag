package com.dong.ddrag.assistant.memory.runtime;

import java.util.List;

public record AssistantRuntimeMemoryState(
        Long version,
        List<Conclusion> conclusions,
        Pending pending
) {

    public static AssistantRuntimeMemoryState empty() {
        return new AssistantRuntimeMemoryState(0L, List.of(), null);
    }

    public List<Conclusion> conclusions() {
        return conclusions == null ? List.of() : conclusions;
    }

    public Long version() {
        return version == null ? 0L : version;
    }

    public record Conclusion(
            String keyId,
            String keyLabel,
            String activeValue,
            Long activeSourceMessageId,
            Long activeCreatedAt,
            List<SupersededValue> supersededValues
    ) {

        public List<SupersededValue> supersededValues() {
            return supersededValues == null ? List.of() : supersededValues;
        }
    }

    public record SupersededValue(
            String value,
            Long sourceMessageId,
            Long supersededAt,
            Long supersededByMessageId,
            String reason
    ) {
    }

    public record Pending(
            AssistantRuntimeMemoryAction action,
            String targetKeyId,
            String proposedKeyLabel,
            String proposedValue,
            Long originalUserMessageId,
            String originalUserRequest,
            String confirmationQuestion,
            Long createdAt
    ) {
    }
}
