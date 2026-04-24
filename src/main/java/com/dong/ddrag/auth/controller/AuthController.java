package com.dong.ddrag.auth.controller;

import com.dong.ddrag.auth.config.AuthProperties;
import com.dong.ddrag.auth.model.dto.LoginRequest;
import com.dong.ddrag.auth.model.dto.RegisterRequest;
import com.dong.ddrag.auth.model.dto.ResetPasswordByIdentityRequest;
import com.dong.ddrag.auth.model.vo.AuthTokensResponse;
import com.dong.ddrag.auth.model.vo.CurrentUserProfileResponse;
import com.dong.ddrag.auth.security.AuthCookieSupport;
import com.dong.ddrag.auth.service.AuthService;
import com.dong.ddrag.auth.service.AuthService.AuthTokens;
import com.dong.ddrag.common.api.ApiResponse;
import com.dong.ddrag.identity.service.CurrentUserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieSupport authCookieSupport;
    private final CurrentUserService currentUserService;
    private final AuthProperties authProperties;

    public AuthController(
            AuthService authService,
            AuthCookieSupport authCookieSupport,
            CurrentUserService currentUserService,
            AuthProperties authProperties
    ) {
        this.authService = authService;
        this.authCookieSupport = authCookieSupport;
        this.currentUserService = currentUserService;
        this.authProperties = authProperties;
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokensResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthTokens tokens = authService.login(request.loginId(), request.password());
        authCookieSupport.writeRefreshTokenCookie(response, tokens.refreshToken());
        return ApiResponse.success(AuthTokensResponse.from(tokens, authService.getCurrentUser(tokens.userId())));
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordByIdentityRequest request) {
        authService.resetPasswordByIdentity(request.username(), request.email(), request.newPassword());
        return ApiResponse.success(null);
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokensResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = extractRefreshToken(request);
        AuthTokens tokens = authService.refresh(refreshToken);
        authCookieSupport.writeRefreshTokenCookie(response, tokens.refreshToken());
        return ApiResponse.success(AuthTokensResponse.from(tokens, authService.getCurrentUser(tokens.userId())));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authService.logout(extractRefreshToken(request));
        authCookieSupport.clearRefreshTokenCookie(response);
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserProfileResponse> currentUser(HttpServletRequest request) {
        return ApiResponse.success(CurrentUserProfileResponse.from(currentUserService.getRequiredCurrentUser(request)));
    }

    private String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        String refreshCookieName = authProperties.getRefreshCookieName();
        for (Cookie cookie : cookies) {
            if (refreshCookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
