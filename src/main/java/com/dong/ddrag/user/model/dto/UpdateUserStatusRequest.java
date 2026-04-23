package com.dong.ddrag.user.model.dto;

import com.dong.ddrag.common.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "用户状态不能为空")
        UserStatus status
) {
}
