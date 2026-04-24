package com.dong.ddrag.auth;

import com.dong.ddrag.auth.security.RefreshTokenService;
import com.dong.ddrag.auth.service.AuthService;
import com.dong.ddrag.auth.service.AuthService.AuthTokens;
import com.dong.ddrag.auth.service.PasswordHasher;
import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.common.enums.UserStatus;
import com.dong.ddrag.ingestion.service.DocumentIngestionProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthControllerTest {

    private static final String ADMIN_DATABASE_URL = "jdbc:postgresql://localhost:5433/postgres";
    private static final String DATABASE_USERNAME = "root";
    private static final String DATABASE_PASSWORD = "123456";
    private static final String TEST_DATABASE = "dd_rag_auth_controller_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String TEST_JWT_SECRET = "test-secret-for-auth-controller-1234567890";
    private static final String REFRESH_COOKIE_NAME = "DD_RAG_REFRESH_TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @MockBean
    private DocumentIngestionProcessor documentIngestionProcessor;

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        createDatabase(TEST_DATABASE);
        String jdbcUrl = "jdbc:postgresql://localhost:5433/" + TEST_DATABASE;
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> DATABASE_USERNAME);
        registry.add("spring.datasource.password", () -> DATABASE_PASSWORD);
        registry.add("spring.flyway.url", () -> jdbcUrl);
        registry.add("spring.flyway.user", () -> DATABASE_USERNAME);
        registry.add("spring.flyway.password", () -> DATABASE_PASSWORD);
        registry.add("ddrag.auth.jwt-secret", () -> TEST_JWT_SECRET);
        registry.add("ddrag.auth.refresh-cookie-secure", () -> false);
        registry.add("spring.ai.dashscope.api-key", () -> "test-dashscope-key");
    }

    @BeforeEach
    void resetAuthState() {
        jdbcTemplate.update("delete from user_refresh_tokens");
        jdbcTemplate.update("delete from users where id >= 9000");
    }

    @Test
    void shouldLoginAndReturnCurrentUserProfile() throws Exception {
        LoginSeed seed = new LoginSeed(9201L, "u9201", "InitPass123!", SystemRole.USER, UserStatus.ACTIVE);
        seedLoginUser(seed);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"u9201","password":"InitPass123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString(REFRESH_COOKIE_NAME + "=")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.currentUser.userId").value(seed.userId()))
                .andExpect(jsonPath("$.data.currentUser.userCode").value(seed.loginId()))
                .andExpect(jsonPath("$.data.currentUser.displayName").value("用户-" + seed.loginId()))
                .andExpect(jsonPath("$.data.currentUser.systemRole").value(seed.systemRole().name()))
                .andExpect(jsonPath("$.data.currentUser.mustChangePassword").value(true));

        assertThat(refreshTokenService.countActiveTokens(seed.userId())).isEqualTo(1);
    }

    @Test
    void shouldRefreshTokenAndReturnCurrentUserProfile() throws Exception {
        LoginSeed seed = new LoginSeed(9202L, "u9202", "InitPass123!", SystemRole.USER, UserStatus.ACTIVE);
        seedLoginUser(seed);
        AuthTokens loginTokens = authService.login(seed.loginId(), seed.rawPassword());

        MvcResult mvcResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, loginTokens.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString(REFRESH_COOKIE_NAME + "=")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.currentUser.userId").value(seed.userId()))
                .andExpect(jsonPath("$.data.currentUser.userCode").value(seed.loginId()))
                .andExpect(jsonPath("$.data.currentUser.systemRole").value(seed.systemRole().name()))
                .andExpect(jsonPath("$.data.currentUser.mustChangePassword").value(true))
                .andReturn();

        String rotatedRefreshToken = extractRefreshToken(mvcResult);
        assertThat(rotatedRefreshToken).isNotBlank();
        assertThat(rotatedRefreshToken).isNotEqualTo(loginTokens.refreshToken());
        assertThat(refreshTokenService.findActiveToken(loginTokens.refreshToken())).isEmpty();
        assertThat(refreshTokenService.findActiveToken(rotatedRefreshToken))
                .map(record -> record.userId().equals(seed.userId()))
                .hasValue(true);
        assertThat(refreshTokenService.countActiveTokens(seed.userId())).isEqualTo(1);
    }

    @Test
    void shouldLogoutAndRevokeRefreshToken() throws Exception {
        LoginSeed seed = new LoginSeed(9203L, "u9203", "InitPass123!", SystemRole.USER, UserStatus.ACTIVE);
        seedLoginUser(seed);
        AuthTokens loginTokens = authService.login(seed.loginId(), seed.rawPassword());

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, loginTokens.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString(REFRESH_COOKIE_NAME + "=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")))
                .andExpect(jsonPath("$.success").value(true));

        assertThat(refreshTokenService.findActiveToken(loginTokens.refreshToken())).isEmpty();
        assertThat(refreshTokenService.countActiveTokens(seed.userId())).isZero();
    }

    @Test
    void shouldRegisterWithoutAuthorizationAndWithoutRefreshCookie() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"register-user",
                                  "email":"register-user@local.ddrag.test",
                                  "displayName":"注册用户",
                                  "password":"UserPass123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));

        RegisteredUser user = loadRegisteredUser("register-user");
        assertThat(user).isNotNull();
        assertThat(user.userCode()).isEqualTo("register-user");
        assertThat(user.systemRole()).isEqualTo(SystemRole.USER);
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.mustChangePassword()).isFalse();
        assertThat(refreshTokenService.countActiveTokens(user.userId())).isZero();
    }

    @Test
    void shouldRejectRegisterWhenPasswordIsWeak() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"weak-password-user",
                                  "email":"weak-password-user@local.ddrag.test",
                                  "displayName":"弱密码用户",
                                  "password":"12345678"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("新密码必须至少 8 位，且同时包含字母和数字"));
    }

    @Test
    void shouldRejectRegisterWhenUsernameIsReservedWord() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"admin",
                                  "email":"admin-duplicate@local.ddrag.test",
                                  "displayName":"保留名用户",
                                  "password":"UserPass123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("用户名不合法"));
    }

    @Test
    void shouldResetPasswordByUsernameAndEmailWithoutAuthorization() throws Exception {
        LoginSeed seed = new LoginSeed(9204L, "u9204", "InitPass123!", SystemRole.USER, UserStatus.ACTIVE);
        seedLoginUser(seed);
        AuthTokens loginTokens = authService.login(seed.loginId(), seed.rawPassword());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"u9204",
                                  "email":"u9204@local.ddrag.test",
                                  "newPassword":"BetterPass123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));

        assertThatThrownBy(() -> authService.login("u9204", "InitPass123!"))
                .isInstanceOf(com.dong.ddrag.common.exception.BusinessException.class)
                .hasMessageContaining("账号或密码错误");
        assertThat(authService.login("u9204", "BetterPass123").accessToken()).isNotBlank();
        assertThat(refreshTokenService.findActiveToken(loginTokens.refreshToken())).isEmpty();
    }

    @AfterAll
    void cleanupDatabase() {
        closeDataSource();
        dropDatabase(TEST_DATABASE);
    }

    private void seedLoginUser(LoginSeed seed) {
        jdbcTemplate.update(
                """
                insert into users (
                    id, user_code, username, email, display_name, password_hash,
                    system_role, status, must_change_password, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, true, now(), now())
                """,
                seed.userId(),
                seed.loginId(),
                seed.loginId(),
                seed.loginId() + "@local.ddrag.test",
                "用户-" + seed.loginId(),
                passwordHasher.hash(seed.rawPassword()),
                seed.systemRole().name(),
                seed.status().name()
        );
    }

    private String extractRefreshToken(MvcResult mvcResult) throws Exception {
        JsonNode responseBody = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertThat(responseBody.path("data").path("accessToken").asText()).isNotBlank();

        String setCookieValue = mvcResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookieValue).isNotBlank();
        String cookiePrefix = REFRESH_COOKIE_NAME + "=";
        int startIndex = setCookieValue.indexOf(cookiePrefix);
        int endIndex = setCookieValue.indexOf(';', startIndex);
        return setCookieValue.substring(startIndex + cookiePrefix.length(), endIndex);
    }

    private RegisteredUser loadRegisteredUser(String username) {
        return jdbcTemplate.queryForObject(
                """
                select id, user_code, username, system_role, status, must_change_password
                from users
                where username = ?
                """,
                (resultSet, rowNum) -> new RegisteredUser(
                        resultSet.getLong("id"),
                        resultSet.getString("user_code"),
                        SystemRole.valueOf(resultSet.getString("system_role")),
                        UserStatus.valueOf(resultSet.getString("status")),
                        resultSet.getBoolean("must_change_password")
                ),
                username
        );
    }

    private void closeDataSource() {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.close();
        }
    }

    private static void createDatabase(String databaseName) {
        executeOnAdminDatabase(connection -> connection.createStatement().execute("create database " + databaseName));
    }

    private static void dropDatabase(String databaseName) {
        executeOnAdminDatabase(connection -> {
            connection.createStatement().execute(
                    """
                    select pg_terminate_backend(pid)
                    from pg_stat_activity
                    where datname = '%s' and pid <> pg_backend_pid()
                    """.formatted(databaseName)
            );
            connection.createStatement().execute("drop database if exists " + databaseName);
        });
    }

    private static void executeOnAdminDatabase(SqlConsumer sqlConsumer) {
        try (Connection connection = DriverManager.getConnection(
                ADMIN_DATABASE_URL,
                DATABASE_USERNAME,
                DATABASE_PASSWORD
        )) {
            sqlConsumer.accept(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to manage temporary auth controller database", exception);
        }
    }

    private record LoginSeed(
            long userId,
            String loginId,
            String rawPassword,
            SystemRole systemRole,
            UserStatus status
    ) {
    }

    private record RegisteredUser(
            long userId,
            String userCode,
            SystemRole systemRole,
            UserStatus status,
            boolean mustChangePassword
    ) {
    }

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
