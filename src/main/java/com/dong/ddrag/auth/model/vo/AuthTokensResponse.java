package com.dong.ddrag.auth.model.vo;

import com.dong.ddrag.auth.service.AuthService.AuthTokens;
import com.dong.ddrag.identity.service.CurrentUserService;

public record AuthTokensResponse(
        String accessToken,
        CurrentUserProfileResponse currentUser
) {

    public static AuthTokensResponse from(AuthTokens tokens, CurrentUserService.CurrentUser currentUser) {
        return new AuthTokensResponse(tokens.accessToken(), CurrentUserProfileResponse.from(currentUser));
    }
}
