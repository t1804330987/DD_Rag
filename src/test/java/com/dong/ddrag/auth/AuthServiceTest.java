package com.dong.ddrag.auth;

import com.dong.ddrag.auth.security.JwtAccessTokenService;
import com.dong.ddrag.auth.security.RefreshTokenService;
import com.dong.ddrag.auth.model.dto.RegisterRequest;
import com.dong.ddrag.auth.service.AuthService;
import com.dong.ddrag.auth.service.AuthService.AuthTokens;
import com.dong.ddrag.auth.service.PasswordHasher;
import com.dong.ddrag.common.enums.SystemRole;
import com.dong.ddrag.common.enums.UserStatus;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.ingestion.service.DocumentIngestionProcessor;
import com.zaxxer.hikari.HikariDataSource;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthServiceTest {

    private static final String ADMIN_DATABASE_URL = "jdbc:postgresql://localhost:5433/postgres";
    private static final String DATABASE_USERNAME = "root";
    private static final String DATABASE_PASSWORD = "123456";
    private static final String TEST_DATABASE = "dd_rag_auth_" + UUID.randomUUID().toString().replace("-", "");
    private static final String TEST_JWT_SECRET = "test-secret-for-auth-service-1234567890";

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

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
        registry.add("spring.ai.dashscope.api-key", () -> "test-dashscope-key");
    }

    @BeforeEach
    void resetAuthState() {
        jdbcTemplate.update("delete from user_refresh_tokens");
        jdbcTemplate.update("delete from users where id >= 9000");
    }

    @Test
    void shouldIssueAccessAndRefreshTokensOnLogin() {
        LoginSeed seed = new LoginSeed(9101L, "u9101", "InitPass123!", SystemRole.USER, UserStatus.ACTIVE);
        seedLoginUser(seed);

        AuthTokens tokens = authService.login(seed.loginId(), seed.rawPassword());
        JwtAccessTokenService.AccessTokenClaims claims = jwtAccessTokenService.parse(tokens.accessToken());

        assertThat(tokens.userId()).isEqualTo(seed.userId());
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.mustChangePassword()).isTrue();
        assertThat(claims.userId()).isEqualTo(seed.userId());
        assertThat(claims.userCode()).isEqualTo(seed.loginId());
        assertThat(claims.systemRole()).isEqualTo(seed.systemRole());
        assertThat(refreshTokenService.findActiveToken(tokens.refreshToken()))
                .map(record -> record.userId().equals(seed.userId()))
                .hasValue(true);
        assertThat(refreshTokenService.countActiveTokens(seed.userId())).isEqualTo(1);
        assertThat(queryLastLoginAt(seed.userId())).isPresent();
    }

    @Test
    void shouldRevokePreviousRefreshTokenWhenSameUserLogsInAgain() {
        LoginSeed seed = new LoginSeed(9102L, "u9102", "InitPass123!", SystemRole.USER, UserStatus.ACTIVE);
        seedLoginUser(seed);

        AuthTokens firstTokens = authService.login(seed.loginId(), seed.rawPassword());
        AuthTokens secondTokens = authService.login(seed.loginId(), seed.rawPassword());

        assertThat(secondTokens.refreshToken()).isNotEqualTo(firstTokens.refreshToken());
        assertThat(refreshTokenService.findActiveToken(firstTokens.refreshToken())).isEmpty();
        assertThat(refreshTokenService.findActiveToken(secondTokens.refreshToken()))
                .map(record -> record.userId().equals(seed.userId()))
                .hasValue(true);
        assertThat(refreshTokenService.countActiveTokens(seed.userId())).isEqualTo(1);
    }

    @Test
    void shouldRejectDisabledUserLogin() {
        LoginSeed seed = new LoginSeed(9103L, "u9103", "InitPass123!", SystemRole.USER, UserStatus.ACTIVE);
        seedLoginUser(seed);
        disableUser(seed.loginId());

        assertThatThrownBy(() -> authService.login(seed.loginId(), seed.rawPassword()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号已被禁用");
    }

    @Test
    void shouldAllowLoginByEmail() {
        LoginSeed seed = new LoginSeed(9106L, "u9106", "InitPass123!", SystemRole.USER, UserStatus.ACTIVE);
        seedLoginUser(seed);

        AuthTokens tokens = authService.login(seed.loginId() + "@local.ddrag.test", seed.rawPassword());

        assertThat(tokens.userId()).isEqualTo(seed.userId());
        assertThat(tokens.accessToken()).isNotBlank();
    }

    @Test
    void shouldRejectAmbiguousLoginIdentifier() {
        seedCustomUser(9104L, "u9104", "user-a@local.ddrag.test", "InitPass123!", SystemRole.USER, UserStatus.ACTIVE);
        seedCustomUser(9105L, "user-b", "u9104", "InitPass456!", SystemRole.USER, UserStatus.ACTIVE);

        assertThatThrownBy(() -> authService.login("u9104", "InitPass123!"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录标识存在冲突");
    }

    @Test
    void shouldRejectPasswordLongerThanBcryptSafeLimit() {
        String longPassword = "密".repeat(25);

        assertThatThrownBy(() -> passwordHasher.hash(longPassword))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("72 字节以内");
    }

    @Test
    void shouldRejectSignedTokenWithMissingRequiredClaims() {
        String malformedToken = Jwts.builder()
                .issuer("dd-rag")
                .subject("u9107")
                .signWith(Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> jwtAccessTokenService.parse(malformedToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("access token 非法或已过期");
    }

    @Test
    void shouldRegisterUserWithoutIssuingTokens() {
        RegisterRequest request = new RegisterRequest(
                "  register-user  ",
                "  register-user@local.ddrag.test  ",
                "  注册用户  ",
                "UserPass123"
        );

        authService.register(request);

        RegisteredUser user = loadRegisteredUser("register-user");
        assertThat(user).isNotNull();
        assertThat(user.userCode()).isEqualTo("register-user");
        assertThat(user.username()).isEqualTo("register-user");
        assertThat(user.email()).isEqualTo("register-user@local.ddrag.test");
        assertThat(user.displayName()).isEqualTo("注册用户");
        assertThat(user.systemRole()).isEqualTo(SystemRole.USER);
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.mustChangePassword()).isFalse();
        assertThat(passwordHasher.matches("UserPass123", user.passwordHash())).isTrue();
        assertThat(refreshTokenService.countActiveTokens(user.userId())).isZero();
    }

    @Test
    void shouldRejectRegisterWhenUsernameExists() {
        seedCustomUser(9108L, "existing-user", "existing-user@local.ddrag.test", "InitPass123!", SystemRole.USER, UserStatus.ACTIVE);

        RegisterRequest request = new RegisterRequest(
                " existing-user ",
                "new-email@local.ddrag.test",
                "重复用户",
                "UserPass123"
        );

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在");
    }

    @Test
    void shouldRejectRegisterWhenUsernameIsReservedWord() {
        RegisterRequest request = new RegisterRequest(
                " NULL ",
                "reserved-null@local.ddrag.test",
                "保留名用户",
                "UserPass123"
        );

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名不合法");
    }

    @Test
    void shouldRejectRegisterWhenUsernameContainsIllegalCharacters() {
        RegisterRequest request = new RegisterRequest(
                "bad user",
                "bad-user@local.ddrag.test",
                "非法字符用户",
                "UserPass123"
        );

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名不合法");
    }

    @Test
    void shouldResetPasswordByUsernameAndEmail() {
        seedCustomUser(9110L, "recover-user", "recover-user@local.ddrag.test", "InitPass123!", SystemRole.USER, UserStatus.ACTIVE);
        AuthTokens tokens = authService.login("recover-user", "InitPass123!");

        authService.resetPasswordByIdentity(" recover-user ", " recover-user@local.ddrag.test ", "BetterPass123");

        assertThatThrownBy(() -> authService.login("recover-user", "InitPass123!"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号或密码错误");
        assertThat(authService.login("recover-user", "BetterPass123").accessToken()).isNotBlank();
        assertThat(refreshTokenService.findActiveToken(tokens.refreshToken())).isEmpty();
    }

    @Test
    void shouldRejectResetPasswordWhenUsernameAndEmailDoNotMatch() {
        seedCustomUser(9111L, "recover-user-2", "recover-user-2@local.ddrag.test", "InitPass123!", SystemRole.USER, UserStatus.ACTIVE);

        assertThatThrownBy(() -> authService.resetPasswordByIdentity(
                "recover-user-2",
                "wrong@local.ddrag.test",
                "BetterPass123"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号信息不匹配");
    }

    @Test
    void shouldRejectRegisterWhenEmailExists() {
        seedCustomUser(9109L, "existing-email-user", "existing-email@local.ddrag.test", "InitPass123!", SystemRole.USER, UserStatus.ACTIVE);

        RegisterRequest request = new RegisterRequest(
                "new-user",
                " existing-email@local.ddrag.test ",
                "重复邮箱",
                "UserPass123"
        );

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("邮箱已存在");
    }

    @Test
    void shouldRejectRegisterWhenPasswordTooShort() {
        RegisterRequest request = new RegisterRequest(
                "short-password-user",
                "short-password-user@local.ddrag.test",
                "弱密码用户",
                "Abc1234"
        );

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少 8 位");
    }

    @Test
    void shouldRejectRegisterWhenPasswordHasNoLetter() {
        RegisterRequest request = new RegisterRequest(
                "digit-only-user",
                "digit-only-user@local.ddrag.test",
                "弱密码用户",
                "12345678"
        );

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少 8 位");
    }

    @Test
    void shouldRejectRegisterWhenPasswordHasNoDigit() {
        RegisterRequest request = new RegisterRequest(
                "letter-only-user",
                "letter-only-user@local.ddrag.test",
                "弱密码用户",
                "Password"
        );

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少 8 位");
    }

    @Test
    void shouldRejectRegisterWhenPasswordExceedsBcryptSafeLimit() {
        RegisterRequest request = new RegisterRequest(
                "long-password-user",
                "long-password-user@local.ddrag.test",
                "弱密码用户",
                "密".repeat(25)
        );

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("72 字节以内");
    }

    @AfterAll
    void cleanupDatabase() {
        closeDataSource();
        dropDatabase(TEST_DATABASE);
    }

    private void seedLoginUser(LoginSeed seed) {
        seedCustomUser(
                seed.userId(),
                seed.loginId(),
                seed.loginId() + "@local.ddrag.test",
                seed.rawPassword(),
                seed.systemRole(),
                seed.status()
        );
    }

    private void seedCustomUser(
            long userId,
            String username,
            String email,
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
                username,
                username,
                email,
                "用户-" + username,
                passwordHasher.hash(rawPassword),
                systemRole.name(),
                status.name()
        );
    }

    private void disableUser(String loginId) {
        jdbcTemplate.update(
                "update users set status = ?, updated_at = now() where username = ?",
                UserStatus.DISABLED.name(),
                loginId
        );
    }

    private Optional<LocalDateTime> queryLastLoginAt(Long userId) {
        Timestamp timestamp = jdbcTemplate.queryForObject(
                "select last_login_at from users where id = ?",
                Timestamp.class,
                userId
        );
        return Optional.ofNullable(timestamp).map(Timestamp::toLocalDateTime);
    }

    private RegisteredUser loadRegisteredUser(String username) {
        return jdbcTemplate.queryForObject(
                """
                select id, user_code, username, email, display_name, password_hash,
                       system_role, status, must_change_password
                from users
                where username = ?
                """,
                (resultSet, rowNum) -> new RegisteredUser(
                        resultSet.getLong("id"),
                        resultSet.getString("user_code"),
                        resultSet.getString("username"),
                        resultSet.getString("email"),
                        resultSet.getString("display_name"),
                        resultSet.getString("password_hash"),
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
            throw new IllegalStateException("Failed to manage temporary auth database", exception);
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
            String username,
            String email,
            String displayName,
            String passwordHash,
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
