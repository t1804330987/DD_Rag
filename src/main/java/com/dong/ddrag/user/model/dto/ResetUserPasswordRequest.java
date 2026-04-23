package com.dong.ddrag.user.model.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetUserPasswordRequest(
        @NotBlank(message = "新密码不能为空")
        String newPassword
) {
}
