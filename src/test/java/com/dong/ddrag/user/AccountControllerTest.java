package com.dong.ddrag.user;

import com.dong.ddrag.auth.service.AuthService;
import com.dong.ddrag.auth.service.AuthService.AuthTokens;
import com.dong.ddrag.auth.service.PasswordHasher;
import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.common.enums.UserStatus;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.ingestion.service.DocumentIngestionProcessor;
import com.zaxxer.hikari.HikariDataSource;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountControllerTest {

    private static final String ADMIN_DATABASE_URL = "jdbc:postgresql://localhost:5433/postgres";
    private static final String DATABASE_USERNAME = "root";
    private static final String DATABASE_PASSWORD = "123456";
    private static final String TEST_DATABASE = "dd_rag_account_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String TEST_JWT_SECRET = "test-secret-for-account-controller-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private AuthService authService;

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
        registry.add("spring.ai.dashscope.api-key", () -> "test-dashscope-key");
    }

    @BeforeEach
    void resetAuthState() {
        jdbcTemplate.update("delete from user_refresh_tokens");
        jdbcTemplate.update("delete from users where id >= 9000");
    }

    @Test
    void shouldForcePasswordChangeFlagToFalseAfterSuccessfulPasswordUpdate() throws Exception {
        LoginSeed seed = new LoginSeed(9401L, "u9401", "InitPass123!", SystemRole.USER, UserStatus.ACTIVE);
        seedLoginUser(seed);
        AuthTokens tokens = authService.login(seed.loginId(), seed.rawPassword());

        mockMvc.perform(post("/api/account/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"InitPass123!","newPassword":"BetterPass123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(loadMustChangePassword(seed.userId())).isFalse();
        assertThat(countActiveRefreshTokens(seed.userId())).isZero();
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));
        assertThatThrownBy(() -> authService.login(seed.loginId(), seed.rawPassword()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号或密码错误");
        assertThat(authService.login(seed.loginId(), "BetterPass123").accessToken()).isNotBlank();
    }

    @Test
    void shouldRejectWeakPassword() throws Exception {
        LoginSeed seed = new LoginSeed(9402L, "u9402", "InitPass123!", SystemRole.USER, UserStatus.ACTIVE);
        seedLoginUser(seed);
        AuthTokens tokens = authService.login(seed.loginId(), seed.rawPassword());

        mockMvc.perform(post("/api/account/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"InitPass123!","newPassword":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("新密码必须至少 8 位，且同时包含字母和数字"));
    }

    @Test
    void shouldRejectIncorrectCurrentPassword() throws Exception {
        LoginSeed seed = new LoginSeed(9403L, "u9403", "InitPass123!", SystemRole.USER, UserStatus.ACTIVE);
        seedLoginUser(seed);
        AuthTokens tokens = authService.login(seed.loginId(), seed.rawPassword());

        mockMvc.perform(post("/api/account/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"WrongPass123","newPassword":"BetterPass123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("当前密码不正确"));
    }

    @Test
    void shouldRejectWhenNewPasswordMatchesCurrentPassword() throws Exception {
        LoginSeed seed = new LoginSeed(9404L, "u9404", "InitPass123!", SystemRole.USER, UserStatus.ACTIVE);
        seedLoginUser(seed);
        AuthTokens tokens = authService.login(seed.loginId(), seed.rawPassword());

        mockMvc.perform(post("/api/account/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"InitPass123!","newPassword":"InitPass123!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("新密码不能与当前密码相同"));
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

    private boolean loadMustChangePassword(long userId) {
        Boolean mustChangePassword = jdbcTemplate.queryForObject(
                "select must_change_password from users where id = ?",
                Boolean.class,
                userId
        );
        return Boolean.TRUE.equals(mustChangePassword);
    }

    private long countActiveRefreshTokens(long userId) {
        Long count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from user_refresh_tokens
                where user_id = ?
                  and revoked_at is null
                """,
                Long.class,
                userId
        );
        return count == null ? 0 : count;
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
            throw new IllegalStateException("Failed to manage temporary account database", exception);
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

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
