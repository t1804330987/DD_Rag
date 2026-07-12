package com.dong.ddrag.assistant.model.dto.session;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SelectAssistantModelRequest(
        @NotNull(message = "connectionId 不能为空") @Positive(message = "connectionId 非法") Long connectionId,
        @NotNull(message = "modelId 不能为空") @Positive(message = "modelId 非法") Long modelId
) { }
