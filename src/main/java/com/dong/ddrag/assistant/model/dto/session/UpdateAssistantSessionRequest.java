package com.dong.ddrag.assistant.model.dto.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAssistantSessionRequest(
        @NotBlank(message = "title 不能为空")
        @Size(max = 255, message = "title 长度不能超过 255")
        String title
) {
}
