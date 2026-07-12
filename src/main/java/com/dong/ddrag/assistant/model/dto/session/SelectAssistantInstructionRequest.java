package com.dong.ddrag.assistant.model.dto.session;

import jakarta.validation.constraints.Positive;

public record SelectAssistantInstructionRequest(
        @Positive(message = "instructionProfileId 非法") Long instructionProfileId
) { }
