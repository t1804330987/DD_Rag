package com.dong.ddrag.auth.model.vo;

import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.identity.service.CurrentUserService;

public record CurrentUserProfileResponse(
        Long userId,
        String userCode,
        String displayName,
        SystemRole systemRole,
        boolean mustChangePassword
) {

    public static CurrentUserProfileResponse from(CurrentUserService.CurrentUser currentUser) {
        return new CurrentUserProfileResponse(
                currentUser.userId(),
                currentUser.userCode(),
                currentUser.displayName(),
                currentUser.systemRole(),
                currentUser.mustChangePassword()
        );
    }
}
