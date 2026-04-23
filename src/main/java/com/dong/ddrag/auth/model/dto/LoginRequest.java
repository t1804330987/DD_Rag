package com.dong.ddrag.auth.model.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "登录标识不能为空")
        String loginId,
        @NotBlank(message = "密码不能为空")
        String password
) {
}
