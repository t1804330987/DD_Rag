package com.dong.ddrag.auth.security;

import com.dong.ddrag.common.api.ApiResponse;
import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTHENTICATED_USER_REQUEST_ATTRIBUTE =
            JwtAuthenticationFilter.class.getName() + ".AUTHENTICATED_USER";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String REGISTER_PATH = "/api/auth/register";
    private static final String RESET_PASSWORD_PATH = "/api/auth/reset-password";
    private static final String REFRESH_PATH = "/api/auth/refresh";
    private static final String LOGOUT_PATH = "/api/auth/logout";

    private final JwtAccessTokenService jwtAccessTokenService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtAccessTokenService jwtAccessTokenService,
            ObjectMapper objectMapper
    ) {
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String accessToken = authorization.substring(BEARER_PREFIX.length()).trim();
        if (accessToken.isEmpty()) {
            writeUnauthorized(response, "access token 非法或已过期");
            return;
        }
        try {
            JwtAccessTokenService.AccessTokenClaims claims = jwtAccessTokenService.parse(accessToken);
            request.setAttribute(
                    AUTHENTICATED_USER_REQUEST_ATTRIBUTE,
                    new AuthenticatedUser(
                            claims.userId(),
                            claims.userCode(),
                            claims.displayName(),
                            claims.systemRole(),
                            claims.mustChangePassword()
                    )
            );
            filterChain.doFilter(request, response);
        } catch (BusinessException exception) {
            writeUnauthorized(response, exception.getMessage());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return LOGIN_PATH.equals(requestUri)
                || REGISTER_PATH.equals(requestUri)
                || RESET_PASSWORD_PATH.equals(requestUri)
                || REFRESH_PATH.equals(requestUri)
                || LOGOUT_PATH.equals(requestUri);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ApiResponse<>(false, null, message));
    }

    public record AuthenticatedUser(
            Long userId,
            String userCode,
            String displayName,
            SystemRole systemRole,
            boolean mustChangePassword
    ) {
    }
}
