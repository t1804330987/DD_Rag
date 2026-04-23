package com.dong.ddrag.user;

import com.dong.ddrag.auth.service.AuthService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminUserControllerTest {

    private static final String ADMIN_DATABASE_URL = "jdbc:postgresql://localhost:5433/postgres";
    private static final String DATABASE_USERNAME = "root";
    private static final String DATABASE_PASSWORD = "123456";
    private static final String TEST_DATABASE = "dd_rag_admin_user_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String TEST_JWT_SECRET = "test-secret-for-admin-user-controller-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AuthService authService;

    @Autowired
    private com.dong.ddrag.auth.service.PasswordHasher passwordHasher;

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
    void resetUsers() {
        jdbcTemplate.update("delete from user_refresh_tokens");
        jdbcTemplate.update("delete from users where id >= 9000 or username in ('admin', 'u9502', 'u2001')");
    }

    @Test
    void shouldAllowAdminToCreateDisableAndResetUser() throws Exception {
        seedLoginUser(9501L, "admin", "AdminInit123!", SystemRole.ADMIN, UserStatus.ACTIVE);
        String adminToken = authService.login("admin", "AdminInit123!").accessToken();

        mockMvc.perform(post("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"u2001",
                                  "email":"u2001@local.ddrag.test",
                                  "displayName":"新用户",
                                  "systemRole":"USER",
                                  "initialPassword":"InitPass456",
                                  "mustChangePassword":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("u2001"))
                .andExpect(jsonPath("$.data.systemRole").value("USER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        long userId = findUserId("u2001");
        assertThat(loadMustChangePassword(userId)).isTrue();

        mockMvc.perform(post("/api/admin/users/{id}/reset-password", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"ResetPass789"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThatThrownBy(() -> authService.login("u2001", "InitPass456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号或密码错误");
        assertThat(authService.login("u2001", "ResetPass789").mustChangePassword()).isTrue();

        mockMvc.perform(patch("/api/admin/users/{id}/status", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThatThrownBy(() -> authService.login("u2001", "ResetPass789"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号已被禁用");
    }

    @Test
    void shouldRejectNonAdminWhenAccessingAdminUsersApi() throws Exception {
        seedLoginUser(9502L, "u9502", "InitPass123!", SystemRole.USER, UserStatus.ACTIVE);
        String userToken = authService.login("u9502", "InitPass123!").accessToken();

        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("当前用户不是系统管理员"));
    }

    @AfterAll
    void cleanupDatabase() {
        closeDataSource();
        dropDatabase(TEST_DATABASE);
    }

    private void seedLoginUser(
            long userId,
            String loginId,
            String rawPassword,
            SystemRole systemRole,
            UserStatus status
    ) {
        jdbcTemplate.update(
                """
                insert into users (
                    id, user_code, username, email, display_name, password_hash,
                    system_role, status, must_change_password, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, false, now(), now())
                """,
                userId,
                loginId,
                loginId,
                loginId + "@local.ddrag.test",
                "用户-" + loginId,
                passwordHasher.hash(rawPassword),
                systemRole.name(),
                status.name()
        );
    }

    private long findUserId(String username) {
        Long userId = jdbcTemplate.queryForObject(
                "select id from users where username = ?",
                Long.class,
                username
        );
        return userId == null ? -1L : userId;
    }

    private boolean loadMustChangePassword(long userId) {
        Boolean mustChangePassword = jdbcTemplate.queryForObject(
                "select must_change_password from users where id = ?",
                Boolean.class,
                userId
        );
        return Boolean.TRUE.equals(mustChangePassword);
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
            throw new IllegalStateException("Failed to manage temporary admin user database", exception);
        }
    }

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
