package com.dong.ddrag.user.model.vo;

import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.common.enums.UserStatus;

import java.time.LocalDateTime;

public record AdminUserItemResponse(
        Long userId,
        String userCode,
        String username,
        String email,
        String displayName,
        SystemRole systemRole,
        UserStatus status,
        boolean mustChangePassword,
        LocalDateTime lastLoginAt
) {
}
