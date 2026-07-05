package com.dong.ddrag.harness.identity;

import com.dong.ddrag.auth.security.JwtAccessTokenService;
import com.dong.ddrag.auth.security.JwtAuthenticationFilter;
import com.dong.ddrag.auth.security.RefreshTokenService;
import com.dong.ddrag.auth.service.AuthService;
import com.dong.ddrag.auth.service.PasswordHasher;
import com.dong.ddrag.auth.service.RefreshTokenRecord;
import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.common.enums.UserStatus;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.common.exception.ForbiddenException;
import com.dong.ddrag.identity.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@SuppressWarnings({"rawtypes", "unchecked"})
class IdentityAccessHarnessTest {

    private static final Long USER_ID = 1001L;
    private static final String USER_CODE = "u1001";

    @Test
    void validActiveUserLoginIssuesTokensAndRecordsLoginSideEffects() throws Exception {
        HarnessRuntime runtime = createAuthRuntime();
        givenLoginUser(runtime.jdbcTemplate(), USER_ID, USER_CODE, SystemRole.USER, UserStatus.ACTIVE, true);
        given(runtime.passwordHasher().matches("UserPass123", "hash-1001")).willReturn(true);
        given(runtime.jwtAccessTokenService().issueToken(any(JwtAccessTokenService.TokenSubject.class)))
                .willReturn("access-token");
        given(runtime.refreshTokenService().issueToken(USER_ID))
                .willReturn(new RefreshTokenService.IssuedRefreshToken(
                        "refresh-token",
                        new RefreshTokenRecord(5001L, USER_ID, "rt-1", "rt-hash", LocalDateTime.now().plusDays(1), null)
                ));

        AuthService.AuthTokens tokens = runtime.authService().login(USER_CODE, "UserPass123");

        assertThat(tokens.userId()).isEqualTo(USER_ID);
        assertThat(tokens.accessToken()).isEqualTo("access-token");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-token");
        assertThat(tokens.mustChangePassword()).isTrue();
        then(runtime.refreshTokenService()).should().revokeActiveTokens(USER_ID);
        then(runtime.refreshTokenService()).should().issueToken(USER_ID);
        then(runtime.jwtAccessTokenService()).should().issueToken(
                new JwtAccessTokenService.TokenSubject(USER_ID, USER_CODE, "用户-u1001", SystemRole.USER, true)
        );
        then(runtime.jdbcTemplate()).should().update(
                contains("update users set last_login_at"),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(USER_ID)
        );
    }

    @Test
    void disabledUserLoginIsRejectedWithoutTokenOrLoginSideEffects() throws Exception {
        HarnessRuntime runtime = createAuthRuntime();
        givenLoginUser(runtime.jdbcTemplate(), USER_ID, USER_CODE, SystemRole.USER, UserStatus.DISABLED, false);

        assertThatThrownBy(() -> runtime.authService().login(USER_CODE, "UserPass123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号已被禁用");

        then(runtime.refreshTokenService()).shouldHaveNoInteractions();
        then(runtime.jwtAccessTokenService()).shouldHaveNoInteractions();
        then(runtime.jdbcTemplate()).should(never()).update(
                contains("update users set last_login_at"),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any()
        );
    }

    @Test
    void refreshRotatesRefreshTokenAndRevokesPreviousToken() throws Exception {
        HarnessRuntime runtime = createAuthRuntime();
        given(runtime.refreshTokenService().findActiveToken("old-refresh-token"))
                .willReturn(java.util.Optional.of(new RefreshTokenRecord(
                        5001L,
                        USER_ID,
                        "old-token-id",
                        "old-token-hash",
                        LocalDateTime.now().plusDays(1),
                        null
                )));
        givenUserById(runtime.jdbcTemplate(), USER_ID, USER_CODE, SystemRole.USER, UserStatus.ACTIVE, false);
        given(runtime.jwtAccessTokenService().issueToken(any(JwtAccessTokenService.TokenSubject.class)))
                .willReturn("next-access-token");
        given(runtime.refreshTokenService().issueToken(USER_ID))
                .willReturn(new RefreshTokenService.IssuedRefreshToken(
                        "next-refresh-token",
                        new RefreshTokenRecord(5002L, USER_ID, "next-token-id", "next-token-hash", LocalDateTime.now().plusDays(1), null)
                ));

        AuthService.AuthTokens tokens = runtime.authService().refresh("old-refresh-token");

        assertThat(tokens.accessToken()).isEqualTo("next-access-token");
        assertThat(tokens.refreshToken()).isEqualTo("next-refresh-token");
        then(runtime.refreshTokenService()).should().revokeToken("old-refresh-token");
        then(runtime.refreshTokenService()).should().issueToken(USER_ID);
    }

    @Test
    void businessUserRequirementAcceptsUserAndRejectsSystemAdmin() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CurrentUserService currentUserService = new CurrentUserService(jdbcTemplate);
        MockHttpServletRequest userRequest = request(USER_ID, USER_CODE, SystemRole.USER);
        MockHttpServletRequest adminRequest = request(9001L, "admin", SystemRole.ADMIN);
        givenCurrentUser(jdbcTemplate, USER_ID, USER_CODE, SystemRole.USER, UserStatus.ACTIVE);
        givenCurrentUser(jdbcTemplate, 9001L, "admin", SystemRole.ADMIN, UserStatus.ACTIVE);

        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser(userRequest);

        assertThat(currentUser.userId()).isEqualTo(USER_ID);
        assertThat(currentUser.systemRole()).isEqualTo(SystemRole.USER);
        assertThatThrownBy(() -> currentUserService.requireBusinessUser(adminRequest))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("系统管理员不能访问普通业务区");
    }

    private HarnessRuntime createAuthRuntime() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PasswordHasher passwordHasher = mock(PasswordHasher.class);
        JwtAccessTokenService jwtAccessTokenService = mock(JwtAccessTokenService.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        AuthService authService = new AuthService(
                jdbcTemplate,
                passwordHasher,
                jwtAccessTokenService,
                refreshTokenService,
                Clock.fixed(Instant.parse("2026-05-22T10:00:00Z"), ZoneOffset.UTC)
        );
        return new HarnessRuntime(authService, jdbcTemplate, passwordHasher, jwtAccessTokenService, refreshTokenService);
    }

    private void givenLoginUser(
            JdbcTemplate jdbcTemplate,
            Long userId,
            String userCode,
            SystemRole systemRole,
            UserStatus status,
            boolean mustChangePassword
    ) throws Exception {
        doAnswer(invocation -> List.of(mapUser(invocation.getArgument(1), userId, userCode, systemRole, status, mustChangePassword)))
                .when(jdbcTemplate)
                .query(contains("where username = ? or email = ?"), any(RowMapper.class), eq(userCode), eq(userCode));
    }

    private void givenUserById(
            JdbcTemplate jdbcTemplate,
            Long userId,
            String userCode,
            SystemRole systemRole,
            UserStatus status,
            boolean mustChangePassword
    ) throws Exception {
        doAnswer(invocation -> List.of(mapUser(invocation.getArgument(1), userId, userCode, systemRole, status, mustChangePassword)))
                .when(jdbcTemplate)
                .query(contains("where id = ?"), any(RowMapper.class), eq(userId));
    }

    private void givenCurrentUser(
            JdbcTemplate jdbcTemplate,
            Long userId,
            String userCode,
            SystemRole systemRole,
            UserStatus status
    ) throws Exception {
        doAnswer(invocation -> List.of(mapCurrentUser(invocation.getArgument(1), userId, userCode, systemRole, status)))
                .when(jdbcTemplate)
                .query(contains("from users"), any(RowMapper.class), eq(userId));
    }

    private Object mapUser(
            RowMapper<?> rowMapper,
            Long userId,
            String userCode,
            SystemRole systemRole,
            UserStatus status,
            boolean mustChangePassword
    ) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        given(resultSet.getLong("id")).willReturn(userId);
        given(resultSet.getString("user_code")).willReturn(userCode);
        given(resultSet.getString("username")).willReturn(userCode);
        given(resultSet.getString("email")).willReturn(userCode + "@local.ddrag.test");
        given(resultSet.getString("display_name")).willReturn("用户-" + userCode);
        given(resultSet.getString("password_hash")).willReturn("hash-1001");
        given(resultSet.getString("system_role")).willReturn(systemRole.name());
        given(resultSet.getString("status")).willReturn(status.name());
        given(resultSet.getBoolean("must_change_password")).willReturn(mustChangePassword);
        return rowMapper.mapRow(resultSet, 0);
    }

    private Object mapCurrentUser(
            RowMapper<?> rowMapper,
            Long userId,
            String userCode,
            SystemRole systemRole,
            UserStatus status
    ) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        given(resultSet.getLong("id")).willReturn(userId);
        given(resultSet.getString("user_code")).willReturn(userCode);
        given(resultSet.getString("display_name")).willReturn("用户-" + userCode);
        given(resultSet.getString("system_role")).willReturn(systemRole.name());
        given(resultSet.getString("status")).willReturn(status.name());
        given(resultSet.getBoolean("must_change_password")).willReturn(false);
        return rowMapper.mapRow(resultSet, 0);
    }

    private MockHttpServletRequest request(Long userId, String userCode, SystemRole systemRole) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                JwtAuthenticationFilter.AUTHENTICATED_USER_REQUEST_ATTRIBUTE,
                new JwtAuthenticationFilter.AuthenticatedUser(userId, userCode, "用户-" + userCode, systemRole, false)
        );
        return request;
    }

    private record HarnessRuntime(
            AuthService authService,
            JdbcTemplate jdbcTemplate,
            PasswordHasher passwordHasher,
            JwtAccessTokenService jwtAccessTokenService,
            RefreshTokenService refreshTokenService
    ) {
    }
}
