package com.dong.ddrag.modelplatform.runtime;

import com.dong.ddrag.modelplatform.model.enums.ConnectionOwnerType;
import com.dong.ddrag.modelplatform.model.enums.ModelScenario;
import com.dong.ddrag.modelplatform.model.enums.ProviderType;

public record ModelInvocationContext(
        Long userId,
        ModelScenario scenario,
        Long connectionId,
        Long modelId,
        Long configVersion,
        ProviderType providerType,
        String modelName,
        String connectionName,
        ConnectionOwnerType ownerType,
        Long sessionId,
        Long userMessageId,
        Long assistantMessageId,
        String turnId,
        String requestId,
        Long instructionProfileId,
        Long instructionVersionId,
        Integer instructionVersionSnapshot) {

    public ModelInvocationContext {
        boolean connectionTest = scenario == ModelScenario.CONNECTION_TEST;
        if (userId == null || scenario == null || connectionId == null
                || (!connectionTest && modelId == null)
                || configVersion == null || providerType == null || modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("Model invocation identifiers are required");
        }
    }

    public ModelInvocationContext(Long userId, ModelScenario scenario, Long connectionId, Long modelId,
                                  Long configVersion, ProviderType providerType, String modelName,
                                  String connectionName, ConnectionOwnerType ownerType, Long sessionId,
                                  Long userMessageId, Long assistantMessageId, String turnId, String requestId) {
        this(userId, scenario, connectionId, modelId, configVersion, providerType, modelName, connectionName,
                ownerType, sessionId, userMessageId, assistantMessageId, turnId, requestId, null, null, null);
    }

    public ModelInvocationContext withInstruction(InstructionSnapshot instruction) {
        if (instruction == null || instruction.profileId() == null) return this;
        return new ModelInvocationContext(userId, scenario, connectionId, modelId, configVersion, providerType,
                modelName, connectionName, ownerType, sessionId, userMessageId, assistantMessageId, turnId,
                requestId, instruction.profileId(), instruction.versionId(), instruction.version());
    }

    public record InstructionSnapshot(Long profileId, Long versionId, Integer version) { }
}
