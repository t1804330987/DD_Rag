package com.dong.ddrag.auth.security;

import com.dong.ddrag.auth.config.AuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthCookieSupport {

    private static final String COOKIE_PATH = "/";
    private static final String SAME_SITE_POLICY = "Lax";

    private final AuthProperties authProperties;

    public AuthCookieSupport(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public void writeRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                buildCookie(refreshToken, Duration.ofDays(authProperties.getRefreshTokenExpireDays())).toString()
        );
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", Duration.ZERO).toString());
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        return ResponseCookie.from(authProperties.getRefreshCookieName(), value)
                .httpOnly(true)
                .secure(authProperties.isRefreshCookieSecure())
                .path(COOKIE_PATH)
                .sameSite(SAME_SITE_POLICY)
                .maxAge(maxAge)
                .build();
    }
}
