package com.dong.ddrag.auth;

import com.dong.ddrag.auth.security.JwtAccessTokenService;
import com.dong.ddrag.auth.service.PasswordHasher;
import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.common.enums.UserStatus;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JwtAuthenticationFilterTest {

    private static final String ADMIN_DATABASE_URL = "jdbc:postgresql://localhost:5433/postgres";
    private static final String DATABASE_USERNAME = "root";
    private static final String DATABASE_PASSWORD = "123456";
    private static final String TEST_DATABASE = "dd_rag_jwt_filter_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String TEST_JWT_SECRET = "test-secret-for-jwt-filter-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

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
        registry.add("spring.ai.dashscope.api-key", () -> "test-dashscope-key");
    }

    @BeforeEach
    void resetAuthState() {
        jdbcTemplate.update("delete from user_refresh_tokens");
        jdbcTemplate.update("delete from users where id >= 9000");
    }

    @Test
    void shouldPreferAuthenticatedPrincipalOverTestHeader() throws Exception {
        seedLoginUser(9301L, "u9301", "InitPass123!", SystemRole.USER, UserStatus.ACTIVE);
        String accessToken = jwtAccessTokenService.issueToken(
                new JwtAccessTokenService.TokenSubject(9301L, "u9301", "用户-u9301", SystemRole.USER, true)
        );

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header("X-Test-User-Id", "u1002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(9301L))
                .andExpect(jsonPath("$.data.userCode").value("u9301"))
                .andExpect(jsonPath("$.data.displayName").value("用户-u9301"))
                .andExpect(jsonPath("$.data.systemRole").value("USER"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
    }

    @Test
    void shouldReturnUnauthorizedWhenBearerTokenIsInvalid() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .header("X-Test-User-Id", "u1001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("access token 非法或已过期"));
    }

    @Test
    void shouldRejectLegacyTestHeaderWithoutBearerAfterCutover() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("X-Test-User-Id", "u1001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("当前请求未登录"));
    }

    @Test
    void shouldIgnoreInvalidBearerTokenOnLoginEndpoint() throws Exception {
        seedLoginUser(9302L, "u9302", "InitPass123!", SystemRole.USER, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"u9302","password":"InitPass123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentUser.userCode").value("u9302"));
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
                ) values (?, ?, ?, ?, ?, ?, ?, ?, true, now(), now())
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
            throw new IllegalStateException("Failed to manage temporary jwt filter database", exception);
        }
    }

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
